package fun.pinger.discovery;

import fun.pinger.utils.InheritanceUtil;
import fun.pinger.model.ClassFile;
import fun.pinger.model.ClassReference;

import fun.pinger.model.DataLoader;
import fun.pinger.model.MethodReference;
import fun.pinger.visitor.classVisitor.MethodDiscoveryClassVisitor;
import lombok.Getter;
import org.objectweb.asm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.*;

public class MethodDiscovery {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodDiscovery.class);

    @Getter
    private final List<ClassReference> discoveredClasses = new ArrayList<>();
    @Getter
    private final List<MethodReference> discoveredMethods = new ArrayList<>();

    /**
     * 存储解析到的: classes\methods\inheritance 数据
     */
    public void save() throws IOException {
        // 保存和读取使用Factory实现

        // classes.dat数据格式：
        // 类名(例：java/lang/String) 父类 接口A,接口B,接口C 是否接口 字段1!字段1access!字段1类型!字段2!字段2access!字段1类型
        DataLoader.saveData(Paths.get("classes.dat"), new ClassReference.Factory(), discoveredClasses);

        // methods.dat数据格式：
        // 类名 方法名 方法描述 是否静态方法 方法注解 方法形参注解
        DataLoader.saveData(Paths.get("methods.dat"), new MethodReference.Factory(), discoveredMethods);

        // 形成 类名(ClassReference.Handle)->类(ClassReference) 的映射关系
        Map<ClassReference.Handle, ClassReference> classMap = new HashMap<>();
        for (ClassReference clazz : discoveredClasses) {
            classMap.put(clazz.getHandle(), clazz);
        }
        // 根据得到的类信息，生成类继承图，存储到inheritanceMap.dat中
        InheritanceUtil.getInheritanceMap(classMap).save();
    }

    public void discover(final List<ClassFile> classFileList) throws Exception {
        for (ClassFile classFile : classFileList) {
            try (InputStream in = classFile.getInputStream()) {
                ClassReader cr = new ClassReader(in);
                try {
                    // 使用asm的ClassVisitor、MethodVisitor，利用观察模式去扫描所有的class和method并记录
                    cr.accept(new MethodDiscoveryClassVisitor(discoveredClasses, discoveredMethods), ClassReader.EXPAND_FRAMES);
                } catch (Exception e) {
                    LOGGER.error("Exception analyzing: " + classFile.getResourceName(), e);
                }
            } catch (Exception e) {
                LOGGER.error(e.getMessage());
            }
        }
    }

}
