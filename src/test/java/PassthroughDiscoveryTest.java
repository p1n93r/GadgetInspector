import fun.pinger.core.ConfigRepository;
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
 * @date : 2022/4/8 14:15
 */
public class PassthroughDiscoveryTest {

    @Test
    public void testDiscovery()throws Exception{
        ClassLoader classLoader = ClassLoaderUtil.getJarAndLibClassLoader(Paths.get("E:\\new-test\\foodemo\\foodemo.jar"));
        List<ClassFile> classFiles = ClassResourceUtil.getAllClassFile(classLoader, false);

        // 在执行passthroughDiscovery之前，需要先收集class、methods等信息
        MethodDiscovery methodDiscovery = new MethodDiscovery();
        // 启动ASM解析
        methodDiscovery.discover(classFiles);
        // 解析之后存储
        methodDiscovery.save();
        // 最后才能执行passthroughDiscovery
        PassthroughDiscovery passthroughDiscovery = new PassthroughDiscovery();
        passthroughDiscovery.discover(classFiles, ConfigRepository.getConfig("jserial"));
        passthroughDiscovery.save();
    }







}
