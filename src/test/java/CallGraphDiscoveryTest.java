import fun.pinger.core.ConfigRepository;
import fun.pinger.core.ScanTypeConfig;
import fun.pinger.discovery.CallGraphDiscovery;
import fun.pinger.discovery.MethodDiscovery;
import fun.pinger.discovery.PassthroughDiscovery;
import fun.pinger.model.ClassFile;
import fun.pinger.utils.ClassLoaderUtil;
import fun.pinger.utils.ClassResourceUtil;
import org.junit.Test;
import java.nio.file.Paths;
import java.util.List;

/**
 * @author : P1n93r
 * @date : 2022/4/8 16:26
 */
public class CallGraphDiscoveryTest {

    @Test
    public void testDiscovery()throws Exception{
        ClassLoader classLoader = ClassLoaderUtil.getJarAndLibClassLoader(Paths.get("C:\\Users\\18148\\Downloads\\Test.jar"));
        List<ClassFile> classFiles = ClassResourceUtil.getAllClassFile(classLoader, true);
        ScanTypeConfig config = ConfigRepository.getConfig("jserial");

        MethodDiscovery methodDiscovery = new MethodDiscovery();
        methodDiscovery.discover(classFiles);
        methodDiscovery.save();

        PassthroughDiscovery passthroughDiscovery = new PassthroughDiscovery();
        passthroughDiscovery.discover(classFiles, config);
        passthroughDiscovery.save();

        // 启用污点分析，获取调用图
        CallGraphDiscovery callGraphDiscovery = new CallGraphDiscovery();
        callGraphDiscovery.discover(classFiles,config);
        callGraphDiscovery.save();
    }



}
