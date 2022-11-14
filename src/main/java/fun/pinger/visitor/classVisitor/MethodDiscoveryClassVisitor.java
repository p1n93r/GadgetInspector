package fun.pinger.visitor.classVisitor;

import fun.pinger.model.ClassReference;
import fun.pinger.model.MethodReference;
import fun.pinger.visitor.methodVisitor.AnnotationMethodVisitor;
import org.objectweb.asm.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author : P1n93r
 * @date : 2022/4/8 11:56
 */
public class MethodDiscoveryClassVisitor extends ClassVisitor {
    /**
     * 当前访问的类的类名
     */
    private String name;

    /**
     * 当前访问的类的超类
     */
    private String superName;
    private String[] interfaces;
    boolean isInterface;

    /**
     * 类的所有字段
     */
    private List<ClassReference.Member> members;

    private ClassReference.Handle classHandle;

    /**
     * 类注解
     */
    private Set<String> annotations;

    /**
     * 传到visitor内的 discoveredClasses 和 discoveredMethods
     */
    List<ClassReference> discoveredClasses;
    List<MethodReference> discoveredMethods;


    public MethodDiscoveryClassVisitor(List<ClassReference> discoveredClasses, List<MethodReference> discoveredMethods) {
        super(Opcodes.ASM6);
        this.discoveredClasses = discoveredClasses;
        this.discoveredMethods = discoveredMethods;
    }

    @Override
    public void visit (int version, int access, String name, String signature, String superName, String[]interfaces) {
        this.name = name;
        this.superName = superName;
        this.interfaces = interfaces;
        this.isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
        this.members = new ArrayList<>();
        this.classHandle = new ClassReference.Handle(name);
        annotations = new HashSet<>();
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        annotations.add(descriptor);
        return super.visitAnnotation(descriptor, visible);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        // 访问非静态字段(静态字段不可控，对于漏洞分析无用)
        if ((access & Opcodes.ACC_STATIC) == 0) {
            Type type = Type.getType(desc);
            String typeName;
            if (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) {
                typeName = type.getInternalName();
            } else {
                typeName = type.getDescriptor();
            }
            members.add(new ClassReference.Member(name, access, new ClassReference.Handle(typeName)));
        }
        return super.visitField(access, name, desc, signature, value);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;

        MethodVisitor methodVisitor = super.visitMethod(access, name, desc, signature, exceptions);

        // 使用AnnotationMethodVisitor进行方法访问，获取注解信息等
        return new AnnotationMethodVisitor(Opcodes.ASM6, methodVisitor, discoveredMethods, classHandle, name, desc, isStatic);
    }

    @Override
    public void visitEnd() {
        ClassReference classReference = new ClassReference(
                name,
                superName,
                interfaces,
                isInterface,
                //把所有找到的字段封装
                members.toArray(new ClassReference.Member[0]),
                annotations);
        //找到一个方法遍历完成后，添加类到缓存
        discoveredClasses.add(classReference);
        super.visitEnd();
    }
}
