package fun.pinger.config;

import com.beust.jcommander.Parameter;
import fun.pinger.core.ConfigRepository;
import fun.pinger.core.ScanTypeConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * @author P1n93r
 */
public class Command {

    private static Command self = null;

    private Command(){}


    public static Command getInstance(){
        if(Command.self==null){
            Command.self = new Command();
        }
        return Command.self;
    }


    @Parameter(names = {"-h", "--h", "--help"}, description = "show help", help = true)
    public static boolean help;

    @Parameter(names = { "--target-file"},description = "the jar or war file to scan")
    public static List<String> targetFile = new ArrayList<>();

    @Parameter(names = {"--is-springboot"}, description = "whether the target file is springboot jar",arity = 1)
    public static boolean isSpringBoot = false;

    @Parameter(names = {"--class-path"}, description = "your custom class path")
    public static String classPath = "";

    @Parameter(names = {"--jar-path"}, description = "your custom jar path")
    public static String jarPath = "";

    @Parameter(names = {"--package"}, description = "limit the scan package")
    public static String packageName = "";

    /**
     * 必须提供的参数
     */
    @Parameter(names = {"--type"} ,description = "scan type,available type is [ jserial, servlet, springMVC, struts ]")
    public static String type = "";

    /**
     * 默认开启污点追踪
     */
    @Parameter(names = {"--use-taint"}, description = "use taint track analyze", arity = 1)
    public static boolean enableTaintTrack = true;

    /**
     * 默认不使用上次扫描过程中收集的数据
     */
    @Parameter(names = {"--resume"}, description = "resume last time dat data", arity = 1)
    public static boolean resume = false;

    @Parameter(names = {"--only-jdk"}, description = "only load rt.jar from current runtime jre", arity = 1)
    public static boolean onlyJdk = false;

    @Parameter(names = {"--output"}, description = "output scan result file")
    public static String outputFile = "";

    /**
     * 指定自定义的额外的sink定义，默认存在一个基础的sink文件
     */
    @Parameter(names = {"--slinks-file"}, description = "self custom slinks file")
    public static String slinksFile = "";

    /**
     * 默认不开启路径爆炸防护
     */
    @Parameter(names = {"--max"}, description = "prevent path explosion,need define the max chain length")
    public static int maxChainLength = -1;


    @Parameter(names = {"--sinks"}, description = "select which sinks to find,available sinks are [JDBC, REFLECT, FILE, DOS, BCEL, XXE, RCE, JNDI, SSTI, ALL]")
    public static String sinks = "ALL";
}
