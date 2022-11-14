import fun.pinger.model.ClassFile;
import fun.pinger.utils.ClassLoaderUtil;
import fun.pinger.utils.ClassResourceUtil;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.List;

/**
 * @author : P1n93r
 * @date : 2022/4/8 11:04
 */
public class ClassResourceUtilTest {

    @Test
    public void testGetInputStream()throws Exception{
        ClassLoader classLoader = ClassLoaderUtil.getJarAndLibClassLoader(Paths.get("C:\\Users\\18148\\Downloads\\Test.jar"));
        List<ClassFile> classFiles = ClassResourceUtil.getAllClassFile(classLoader, false);
        System.out.println(classFiles.size());
        System.out.println(classFiles.get(25).getInputStream()!=null);
    }

}
