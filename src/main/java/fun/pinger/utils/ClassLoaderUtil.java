package fun.pinger.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * @author : P1n93r
 * @date : 2022/4/7 18:41
 */
public class ClassLoaderUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClassLoaderUtil.class);


    /**
     * 处理war包的ClassLoader
     */
    public static ClassLoader getWarClassLoader(Path warPath) throws IOException {
        //创建临时文件夹，在jvm shutdown自动删除
        final Path tmpDir = Files.createTempDirectory("exploded-war");
        // Delete the temp directory at shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> FileUtil.removeDir(tmpDir.toFile())));

        // Extract to war to the temp directory
        try (JarInputStream jarInputStream = new JarInputStream(Files.newInputStream(warPath))) {
            JarEntry jarEntry;
            while ((jarEntry = jarInputStream.getNextJarEntry()) != null) {
                Path fullPath = tmpDir.resolve(jarEntry.getName());
                if (!jarEntry.isDirectory()) {
                    Path dirName = fullPath.getParent();
                    if (dirName == null) {
                        throw new IllegalStateException("Parent of item is outside temp directory.");
                    }
                    if (!Files.exists(dirName)) {
                        Files.createDirectories(dirName);
                    }
                    try (OutputStream outputStream = Files.newOutputStream(fullPath)) {
                        FileUtil.copy(jarInputStream, outputStream);
                    }
                }
            }
        }
        final List<URL> classPathUrls = new ArrayList<>();
        classPathUrls.add(tmpDir.resolve("WEB-INF/classes").toUri().toURL());
        Files.list(tmpDir.resolve("WEB-INF/lib")).forEach(p -> {
            try {
                classPathUrls.add(p.toUri().toURL());
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        });
        URLClassLoader classLoader = new URLClassLoader(classPathUrls.toArray(new URL[classPathUrls.size()]));
        return classLoader;
    }

    /**
     * 专门处理spring-boot jar
     * 获取jar包中 BOOT-INF/classes 以及 BOOT-INF/lib 下的class
     */
    public static ClassLoader getJarAndLibClassLoader(Path jarPath) throws IOException {
        //创建临时文件夹，在jvm shutdown自动删除
        final Path tmpDir = Files.createTempDirectory("exploded-jar");
        // Delete the temp directory at shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> FileUtil.removeDir(tmpDir.toFile())));
        // Extract to war to the temp directory
        try (JarInputStream jarInputStream = new JarInputStream(Files.newInputStream(jarPath))) {
            JarEntry jarEntry;
            while ((jarEntry = jarInputStream.getNextJarEntry()) != null) {
                Path fullPath = tmpDir.resolve(jarEntry.getName());
                if (!jarEntry.isDirectory()) {
                    Path dirName = fullPath.getParent();
                    if (dirName == null) {
                        throw new IllegalStateException("Parent of item is outside temp directory.");
                    }
                    if (!Files.exists(dirName)) {
                        Files.createDirectories(dirName);
                    }
                    try (OutputStream outputStream = Files.newOutputStream(fullPath)) {
                        FileUtil.copy(jarInputStream, outputStream);
                    }
                }
            }
        }
        final List<URL> classPathUrls = new ArrayList<>();
        // spring-boot
        if (Files.exists(tmpDir.resolve("BOOT-INF"))) {
            classPathUrls.add(tmpDir.resolve("BOOT-INF/classes").toUri().toURL());
            Files.list(tmpDir.resolve("BOOT-INF/lib")).forEach(p -> {
                try {
                    classPathUrls.add(p.toUri().toURL());
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
            });
        }else {
            // shadow jar / common lib jar
            classPathUrls.add(tmpDir.toUri().toURL());
        }
        return new URLClassLoader(classPathUrls.toArray(new URL[0]));
    }

    /**
     * 加载所有指定的jar
     * 这些jar不是像springboot那种jar，而是一些底层jar，jar内不再包含libs
     */
    public static ClassLoader getJarClassLoader(Path ... jarPaths) throws IOException {
        final List<URL> classPathUrls = new ArrayList<>(jarPaths.length);
        for (Path jarPath : jarPaths) {
            if (!Files.exists(jarPath) || Files.isDirectory(jarPath)) {
                throw new IllegalArgumentException("Path \"" + jarPath + "\" is not a path to a file.");
            }
            classPathUrls.add(jarPath.toUri().toURL());
        }
        URLClassLoader classLoader = new URLClassLoader(classPathUrls.toArray(new URL[classPathUrls.size()]));
        return classLoader;
    }

    /**
     * 对于WEB应用的处理无非就是加载class和jar
     * 这里可以手动指定class的位置和jar的位置
     */
    public static ClassLoader getCustomClassLoader(Path classPath,Path jarPath) throws IOException {
        final List<URL> classPathUrls = new ArrayList<>();
        if (classPath!=null && Files.exists(classPath)) {
            classPathUrls.add(classPath.toUri().toURL());
        }
        if(jarPath!=null && Files.exists(jarPath)){
            Files.list(jarPath).forEach(p -> {
                try {
                    classPathUrls.add(p.toUri().toURL());
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        if( (classPath==null || !Files.exists(classPath)) && (jarPath==null || !Files.exists(jarPath)) ){
            throw new RuntimeException("your custom classPath or jarPath is none or wrong.");
        }
        return new URLClassLoader(classPathUrls.toArray(new URL[0]));
    }


}
