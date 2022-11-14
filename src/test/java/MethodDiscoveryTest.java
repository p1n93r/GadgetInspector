import fun.pinger.discovery.MethodDiscovery;
import fun.pinger.model.ClassFile;
import fun.pinger.utils.ClassLoaderUtil;
import fun.pinger.utils.ClassResourceUtil;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.List;

/**
 * @author : P1n93r
 * @date : 2022/4/8 10:56
 */
public class MethodDiscoveryTest {

    @Test
    public void testDiscovery()throws Exception{
        ClassLoader classLoader = ClassLoaderUtil.getJarAndLibClassLoader(Paths.get("C:\\Users\\18148\\Downloads\\Test.jar"));
        List<ClassFile> classFiles = ClassResourceUtil.getAllClassFile(classLoader, false);
        MethodDiscovery methodDiscovery = new MethodDiscovery();
        // 启动ASM解析
        methodDiscovery.discover(classFiles);
        // 解析之后存储
        methodDiscovery.save();
    }


}
