package fun.pinger.visitor.methodVisitor;

import fun.pinger.model.ClassReference;
import fun.pinger.model.MethodReference;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;

import java.util.List;

/**
 * @author : P1n93r
 * @date : 2022/4/12 13:25
 * 主要是用于发现方法上的注解，以及方法的参数注解
 */
public class AnnotationMethodVisitor extends MethodVisitor {

    /**
     * 方法所在的类
     */
    private final ClassReference.Handle classHandle;

    /**
     * 方法名
     */
    private final String name;

    /**
     * 方法描述
     */
    private final String desc;
    private final boolean isStatic;

    /**
     * 方法注解
     */
    private String methodAnnotation = "";

    /**
     * 方法形参注解
     */
    private String paramAnnotation = "";

    /**
     * 已发现的方法
     */
    private List<MethodReference> discoveredMethods;


    public AnnotationMethodVisitor(int api, MethodVisitor methodVisitor, List<MethodReference> discoveredMethods, ClassReference.Handle classHandle, String name, String desc, boolean isStatic) {
        super(api, methodVisitor);
        this.discoveredMethods = discoveredMethods;
        this.classHandle = classHandle;
        this.name = name;
        this.desc = desc;
        this.isStatic = isStatic;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        this.methodAnnotation += descriptor;
        return super.visitAnnotation(descriptor, visible);
    }

    @Override
    public void visitCode() {
        super.visitCode();
    }

    @Override
    public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
        paramAnnotation += parameter + ":" + descriptor;
        return super.visitParameterAnnotation(parameter, descriptor, visible);
    }

    @Override
    public void visitParameter(String name, int access) {
        super.visitParameter(name, access);
    }


    @Override
    public void visitEnd() {
        //找到一个方法，添加到缓存
        this.discoveredMethods.add(new MethodReference(
                //类名
                classHandle,
                name,
                desc,
                isStatic,
                // 方法注解和形参注解
                methodAnnotation,
                paramAnnotation
        ));
        super.visitEnd();
    }
}
