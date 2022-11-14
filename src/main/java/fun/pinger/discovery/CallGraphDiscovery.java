package fun.pinger.discovery;

import fun.pinger.config.Command;
import fun.pinger.core.ScanTypeConfig;
import fun.pinger.core.SerializableDecider;
import fun.pinger.visitor.classVisitor.ModelGeneratorClassVisitor;
import fun.pinger.model.*;
import lombok.Getter;
import org.objectweb.asm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.*;


/**
 * @author P1n93r
 */
public class CallGraphDiscovery {
    private static final Logger LOGGER = LoggerFactory.getLogger(CallGraphDiscovery.class);

    @Getter
    private final Set<CallGraph> discoveredCalls = new HashSet<>();

    /**
     * 调用图发现
     * @param classFileList 已加载的class
     * @param config 扫描类型配置
     */
    public void discover(final List<ClassFile> classFileList, ScanTypeConfig config) throws IOException {
        //加载所有方法信息
        Map<MethodReference.Handle, MethodReference> methodMap = DataLoader.loadMethods();
        //加载所有类信息
        Map<ClassReference.Handle, ClassReference> classMap = DataLoader.loadClasses();
        //加载所有父子类、超类、实现类关系
        InheritanceMap inheritanceMap = InheritanceMap.load();
        //加载所有方法参数和返回值的污染关联
        Map<MethodReference.Handle, Set<Integer>> passthroughDataFlow = Command.enableTaintTrack ? PassthroughDiscovery.load() : Collections.EMPTY_MAP;

        SerializableDecider serializableDecider = config.getSerializableDecider(methodMap, inheritanceMap);

        for (ClassFile classFile : classFileList) {
            try (InputStream in = classFile.getInputStream()) {
                // 过滤package，只分析指定的package中的call-site
                if(Command.packageName!=null && !"".equals(Command.packageName)){
                    String targetPackage = Command.packageName.replaceAll("\\.", "/");
                    if(!classFile.getResourceName().contains(targetPackage)){
                        continue;
                    }
                }
                ClassReader cr = new ClassReader(in);
                try {
                    cr.accept(new ModelGeneratorClassVisitor(classMap, inheritanceMap, passthroughDataFlow, serializableDecider, Opcodes.ASM6,discoveredCalls),
                            ClassReader.EXPAND_FRAMES);
                } catch (Exception e) {
                    LOGGER.error("Error analyzing: " + classFile.getResourceName(), e);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void save() throws IOException {
        DataLoader.saveData(Paths.get("callgraph.dat"), new CallGraph.Factory(), discoveredCalls);
    }

}
