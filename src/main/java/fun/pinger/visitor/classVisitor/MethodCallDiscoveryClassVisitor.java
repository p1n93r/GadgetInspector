package fun.pinger.visitor.classVisitor;

import fun.pinger.model.MethodReference;
import fun.pinger.visitor.methodVisitor.MethodCallDiscoveryMethodVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.commons.JSRInlinerAdapter;

import java.util.Map;
import java.util.Set;

/**
 * @author : P1n93r
 * @date : 2022/4/8 12:08
 * 这个ClassVisitor作用很简单，就是用于发现当前visit类的所有函数的call-site
 */
public class MethodCallDiscoveryClassVisitor extends ClassVisitor {

    private String name = null;

    private final Map<MethodReference.Handle, Set<MethodReference.Handle>> methodCalls;

    public MethodCallDiscoveryClassVisitor(int api, Map<MethodReference.Handle, Set<MethodReference.Handle>> methodCalls) {
        super(api);
        this.methodCalls = methodCalls;
    }


    @Override
    public void visit(int version, int access, String name, String signature,
                      String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        if (this.name != null) {
            throw new IllegalStateException("ClassVisitor already visited a class!");
        }
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);

        // 在visit每个method的时候，创建MethodVisitor对method进行观察
        MethodCallDiscoveryMethodVisitor modelGeneratorMethodVisitor = new MethodCallDiscoveryMethodVisitor(
                api,this.methodCalls, mv, this.name, name, desc);
        return new JSRInlinerAdapter(modelGeneratorMethodVisitor, access, name, desc, signature, exceptions);
    }

    @Override
    public void visitEnd() {
        super.visitEnd();
    }
}
