package fun.pinger.visitor.methodVisitor;

import fun.pinger.config.Command;
import fun.pinger.core.SerializableDecider;
import fun.pinger.model.CallGraph;
import fun.pinger.model.ClassReference;
import fun.pinger.model.InheritanceMap;
import fun.pinger.model.MethodReference;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author : P1n93r
 * @date : 2022/4/8 16:03
 */
public class ModelGeneratorMethodVisitor extends TaintTrackingMethodVisitor<String> {

    private final Map<ClassReference.Handle, ClassReference> classMap;
    private final InheritanceMap inheritanceMap;
    private final SerializableDecider serializableDecider;
    private final String owner;
    private final int access;
    private final String name;
    private final String desc;
    private final Set<CallGraph> discoveredCalls;

    public ModelGeneratorMethodVisitor(Map<ClassReference.Handle, ClassReference> classMap,
                                       InheritanceMap inheritanceMap,
                                       Map<MethodReference.Handle, Set<Integer>> passthroughDataFlow,
                                       SerializableDecider serializableDecider, final int api, final MethodVisitor mv,
                                       final String owner, int access, String name, String desc, String signature,
                                       String[] exceptions, Set<CallGraph> discoveredCalls) {
        super(inheritanceMap, passthroughDataFlow, api, mv, owner, access, name, desc, signature, exceptions);
        this.classMap = classMap;
        this.inheritanceMap = inheritanceMap;
        this.serializableDecider = serializableDecider;
        this.owner = owner;
        this.access = access;
        this.name = name;
        this.desc = desc;
        this.discoveredCalls = discoveredCalls;
    }

    @Override
    public void visitCode() {
        super.visitCode();

        int localIndex = 0;
        int argIndex = 0;
        // 使用arg前缀来表示方法入参，后续用于判断是否为目标调用方法的入参
        if ((this.access & Opcodes.ACC_STATIC) == 0) {
            setLocalTaint(localIndex, "arg" + argIndex);
            localIndex += 1;
            argIndex += 1;
        }
        for (Type argType : Type.getArgumentTypes(desc)) {
            setLocalTaint(localIndex, "arg" + argIndex);
            localIndex += argType.getSize();
            argIndex += 1;
        }
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String desc) {

        switch (opcode) {
            case Opcodes.GETSTATIC:
                break;
            case Opcodes.PUTSTATIC:
                break;
            // 入操作栈
            case Opcodes.GETFIELD:
                Type type = Type.getType(desc);
                if (type.getSize() == 1) {
                    Boolean isTransient = null;

                    // If a field type could not possibly be serialized, it's effectively transient
                    if (!couldBeSerialized(serializableDecider, inheritanceMap, new ClassReference.Handle(type.getInternalName()))) {
                        isTransient = Boolean.TRUE;
                    } else {
                        ClassReference clazz = classMap.get(new ClassReference.Handle(owner));
                        while (clazz != null) {
                            for (ClassReference.Member member : clazz.getMembers()) {
                                if (member.getName().equals(name)) {
                                    isTransient = (member.getModifiers() & Opcodes.ACC_TRANSIENT) != 0;
                                    break;
                                }
                            }
                            if (isTransient != null) {
                                break;
                            }
                            clazz = classMap.get(new ClassReference.Handle(clazz.getSuperClass()));
                        }
                    }

                    // 只有寻找反序列化利用链的时候才需要考虑transient
                    if(!"".equals(Command.type) && !"jserial".equals(Command.type)){
                        isTransient = false;
                    }

                    Set<String> newTaint = new HashSet<>();
                    if (!Boolean.TRUE.equals(isTransient)) {
                        // 如果可序列化
                        for (String s : getStackTaint(0)) {
                            newTaint.add(s + "." + name);
                        }
                    }
                    super.visitFieldInsn(opcode, owner, name, desc);
                    // 在调用方法前，这些东西都会先入操作栈
                    setStackTaint(0, newTaint);
                    return;
                }
                break;
            case Opcodes.PUTFIELD:
                break;
            default:
                throw new IllegalStateException("Unsupported opcode: " + opcode);
        }
        super.visitFieldInsn(opcode, owner, name, desc);
    }

    /**
     * 注意: 这个是在Method内的call-site时发生
     */
    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
        // 获取被调用method的参数和类型，非静态方法需要把实例类型放在第一个元素
        Type[] argTypes = Type.getArgumentTypes(desc);
        if (opcode != Opcodes.INVOKESTATIC) {
            Type[] extendedArgTypes = new Type[argTypes.length+1];
            System.arraycopy(argTypes, 0, extendedArgTypes, 1, argTypes.length);
            extendedArgTypes[0] = Type.getObjectType(owner);
            argTypes = extendedArgTypes;
        }

        switch (opcode) {
            case Opcodes.INVOKESTATIC:
            case Opcodes.INVOKEVIRTUAL:
            case Opcodes.INVOKESPECIAL:
            case Opcodes.INVOKEINTERFACE:
                if (!Command.enableTaintTrack) {
                    // 不进行污点分析，无脑记录调用关系
                    this.discoveredCalls.add(new CallGraph(
                            new MethodReference.Handle(new ClassReference.Handle(this.owner), this.name, this.desc),
                            new MethodReference.Handle(new ClassReference.Handle(owner), name, desc),
                            0,
                            "",
                            0));
                    break;
                }
                // 进行污点分析
                int stackIndex = 0;
                for (int i = 0; i < argTypes.length; i++) {
                    int argIndex = argTypes.length-1-i;
                    Type type = argTypes[argIndex];
                    // 从栈顶取操作数
                    Set<String> taint = getStackTaint(stackIndex);
                    // 如果取出来的操作数携带污点，则加入call-graph节点
                    if (taint.size() > 0) {
                        for (String argSrc : taint) {
                            // 取出出栈的参数，判断是否为当前方法的入参，arg前缀
                            if (!argSrc.substring(0, 3).equals("arg")) {
                                throw new IllegalStateException("Invalid taint arg: " + argSrc);
                            }
                            int dotIndex = argSrc.indexOf('.');
                            int srcArgIndex;
                            String srcArgPath;
                            if (dotIndex == -1) {
                                srcArgIndex = Integer.parseInt(argSrc.substring(3));
                                srcArgPath = null;
                            } else {
                                // 这种不带点的，是属性携带污点的情况，此时argSrc的形式为：arg0.Username
                                srcArgIndex = Integer.parseInt(argSrc.substring(3, dotIndex));
                                srcArgPath = argSrc.substring(dotIndex+1);
                            }
                            //记录参数流动关系
                            //argIndex：当前方法参数索引，srcArgIndex：对应上一级方法的参数索引
                            this.discoveredCalls.add(new CallGraph(
                                    new MethodReference.Handle(new ClassReference.Handle(this.owner), this.name, this.desc),
                                    new MethodReference.Handle(new ClassReference.Handle(owner), name, desc),
                                    srcArgIndex,
                                    srcArgPath,
                                    argIndex));
                        }
                    }

                    stackIndex += type.getSize();
                }
                break;
            default:
                throw new IllegalStateException("Unsupported opcode: " + opcode);
        }

        super.visitMethodInsn(opcode, owner, name, desc, itf);
    }
}

