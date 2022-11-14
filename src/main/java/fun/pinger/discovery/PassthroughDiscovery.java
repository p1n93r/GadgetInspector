package fun.pinger.discovery;

import fun.pinger.core.ScanTypeConfig;
import fun.pinger.core.SerializableDecider;
import fun.pinger.visitor.classVisitor.MethodCallDiscoveryClassVisitor;
import fun.pinger.visitor.classVisitor.PassthroughDataflowClassVisitor;
import fun.pinger.model.*;
import lombok.Getter;
import org.objectweb.asm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.*;



/**
 * @author P1n93r
 */
public class PassthroughDiscovery {

    private static final Logger LOGGER = LoggerFactory.getLogger(PassthroughDiscovery.class);

    /**
     * 记录方法调用关系图: {{sourceClass,sourceMethod}:[{targetClass,targetMethod}]}
     * 也就是call-site集合, caller->callee的关系
     * 这个call-site的唯一作用就是进行逆拓扑排序用
     */
    @Getter
    private final Map<MethodReference.Handle, Set<MethodReference.Handle>> methodCalls = new HashMap<>();

    /**
     * 方法返回值与哪个参数有关系,标记能影响返回值的参数，作为有效污点
     */
    @Getter
    private Map<MethodReference.Handle, Set<Integer>> passthroughDataFlow;

    public void discover(final List<ClassFile> classFileList, final ScanTypeConfig config) throws IOException {
        // 从前面MethodDiscovery存储的methods.dat、classes.dat文件中加载方法、类信息
        Map<MethodReference.Handle, MethodReference> methodMap = DataLoader.loadMethods();
        Map<ClassReference.Handle, ClassReference> classMap = DataLoader.loadClasses();

        // 从前面MethodDiscovery存储的inheritanceMap.dat文件中加载类继承关系
        InheritanceMap inheritanceMap = InheritanceMap.load();

        // 搜索方法间的调用关系，缓存至methodCalls集合，并且返回 类名->类资源 映射集合，也就是分析过的类
        Map<String, ClassFile> classResourceByName = discoverMethodCalls(classFileList);

        // 根据方法调用关系进行函数顺序逆拓扑，按顺序把最底层的函数放在前面，原理其实很简单：分析最外层函数的污点传播，需要知道内层callee函数的污点传播结果，所以想要分析所有函数的污点传播，需要先分析最底层的函数的污点传播
        List<MethodReference.Handle> sortedMethods = topologicallySortMethodCalls();

        // 进行所有函数的过程间污点传播分析
        passthroughDataFlow = calculatePassthroughDataFlow(classResourceByName, classMap, inheritanceMap, sortedMethods,
                config.getSerializableDecider(methodMap, inheritanceMap));
    }

    /**
     * 搜索method调用关联信息
     */
    private Map<String, ClassFile> discoverMethodCalls(final List<ClassFile> classFileList) throws IOException {
        Map<String, ClassFile> classResourcesByName = new HashMap<>();
        for (ClassFile classFile : classFileList) {
            try (InputStream in = classFile.getInputStream()) {
                ClassReader cr = new ClassReader(in);
                try {
                    // 这个visitor中会分析方法调用关系，并存储到methodCalls中
                    MethodCallDiscoveryClassVisitor visitor = new MethodCallDiscoveryClassVisitor(Opcodes.ASM6, methodCalls);
                    cr.accept(visitor, ClassReader.EXPAND_FRAMES);

                    // 记录一下分析过的类，记录格式：{类名: 对应的classFile}
                    classResourcesByName.put(visitor.getName(), classFile);
                } catch (Exception e) {
                    LOGGER.error("Error analyzing: " + classFile.getResourceName(), e);
                }
            }
        }
        return classResourcesByName;
    }

    /**
     * 对方法调用进行逆拓扑排序
     */
    private List<MethodReference.Handle> topologicallySortMethodCalls() {
        Map<MethodReference.Handle, Set<MethodReference.Handle>> outgoingReferences = new HashMap<>();

        // just deep clone methodCalls to outgoingReferences
        for (Map.Entry<MethodReference.Handle, Set<MethodReference.Handle>> entry : methodCalls.entrySet()) {
            MethodReference.Handle method = entry.getKey();
            outgoingReferences.put(method, new HashSet<>(entry.getValue()));
        }

        // Topological sort methods
        LOGGER.debug("Performing topological sort...");
        Set<MethodReference.Handle> dfsStack = new HashSet<>();
        Set<MethodReference.Handle> visitedNodes = new HashSet<>();
        List<MethodReference.Handle> sortedMethods = new ArrayList<>(outgoingReferences.size());

        for (MethodReference.Handle root : outgoingReferences.keySet()) {
            // 遍历集合中的起始方法，进行递归搜索DFS，通过逆拓扑排序，调用链的最末端排在最前面，这样才能实现入参、返回值、函数调用链之间的污点影响
            dfsTsort(outgoingReferences, sortedMethods, visitedNodes, dfsStack, root);
        }
        LOGGER.debug(String.format("Outgoing references %d, sortedMethods %d", outgoingReferences.size(), sortedMethods.size()));
        return sortedMethods;
    }


