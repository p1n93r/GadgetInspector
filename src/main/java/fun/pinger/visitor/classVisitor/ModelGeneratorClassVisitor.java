package fun.pinger.visitor.classVisitor;

import fun.pinger.core.SerializableDecider;
import fun.pinger.model.CallGraph;
import fun.pinger.model.ClassReference;
import fun.pinger.model.InheritanceMap;
import fun.pinger.model.MethodReference;
import fun.pinger.visitor.methodVisitor.ModelGeneratorMethodVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.commons.JSRInlinerAdapter;

import java.util.Map;
import java.util.Set;

/**
 * @author : P1n93r
 * @date : 2022/4/8 16:02
 */
public class ModelGeneratorClassVisitor extends ClassVisitor {

    private final Map<ClassReference.Handle, ClassReference> classMap;
    private final InheritanceMap inheritanceMap;
    private final Map<MethodReference.Handle, Set<Integer>> passthroughDataFlow;
    private final SerializableDecider serializableDecider;
    private final Set<CallGraph> discoveredCalls;

    public ModelGeneratorClassVisitor(Map<ClassReference.Handle, ClassReference> classMap,
                                      InheritanceMap inheritanceMap,
                                      Map<MethodReference.Handle, Set<Integer>> passthroughDataFlow,
                                      SerializableDecider serializableDecider, int api, Set<CallGraph> discoveredCalls) {
        super(api);
        this.classMap = classMap;
        this.inheritanceMap = inheritanceMap;
        this.passthroughDataFlow = passthroughDataFlow;
        this.serializableDecider = serializableDecider;
        this.discoveredCalls = discoveredCalls;
    }

    private String name;
    private String signature;
    private String superName;
    private String[] interfaces;

    @Override
    public void visit(int version, int access, String name, String signature,
                      String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        this.name = name;
        this.signature = signature;
        this.superName = superName;
        this.interfaces = interfaces;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc,
                                     String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        ModelGeneratorMethodVisitor modelGeneratorMethodVisitor = new ModelGeneratorMethodVisitor(classMap,
                inheritanceMap, passthroughDataFlow, serializableDecider, api, mv, this.name, access, name, desc, signature, exceptions,discoveredCalls);

        return new JSRInlinerAdapter(modelGeneratorMethodVisitor, access, name, desc, signature, exceptions);
    }

    @Override
    public void visitOuterClass(String owner, String name, String desc) {
        super.visitOuterClass(owner, name, desc);
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        super.visitInnerClass(name, outerName, innerName, access);
    }

    @Override
    public void visitEnd() {
        super.visitEnd();
    }
}
