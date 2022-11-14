package fun.pinger.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;


/**
 * @author P1n93r
 */
public class ClassFile {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClassFile.class);

    private static ClassLoader classLoader;
    private final String resourceName;
    private final Boolean isClassLoaderResource;
    private final String jarPath;

    public ClassFile(String resourceName,String jarPath, Boolean isClassLoaderResource) {
        this.resourceName = resourceName;
        this.jarPath = jarPath;
        this.isClassLoaderResource = isClassLoaderResource;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ClassFile classFile = (ClassFile) o;
        return Objects.equals(resourceName, classFile.resourceName);
    }

    @Override
    public int hashCode() {
        return resourceName != null ? resourceName.hashCode() : 0;
    }

    public InputStream getInputStream(){
        try {
            if(isClassLoaderResource){
                return classLoader.getResourceAsStream(resourceName);
            }else{
                return Files.newInputStream(Paths.get(new URI(resourceName)));
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    public String getResourceName() {
        return resourceName;
    }

    public String getJarPath() {
        return jarPath;
    }

    public static void setClassLoader(ClassLoader classLoader) {
        ClassFile.classLoader = classLoader;
    }

}