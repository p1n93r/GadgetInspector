package fun.pinger.visitor.methodVisitor;

import fun.pinger.model.ClassReference;
import fun.pinger.model.MethodReference;
import org.objectweb.asm.MethodVisitor;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author : P1n93r
 * @date : 2022/4/8 12:09
 */
public class MethodCallDiscoveryMethodVisitor extends MethodVisitor {
    private final Set<MethodReference.Handle> calledMethods;

    /**
     * @param methodCalls 上一步ClassVisitor在在visitMethod时，传入的methodCalls
     * @param owner 上一步ClassVisitor在visitMethod时，传入的当前class
     * @param name visit的方法名
     * @param desc visit的方法描述
     */
    public MethodCallDiscoveryMethodVisitor(final int api, Map<MethodReference.Handle, Set<MethodReference.Handle>> methodCalls, final MethodVisitor mv, final String owner, String name, String desc) {
        super(api, mv);

        // 创建calledMethod收集调用到的method，最后形成集合{{sourceClass,sourceMethod}:[{targetClass,targetMethod}]}
        this.calledMethods = new HashSet<>();
        methodCalls.put(new MethodReference.Handle(new ClassReference.Handle(owner), name, desc), calledMethods);
    }

    /**
     * 当前visit的方法内，每一个方法调用都会执行该方法
     *
     * @param opcode 调用操作码：INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC or INVOKEINTERFACE.
     * @param owner 被调用的类名
     * @param name 被调用的方法
     * @param desc 被调用方法的描述
     * @param itf 被调用的类是否接口
     */
    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
        calledMethods.add(new MethodReference.Handle(new ClassReference.Handle(owner), name, desc));

        super.visitMethodInsn(opcode, owner, name, desc, itf);
    }
}