    private static void dfsTsort(Map<MethodReference.Handle, Set<MethodReference.Handle>> outgoingReferences,
                                 List<MethodReference.Handle> sortedMethods, Set<MethodReference.Handle> visitedNodes,
                                 Set<MethodReference.Handle> stack, MethodReference.Handle node) {

        if (stack.contains(node)) {
            return;
        }
        if (visitedNodes.contains(node)) {
            return;
        }
        // 根据起始方法，取出被调用的方法集
        Set<MethodReference.Handle> outgoingRefs = outgoingReferences.get(node);
        if (outgoingRefs == null) {
            return;
        }

        // 入栈，以便于递归不造成类似循环引用的死循环整合
        stack.add(node);
        for (MethodReference.Handle child : outgoingRefs) {
            dfsTsort(outgoingReferences, sortedMethods, visitedNodes, stack, child);
        }
        stack.remove(node);
        // 记录已被探索过的方法，用于在上层调用遇到重复方法时可以跳过
        visitedNodes.add(node);

        // 递归完成的探索，会添加进来
        sortedMethods.add(node);
    }


    /**
     * 发现方法返回值，也即和入参有关联的返回值，用于分析污染链路
     *
     * @param classResourceByName 类资源集合
     * @param classMap            类信息集合
     * @param inheritanceMap      类继承结构关系集合
     * @param sortedMethods       逆拓扑排序后的方法集合
     * @param serializableDecider 决策者
     */
    private static Map<MethodReference.Handle, Set<Integer>> calculatePassthroughDataFlow(Map<String, ClassFile> classResourceByName,
                                                                                          Map<ClassReference.Handle, ClassReference> classMap,
                                                                                          InheritanceMap inheritanceMap,
                                                                                          List<MethodReference.Handle> sortedMethods,
                                                                                          SerializableDecider serializableDecider){
        // 记录被分析的方法，第几个参数可以影响返回值
        final Map<MethodReference.Handle, Set<Integer>> passthroughDataFlow = new HashMap<>();

        // 遍历所有方法，然后asm观察所属类，经过前面DFS的排序，调用链最末端的方法在最前面
        // 调用链最末端的方法，其方法体内没有call-site
        for (MethodReference.Handle method : sortedMethods) {

            // 跳过static静态初始化代码，静态初始化块代码无法进行污点传播
            if (method.getName().equals("<clinit>")) {
                continue;
            }

            // 获取所属类进行观察
            ClassFile classResource = classResourceByName.get(method.getClassReference().getName());
            try (InputStream inputStream = classResource.getInputStream()) {
                ClassReader cr = new ClassReader(inputStream);
                try {
                    PassthroughDataflowClassVisitor cv = new PassthroughDataflowClassVisitor(classMap, inheritanceMap, passthroughDataFlow, serializableDecider, Opcodes.ASM6, method);
                    cr.accept(cv, ClassReader.EXPAND_FRAMES);

                    // 方法的哪个形参可以污染返回值
                    passthroughDataFlow.put(method, cv.getReturnTaint());
                } catch (Exception e) {
                    LOGGER.error("Exception analyzing " + method.getClassReference().getName() + ", analyzing method: " + method.getName()+method.getDesc(), e);
                }
            } catch (IOException e) {
                LOGGER.error("Unable to analyze " + method.getClassReference().getName(), e);
            }
        }
        return passthroughDataFlow;
    }


    public void save() throws IOException {
        if (passthroughDataFlow == null) {
            throw new IllegalStateException("Save called before discover()");
        }
        DataLoader.saveData(Paths.get("passthrough.dat"), new PassThroughFactory(), passthroughDataFlow.entrySet());
    }

    public static Map<MethodReference.Handle, Set<Integer>> load() throws IOException {
        Map<MethodReference.Handle, Set<Integer>> passthroughDataFlow = new HashMap<>();
        for (Map.Entry<MethodReference.Handle, Set<Integer>> entry : DataLoader.loadData(Paths.get("passthrough.dat"), new PassThroughFactory())) {
            passthroughDataFlow.put(entry.getKey(), entry.getValue());
        }
        return passthroughDataFlow;
    }


    public static class PassThroughFactory implements DataFactory<Map.Entry<MethodReference.Handle, Set<Integer>>> {
        @Override
        public Map.Entry<MethodReference.Handle, Set<Integer>> parse(String[] fields) {
            ClassReference.Handle clazz = new ClassReference.Handle(fields[0]);
            MethodReference.Handle method = new MethodReference.Handle(clazz, fields[1], fields[2]);

            Set<Integer> passthroughArgs = new HashSet<>();
            for (String arg : fields[3].split(",")) {
                if (arg.length() > 0) {
                    passthroughArgs.add(Integer.parseInt(arg));
                }
            }
            return new AbstractMap.SimpleEntry<>(method, passthroughArgs);
        }

        @Override
        public String[] serialize(Map.Entry<MethodReference.Handle, Set<Integer>> entry) {
            if (entry.getValue().size() == 0) {
                return null;
            }

            final String[] fields = new String[4];
            fields[0] = entry.getKey().getClassReference().getName();
            fields[1] = entry.getKey().getName();
            fields[2] = entry.getKey().getDesc();

            StringBuilder sb = new StringBuilder();
            for (Integer arg : entry.getValue()) {
                sb.append(arg);
                sb.append(",");
            }
            fields[3] = sb.toString();

            return fields;
        }
    }


}
