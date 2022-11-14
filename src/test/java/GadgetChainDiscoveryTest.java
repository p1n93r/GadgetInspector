import fun.pinger.core.ConfigRepository;
import fun.pinger.core.ScanTypeConfig;
import fun.pinger.discovery.*;
import fun.pinger.model.ClassFile;
import fun.pinger.utils.ClassLoaderUtil;
import fun.pinger.utils.ClassResourceUtil;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.List;

/**
 * @author : P1n93r
 * @date : 2022/4/8 19:08
 */
public class GadgetChainDiscoveryTest {

    @Test
    public void testDisvocery()throws Exception{
        ClassLoader classLoader = ClassLoaderUtil.getJarAndLibClassLoader(Paths.get("C:\\Users\\18148\\Downloads\\Test.jar"));
        List<ClassFile> classFiles = ClassResourceUtil.getAllClassFile(classLoader, false);
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

        // 别忘了加载source
        SourceDiscovery sourceDiscovery = config.getSourceDiscovery();
        //查找利用链的入口（例：java原生反序列化的readObject）
        sourceDiscovery.discover();
        sourceDiscovery.save();

        // 开启漏洞链发现
        GadgetChainDiscovery gadgetChainDiscovery = new GadgetChainDiscovery(config);
        // 不用开启save，会自动写如到文件
        gadgetChainDiscovery.discover();


    }


}
