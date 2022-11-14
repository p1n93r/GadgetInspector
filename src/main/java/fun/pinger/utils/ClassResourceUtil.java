package fun.pinger.utils;

import fun.pinger.model.ClassFile;
import com.google.common.reflect.ClassPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : P1n93r
 * @date : 2022/4/7 19:15
 */
public class ClassResourceUtil {


    private static final Logger LOGGER = LoggerFactory.getLogger(ClassResourceUtil.class);


    public static List<ClassFile> getAllClassFile(ClassLoader classLoader, boolean onlyJdk) throws Exception {
        ClassFile.setClassLoader(classLoader);
        // 加载rt.jar中的所有class
        List<ClassFile> result = new ArrayList<>(getRuntimeClasses());
//        List<ClassFile> result = new ArrayList<>();
        if (onlyJdk) {
            return result;
        }
        for (ClassPath.ClassInfo classInfo : ClassPath.from(classLoader).getAllClasses()) {

            Class clazz = classInfo.getClass().getSuperclass();
            Field targetField = clazz.getDeclaredField("file");
            targetField.setAccessible(true);
            File file = (File)targetField.get(classInfo);
            String absolutePath = file.getAbsolutePath();

            result.add(new ClassFile(classInfo.getResourceName(),absolutePath, true));
        }
        return result;
    }


    private static List<ClassFile> getRuntimeClasses() throws Exception {
        // 加载原生JDK中的class，却决于当前执行的JRE环境
        URL stringClassUrl = Object.class.getResource("String.class");
        URLConnection connection = stringClassUrl.openConnection();
        List<ClassFile> result = new ArrayList<>();
        if (connection instanceof JarURLConnection) {
            URL runtimeUrl = ((JarURLConnection) connection).getJarFileURL();
            URLClassLoader classLoader = new URLClassLoader(new URL[]{runtimeUrl});
            for (ClassPath.ClassInfo classInfo : ClassPath.from(classLoader).getAllClasses()) {

                Class clazz = classInfo.getClass().getSuperclass();
                Field targetField = clazz.getDeclaredField("file");
                targetField.setAccessible(true);
                File file = (File)targetField.get(classInfo);
                String absolutePath = file.getAbsolutePath();

                result.add(new ClassFile(classInfo.getResourceName(),absolutePath,true));
            }
        }
        if (!result.isEmpty()) {
            return result;
        }

        // Try finding all the JDK classes using the Java9+ modules method:
        try {
            FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
            Files.walk(fs.getPath("/")).forEach(p -> {
                if (p.toString().toLowerCase().endsWith(".class")) {
                    result.add(new ClassFile(p.toUri().toString(),p.toAbsolutePath().toString(),false));
                }
            });
        } catch (ProviderNotFoundException e) {
            // Do nothing; this is expected on versions below Java9
        }
        return result;
    }

}
