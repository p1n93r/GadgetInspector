package fun.pinger.discovery;

import fun.pinger.config.Command;
import fun.pinger.core.ImplementationFinder;
import fun.pinger.core.ScanTypeConfig;
import fun.pinger.utils.InheritanceUtil;
import fun.pinger.utils.SinkUtil;
import fun.pinger.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;



public class GadgetChainDiscovery {

  private static final Logger LOGGER = LoggerFactory.getLogger(GadgetChainDiscovery.class);

  private final ScanTypeConfig config;

  public GadgetChainDiscovery(ScanTypeConfig config) {
    this.config = config;
  }

  /**
   * 自定义的slink点
   */
  private static List<CustomSink> customSinks = new ArrayList<>();

  static {
    // 先从自定义的sink文件中加载slink
    if (!Command.slinksFile.isEmpty()) {
      Path path = Paths.get(Command.slinksFile);
      customSinks.addAll(SinkUtil.loadSink(path));
    }
    // 然后加载自带的sink
    InputStream inputStream = GadgetChainDiscovery.class.getResourceAsStream("/common-sinks.txt");
    customSinks.addAll(SinkUtil.loadSink(inputStream));
    LOGGER.info(String.format("[*] current custom sinks count are : %d", customSinks.size()));
  }

  public void discover() throws Exception {

    // 先加载一下前面生成的数据
    Map<MethodReference.Handle, MethodReference> methodMap = DataLoader.loadMethods();
    InheritanceMap inheritanceMap = InheritanceMap.load();
    Map<MethodReference.Handle, Set<MethodReference.Handle>> methodImplMap = InheritanceUtil.getAllMethodImplementations(inheritanceMap, methodMap);
    Map<ClassReference.Handle, Set<MethodReference.Handle>> methodsByClass = InheritanceUtil.getMethodsByClass(methodMap);

    final ImplementationFinder implementationFinder = config.getImplementationFinder(methodMap, methodImplMap, inheritanceMap, methodsByClass);

    // 将 父方法-子方法 写入到文件
    try (Writer writer = Files.newBufferedWriter(Paths.get("methodimpl.dat"))) {
      for (Map.Entry<MethodReference.Handle, Set<MethodReference.Handle>> entry : methodImplMap.entrySet()) {
        // 父类方法
        writer.write(entry.getKey().getClassReference().getName());
        writer.write("\t");
        writer.write(entry.getKey().getName());
        writer.write("\t");
        writer.write(entry.getKey().getDesc());
        writer.write("\n");

        // 子类方法实现
        for (MethodReference.Handle method : entry.getValue()) {
          writer.write("\t");
          writer.write(method.getClassReference().getName());
          writer.write("\t");
          writer.write(method.getName());
          writer.write("\t");
          writer.write(method.getDesc());
          writer.write("\n");
        }
      }
    }

    // 加载 callGraph，key：caller，value：callGraph 对象
    Map<MethodReference.Handle, Set<CallGraph>> graphCallMap = new HashMap<>();
    for (CallGraph graphCall : DataLoader.loadData(Paths.get("callgraph.dat"), new CallGraph.Factory())) {
      MethodReference.Handle caller = graphCall.getCallerMethod();
      if (!graphCallMap.containsKey(caller)) {
        Set<CallGraph> graphCalls = new HashSet<>();
        graphCalls.add(graphCall);
        graphCallMap.put(caller, graphCalls);
      } else {
        graphCallMap.get(caller).add(graphCall);
      }
    }

    // 探索过的方法
    Set<GadgetChainNode> exploredMethods = new HashSet<>();
    // 待探索的方法
    LinkedList<GadgetChain> methodsToExplore = new LinkedList<>();


    // 加载source，作为GadgetChain的第一个link，然后存放在exploredMethods，后续再探索
    for (Source source : DataLoader.loadData(Paths.get("sources.dat"), new Source.Factory())) {
      GadgetChainNode srcLink = new GadgetChainNode(source.getSourceMethod(), source.getTaintedArgIndex());
      if (exploredMethods.contains(srcLink)) {
        continue;
      }
      methodsToExplore.add(new GadgetChain(Arrays.asList(srcLink)));
      exploredMethods.add(srcLink);
    }

    // 发现的有效链
    Set<GadgetChain> discoveredGadgets = new HashSet<>();

    // 这个地方一定要注意，对于每一个source，exploredMethods都需要是初始值，否则前一个source的exploredMethods会影响下一个，造成严重漏报
    methodsToExplore.forEach(sourceChain->{
      // 探索过的方法
      Set<GadgetChainNode> explored = new HashSet<>();
      explored.addAll(exploredMethods);
      // 待探索的方法
      LinkedList<GadgetChain> processList = new LinkedList<>();
      processList.add(sourceChain);


      GadgetChainNode sourceLink = sourceChain.getLinks().get(0);
      LOGGER.info(String.format("[*] current source class: %s, method: %s, argIndex: %d",
              sourceLink.getMethod().getClassReference().getName(),
              sourceLink.getMethod().getName(),
              sourceLink.getTaintedArgIndex()
      ));
      while (processList.size() > 0) {
        GadgetChain chain = processList.pop();
        GadgetChainNode lastLink = chain.getLinks().get(chain.getLinks().size() - 1);

        // 如果设置了调用链路径长度限制，则会进行长度判断；默认不限制，可能会存在很长的调用链，造成Dos
        if (Command.maxChainLength != -1 && chain.getLinks().size() >= Command.maxChainLength) {
          continue;
        }

        // 链尾的方法会调用的方法
        Set<CallGraph> methodCalls = graphCallMap.get(lastLink.getMethod());
        if (methodCalls != null) {
          for (CallGraph graphCall : methodCalls) {
            // 使用污点分析才会进行数据流判断，如果污点传播参数不对，则此链断开
            if (Command.enableTaintTrack && graphCall.getCallerArgIndex() != lastLink.getTaintedArgIndex()) {
              continue;
            }

            // 获取待调用的方法的所有的方法实现
            Set<MethodReference.Handle> allImpls = implementationFinder.getImplementations(graphCall.getTargetMethod());

            // 找不到就从父类找
            if (allImpls.isEmpty()) {
              Set<ClassReference.Handle> parents = inheritanceMap.getSuperClasses(graphCall.getTargetMethod().getClassReference());
              if (parents == null) {
                continue;
              }
              for (ClassReference.Handle parent : parents) {
                Set<MethodReference.Handle> methods = methodsByClass.get(parent);
                // 为了解决这个bug，只能反向父类去查找方法，但是目前解决的方式可能会存在记录多个父类方法，但是已初步解决这个问题
                if (methods == null) {
                  continue;
                }
                for (MethodReference.Handle method : methods) {
                  if (method.getName().equals(graphCall.getTargetMethod().getName()) && method.getDesc().equals(graphCall.getTargetMethod().getDesc())) {
                    allImpls.add(method);
                  }
                }
              }
            }

            for (MethodReference.Handle methodImpl : allImpls) {
              GadgetChainNode newLink = new GadgetChainNode(methodImpl,graphCall.getTargetArgIndex());
              // 如果发现存在重复的link节点了，则跳过，就是因为这个地方，所以才需要单独处理每一个source的explored
              if (explored.contains(newLink)) {
                continue;
              }
              GadgetChain newChain = new GadgetChain(chain, newLink);
              if (isSink(methodImpl, graphCall.getTargetArgIndex(), inheritanceMap)) {
                // 如果当前link节点是sink，则添加到discoveredGadgets
                discoveredGadgets.add(newChain);
              } else {
                // 前面的条件能通过，则调用链继续往下走，将刚才分析过的link节点加入当前分析的GadgetChain形成新的GadgetChain进行下一轮分析
                processList.add(newChain);
                explored.add(newLink);
              }
            }
          }
        }
      }
    });


    // 如果发现了调用链
    if (!discoveredGadgets.isEmpty()) {
      SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm");
      String resultFilePath = Command.outputFile.isEmpty()? ("scan-result-" + simpleDateFormat.format(new Date()) + ".txt"):Command.outputFile;
      try(
          // 准备写入到结果文件
          OutputStream outputStream = Files.newOutputStream(Paths.get(resultFilePath));
          Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
      ){
        int count = 0;
        for (GadgetChain chain : discoveredGadgets) {
          printGadgetChain(writer, chain,count++);
        }
      }
      LOGGER.info("[+] finally found {} vul chains. result file is : {}", discoveredGadgets.size(), resultFilePath);
    }else{
      LOGGER.info("[*] none vul chains found :( ");
    }
  }



