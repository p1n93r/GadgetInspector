package fun.pinger.config;

import fun.pinger.core.ConfigRepository;
import fun.pinger.core.ScanTypeConfig;
import fun.pinger.discovery.*;
import fun.pinger.model.ClassFile;
import fun.pinger.utils.ClassLoaderUtil;
import fun.pinger.utils.ClassResourceUtil;
import com.beust.jcommander.JCommander;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author : P1n93r
 * @date : 2022/4/7 17:54
 */
@Slf4j
public class Application {

    private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);

    public static void run(String[] args){
        printLogo();
        Command command = Command.getInstance();
        JCommander jc = JCommander.newBuilder().addObject(command).build();
        jc.parse(args);
        if (Command.help) {
            jc.usage();
            return;
        }
        printConfig();
        if (Command.targetFile.size() != 0 && !"".equals(Command.type)){
            start(false);
        }else if((!"".equals(Command.classPath)||!"".equals(Command.jarPath))&&!"".equals(Command.type)){
            // classPath就是自定义的class文件所在位置，jarPath就是自定义的jar文件所在位置
            // 后续通过URLClassLoader加载classPath下的class，以及jarPath下指定的jar
            start(true);
        }else {
            LOGGER.info("[-] no target file found or scan type is none.");
        }
    }


    private static void start(boolean useCustomPath){
        // 默认使用的source：java原生反序列化利用链source
        ScanTypeConfig scanType = ConfigRepository.getConfig(Command.type.isEmpty() ? "jserial" : Command.type);
        if(scanType==null){
            LOGGER.info("[-] your typed scanType is not found.");
            return;
        }
        try {
            List<ClassFile> classFiles = null;
            // 如果手动指定class和jar的位置
            if(useCustomPath){
                Path classPath = null;
                Path jarPath = null;
                if(!"".equals(Command.classPath)){
                    classPath = Paths.get(Command.classPath);
                }
                if(!"".equals(Command.jarPath)){
                    jarPath = Paths.get(Command.jarPath);
                }
                ClassLoader classLoader = ClassLoaderUtil.getCustomClassLoader(classPath,jarPath);
                classFiles = ClassResourceUtil.getAllClassFile(classLoader, Command.onlyJdk);
            }else{
                // 如果指定了就是springboot应用
                if(Command.isSpringBoot){
                    // 只能同时加载一个springboot应用到ClassLoader中
                    String currentTargetFile = Command.targetFile.get(0);
                    // 检查后缀是否为jar
                    if(currentTargetFile!=null && currentTargetFile.endsWith(".jar")){
                        ClassLoader classLoader = ClassLoaderUtil.getJarAndLibClassLoader(Paths.get(currentTargetFile));
                        classFiles = ClassResourceUtil.getAllClassFile(classLoader, Command.onlyJdk);
                    }else{
                        LOGGER.info("[-] the target file is not a spring-boot jar file, so skipped it");
                        return;
                    }
                }else {
                    // 非springboot项目，则根据后缀自动选择不同的ClassLoader初始化
                    if(Command.targetFile.get(0).endsWith(".war")){
                        ClassLoader classLoader = ClassLoaderUtil.getWarClassLoader(Paths.get(Command.targetFile.get(0)));
                        classFiles = ClassResourceUtil.getAllClassFile(classLoader, Command.onlyJdk);
                    }else{
                        // 如果是加载jar包，则可以同时加载多个jar到一个ClassLoader中
                        List<String> jarFiles = Command.targetFile;
                        // 遍历输入的targetFiles
                        List<Path> pathList = new ArrayList<>();
                        for (int index = jarFiles.size()-1; index >= 0; index--) {
                            if(!jarFiles.get(index).endsWith(".jar")){
                                // 如果输入的是一个目录,遍历目录里的jar，加入ClassLoader
                                File file = Paths.get(jarFiles.get(index)).toFile();
                                if (!file.exists()) {
                                    continue;
                                }
                                Files.walkFileTree(file.toPath(), new SimpleFileVisitor<Path>() {
                                    @Override
                                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                                        if (!file.getFileName().toString().endsWith(".jar")) {
                                            return FileVisitResult.CONTINUE;
                                        }
                                        File readFile = file.toFile();
                                        Path path = Paths.get(readFile.getAbsolutePath());
                                        if (Files.exists(path)) {
                                            pathList.add(path);
                                        }
                                        return FileVisitResult.CONTINUE;
                                    }
                                });
                            }else{
                                Path path = Paths.get(jarFiles.get(index)).toAbsolutePath();
                                if (!Files.exists(path)) {
                                    throw new IllegalArgumentException("[-] invalid jar path: " + path);
                                }
                                pathList.add(path);
                            }
                        }
                        ClassLoader classLoader = ClassLoaderUtil.getJarClassLoader(pathList.toArray(new Path[0]));
                        classFiles = ClassResourceUtil.getAllClassFile(classLoader, Command.onlyJdk);
                    }
                }
            }


            LOGGER.info(String.format("[*] current analyze target files: %s",Command.targetFile));

            // 如果不resume，则删除所有的dat文件
            if (!Command.resume) {
                LOGGER.info("[*] delete all existing dat files...");
                for (String datFile : Arrays.asList("classes.dat", "methods.dat", "inheritanceMap.dat", "passthrough.dat", "callgraph.dat", "sources.dat", "methodimpl.dat")) {
                    final Path path = Paths.get(datFile);
                    if (Files.exists(path)) {
                        Files.delete(path);
                    }
                }
            }
            // 这一部分主要是通过MethodDiscovery发现并存储 classes/methods/inheritance 数据
            if (!Files.exists(Paths.get("classes.dat")) || !Files.exists(Paths.get("methods.dat")) || !Files.exists(Paths.get("inheritanceMap.dat"))) {
                LOGGER.info("[*] running method discovery, in this section, will find and save classes/methods/inheritance.");
                MethodDiscovery methodDiscovery = new MethodDiscovery();
                methodDiscovery.discover(classFiles);
                //保存了类信息、方法信息、继承实现信息
                methodDiscovery.save();
                LOGGER.info(String.format("[*] found %d classes, %d methods",methodDiscovery.getDiscoveredClasses().size(),methodDiscovery.getDiscoveredMethods().size()));
            }

            // 如果启用污点分析，则会进行过程内污点分析(函数的第n个参数，可以影响本函数的返回值；也就是污点可以从函数的第n个参数流出到函数返回值)
            if (Command.enableTaintTrack && !Files.exists(Paths.get("passthrough.dat"))) {
                LOGGER.info("[*] analyzing methods for passthrough dataflow...");
                PassthroughDiscovery passthroughDiscovery = new PassthroughDiscovery();
                // 记录参数在方法调用链中的流动关联（如：A、B、C、D四个方法，调用链为A->B B->C C->D，其中参数随着调用关系从A流向B，在B调用C过程中作为入参并随着方法结束返回，最后流向D）
                // 该方法主要是追踪上面所说的"B调用C过程中作为入参并随着方法结束返回"，入参和返回值之间的关联
                passthroughDiscovery.discover(classFiles, scanType);
                passthroughDiscovery.save();
                LOGGER.info(String.format("[*] found %d passthroughDataFlow, %d methodCalls",passthroughDiscovery.getPassthroughDataFlow().size(),passthroughDiscovery.getMethodCalls().size()));
            }

            if (!Files.exists(Paths.get("callgraph.dat"))) {
                // 这个discovery主要是记录存在污点传播关系的call-site，例如caller的第1个参数，可以传播到callee的第2个参数
                LOGGER.info("[*] analyzing methods in order to build a call graph...");
                CallGraphDiscovery callGraphDiscovery = new CallGraphDiscovery();
                callGraphDiscovery.discover(classFiles, scanType);
                callGraphDiscovery.save();
                LOGGER.info(String.format("[*] found %d callGraph",callGraphDiscovery.getDiscoveredCalls().size()));
            }

            if (!Files.exists(Paths.get("sources.dat"))) {
                LOGGER.info("[*] discovering gadget chain source methods...");
                SourceDiscovery sourceDiscovery = scanType.getSourceDiscovery();
                // 查找利用链的入口（例：java原生反序列化的readObject）
                sourceDiscovery.discover();
                sourceDiscovery.save();
                LOGGER.info(String.format("[*] found %d sources",sourceDiscovery.getDiscoveredSources().size()));
            }
            LOGGER.info("[*] searching call graph for gadget chains...");
            GadgetChainDiscovery gadgetChainDiscovery = new GadgetChainDiscovery(scanType);
            // 根据上面的数据收集，最终分析利用链
            gadgetChainDiscovery.discover();
        }catch (Exception exception){
            exception.printStackTrace();
            System.exit(-1);
        }
    }

    private static void printConfig() {
        System.out.print("> target file: ");
        for (String targetFile : Command.targetFile) {
            System.out.print(targetFile + " ");
        }
        System.out.println();

        if (Command.isSpringBoot) {
            System.out.println("> think the target file is a spring-boot jar");
        }
        if (Command.packageName != null && !"".equals(Command.packageName)) {
            System.out.println("> package name: " + Command.packageName);
        }

        if (Command.classPath != null && !"".equals(Command.classPath)) {
            System.out.println("> class path: " + Command.classPath);
        }
        if (Command.jarPath != null && !"".equals(Command.jarPath)) {
            System.out.println("> jar path: " + Command.jarPath);
        }

        if (Command.type != null && !"".equals(Command.type)) {
            System.out.println("> scan type: " + Command.type);
        }
        if (Command.enableTaintTrack) {
            System.out.println("> use taint track analyze");
        }
        if (Command.resume) {
            System.out.println("> resume last time dat data");
        }
        if (Command.onlyJdk) {
            System.out.println("> only scan rj.jar from jdk");
        }
        if (Command.outputFile != null && !"".equals(Command.outputFile)) {
            System.out.println("> outputFile : " + Command.outputFile);
        }
        if (Command.slinksFile != null && !"".equals(Command.slinksFile)) {
            System.out.println("> custom slinksFile : " + Command.slinksFile);
        }
        if (Command.maxChainLength != -1) {
            System.out.println("> max chain length : " + Command.slinksFile);
        }
        if (Command.sinks != null && !"".equals(Command.sinks)) {
            System.out.println("> selected sinks : " + Command.sinks);
        }
        System.out.println("\r\n\r\n");
    }


    private static void printLogo(){
        String logo="                            GadgetInspector                       \n" +
                    "                             +----------+                         \n" +
                    "                             |  Source  |                         \n" +
                    "                             +----------+                         \n" +
                    "                             .-'  |  `-._                         \n" +
                    "                          .-'     |      `-._ [taint]             \n" +
                    "                       .-'        |          `-._                 \n" +
                    "                    .-'           |              `-.              \n" +
                    "               +---'-------+     +-----+-----+     +-`--`------+  \n" +
                    "               | Call-site |     | Call-site |     | Call-site |  \n" +
                    "               +-----------+     +-----------+     +-----------+  \n" +
                    "              _.-' | `.                       .-'    \\           \n" +
                    "          _.-'     |   `.         [taint]  .-'        \\  [taint] \n" +
                    "      _.-'         |     `.             .-'            \\         \n" +
                    "+----'-------+-----+-----+--`----+ +----'------+    +----`------+ \n" +
                    "|  ........  | Call-site |  ...  | | Call-site |    | Call-site | \n" +
                    "+------------+-----------+-------+ +-----------+    +-----------+ \n" +
                    "                                _.-'.'/ \\             | \\       \n" +
                    "                            _.-'  .' /   \\            |  \\      \n" +
                    "               [taint]  _.-'   .-'  /     \\           |   \\     \n" +
                    "                    _.-'    .-'    /       \\          |    \\    \n" +
                    "                _.-'     .-'      /         \\         |     \\   \n" +
                    "            _.-'      .-'        /           \\        |      \\  \n" +
                    "      .----'---.-----'---.------'-----.-------`--.----+---.---`--.\n" +
                    "      |  Sink  |   Sink  |    Sink    |   Sink   |  Sink  | Sink |\n" +
                    "      `--------^---------^------------^----------^--------^------'\n";

        logo  +=    "[!] forked from https://github.com/JackOfMostTrades/gadgetinspector. \n" ;

        System.out.println(logo);
    }





}
