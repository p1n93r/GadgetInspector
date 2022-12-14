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
        // ??????arg????????????????????????????????????????????????????????????????????????????????????
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
            // ????????????
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

                    // ?????????????????????????????????????????????????????????transient
                    if(!"".equals(Command.type) && !"jserial".equals(Command.type)){
                        isTransient = false;
                    }

                    Set<String> newTaint = new HashSet<>();
                    if (!Boolean.TRUE.equals(isTransient)) {
                        // ??????????????????
                        for (String s : getStackTaint(0)) {
                            newTaint.add(s + "." + name);
                        }
                    }
                    super.visitFieldInsn(opcode, owner, name, desc);
                    // ??????????????????????????????????????????????????????
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
     * ??????: ????????????Method??????call-site?????????
     */
    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
        // ???????????????method??????????????????????????????????????????????????????????????????????????????
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
                    // ????????????????????????????????????????????????
                    this.discoveredCalls.add(new CallGraph(
                            new MethodReference.Handle(new ClassReference.Handle(this.owner), this.name, this.desc),
                            new MethodReference.Handle(new ClassReference.Handle(owner), name, desc),
                            0,
                            "",
                            0));
                    break;
                }
                // ??????????????????
                int stackIndex = 0;
                for (int i = 0; i < argTypes.length; i++) {
                    int argIndex = argTypes.length-1-i;
                    Type type = argTypes[argIndex];
                    // ?????????????????????
                    Set<String> taint = getStackTaint(stackIndex);
                    // ???????????????????????????????????????????????????call-graph??????
                    if (taint.size() > 0) {
                        for (String argSrc : taint) {
                            // ???????????????????????????????????????????????????????????????arg??????
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
                                // ????????????????????????????????????????????????????????????argSrc???????????????arg0.Username
                                srcArgIndex = Integer.parseInt(argSrc.substring(3, dotIndex));
                                srcArgPath = argSrc.substring(dotIndex+1);
                            }
                            //????????????????????????
                            //argIndex??????????????????????????????srcArgIndex???????????????????????????????????????
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

