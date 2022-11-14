package fun.pinger.visitor.classVisitor;

import fun.pinger.core.SerializableDecider;
import fun.pinger.model.ClassReference;
import fun.pinger.model.InheritanceMap;
import fun.pinger.model.MethodReference;
import fun.pinger.visitor.methodVisitor.PassthroughDataflowMethodVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.commons.JSRInlinerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author : P1n93r
 * @date : 2022/4/8 12:10
 */
public class PassthroughDataflowClassVisitor extends ClassVisitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(PassthroughDataflowClassVisitor.class);

    /**
     * 类信息集合
     */
    Map<ClassReference.Handle, ClassReference> classMap;
    /**
     * 要观察的方法
     */
    private final MethodReference.Handle methodToVisit;
    /**
     * 类继承关系集合
     */
    private final InheritanceMap inheritanceMap;
    /**
     * 方法的哪个形参可以污染返回值
     */
    private final Map<MethodReference.Handle, Set<Integer>> passthroughDataFlow;
    /**
     * 决策者
     */
    private final SerializableDecider serializableDecider;
    /**
     * 当前visit的类名
     */
    private String name;

    /**
     * 关键过程间污点分析逻辑都在这个MethodVisitor中
     */
    private PassthroughDataflowMethodVisitor passthroughDataflowMethodVisitor;

    public PassthroughDataflowClassVisitor(Map<ClassReference.Handle, ClassReference> classMap,
                                           InheritanceMap inheritanceMap, Map<MethodReference.Handle, Set<Integer>> passthroughDataFlow,
                                           SerializableDecider serializableDecider, int api, MethodReference.Handle methodToVisit) {
        super(api);
        this.classMap = classMap;
        this.inheritanceMap = inheritanceMap;
        this.methodToVisit = methodToVisit;
        this.passthroughDataFlow = passthroughDataFlow;
        this.serializableDecider = serializableDecider;
    }

    @Override
    public void visit(int version, int access, String name, String signature,
                      String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        this.name = name;
        // 检查类名是否一致
        if (!this.name.equals(methodToVisit.getClassReference().getName())) {
            throw new IllegalStateException("Expecting to visit " + methodToVisit.getClassReference().getName() + " but instead got " + this.name);
        }
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        // 只观察选定的method
        if (!name.equals(methodToVisit.getName()) || !desc.equals(methodToVisit.getDesc())) {
            return null;
        }
        if (passthroughDataflowMethodVisitor != null) {
            throw new IllegalStateException("Constructing passthroughDataflowMethodVisitor twice!");
        }

        // 对目标method进行观察
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);

        passthroughDataflowMethodVisitor = new PassthroughDataflowMethodVisitor(
                classMap, inheritanceMap, this.passthroughDataFlow, serializableDecider,
                api, mv, this.name, access, name, desc, signature, exceptions);

        return new JSRInlinerAdapter(passthroughDataflowMethodVisitor, access, name, desc, signature, exceptions);
    }

    /**
     * 返回的Set含义: 方法返回值可以被哪个参数污染
     */
    public Set<Integer> getReturnTaint() {
        if (passthroughDataflowMethodVisitor == null) {
            // throw new IllegalStateException("Never constructed the passthroughDataflowMethodVisitor!");
            LOGGER.error(String.format("[!] never constructed the passthroughDataflowMethodVisitor! maybe never visited target method. the target method is %s, current visit class is %s", this.methodToVisit, this.name));
            return new HashSet<>(0);
        }
        return passthroughDataflowMethodVisitor.getReturnTaint();
    }
}