  private static void printGadgetChain(Writer writer, GadgetChain chain, int index) throws IOException {
    writer.write(String.format("[%d] %s.%s%s (%d)%n",
        index,
        chain.getLinks().get(0).getMethod().getClassReference().getName(),
        chain.getLinks().get(0).getMethod().getName(),
        chain.getLinks().get(0).getMethod().getDesc(),
        chain.getLinks().get(0).getTaintedArgIndex())
    );
    for (int i = 1; i < chain.getLinks().size(); i++) {
      writer.write(String.format("        %s.%s%s (%d)%n",
          chain.getLinks().get(i).getMethod().getClassReference().getName(),
          chain.getLinks().get(i).getMethod().getName(),
          chain.getLinks().get(i).getMethod().getDesc(),
          chain.getLinks().get(i).getTaintedArgIndex())
      );
    }
    writer.write("\n");
  }


  /**
   * 是否为sink
   * @param method 待调用的方法
   * @param argIndex 污染参数
   * @param inheritanceMap 继承关系图
   * @return 是否为sink
   */
  private boolean isSink(MethodReference.Handle method, int argIndex, InheritanceMap inheritanceMap) {
    // 所有的sink都通过配置文件加载到了customSinks
    if (!customSinks.isEmpty()) {
      for (CustomSink customSink : customSinks) {
        if(Command.sinks.contains("ALL") || Command.sinks.contains(customSink.getType())){
          boolean flag = true;
          // 1. 进行类匹配
          if(customSink.isMatchSubClassMethod()){
            // 如果匹配子类重写方法
            if (customSink.getClassName() != null) {
              flag &= inheritanceMap.isSubclassOf(method.getClassReference(),new ClassReference.Handle(customSink.getClassName()));
            }
          }else{
            flag &= customSink.getClassName().equals(method.getClassReference().getName());
          }
          // 2. 进行方法匹配
          if (customSink.getMethod() != null) {
            flag &= customSink.getMethod().equals(method.getName());
          }
          // 3. 进行方法形参列表匹配
          if (customSink.getDesc()!=null && !"null".equals(customSink.getDesc())){
            flag &= customSink.getDesc().equals(method.getDesc());
          }
          // 4. 进行taintArg匹配
          if (customSink.getTaintArg() != -1){
            flag &= (customSink.getTaintArg()==argIndex);
          }
          if (flag) {
            return flag;
          }
        }
      }
      return false;
    }
    // 如果customSinks为空，则代表没加载sink，直接返回false
    return false;
  }

}
