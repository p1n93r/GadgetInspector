package fun.pinger.visitor.methodVisitor;

import fun.pinger.config.Command;
import fun.pinger.core.SerializableDecider;
import fun.pinger.model.ClassReference;
import fun.pinger.model.InheritanceMap;
import fun.pinger.model.MethodReference;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author : P1n93r
 * @date : 2022/4/8 12:10
 */
public class PassthroughDataflowMethodVisitor extends TaintTrackingMethodVisitor<Integer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PassthroughDataflowMethodVisitor.class);


    /**
     * 类信息集合
     */
    private final Map<ClassReference.Handle, ClassReference> classMap;
    /**
     * 类继承关系集合
     */
    private final InheritanceMap inheritanceMap;

    /**
     * 方法哪个形参可以污染返回值
     * 充当检查表的角色，visit逆拓扑排序后的最底层方法时，并不依赖这个检查表，而是依赖我们硬编码自定义的一些初始passthrough
     */
    private final Map<MethodReference.Handle, Set<Integer>> passthroughDataFlow;

    /**
     * 当前visit的方法中，哪个形参可以污染返回值
     */
    private final Set<Integer> returnTaint;


    public Set<Integer> getReturnTaint() {
        return returnTaint;
    }

    /**
     * 决策者
     */
    private final SerializableDecider serializableDecider;

    private final int access;
    private final String desc;


    public PassthroughDataflowMethodVisitor(Map<ClassReference.Handle, ClassReference> classMap,
                                            InheritanceMap inheritanceMap, Map<MethodReference.Handle,
            Set<Integer>> passthroughDataFlow, SerializableDecider serializableDeciderMap, int api, MethodVisitor mv,
                                            String owner, int access, String name, String desc, String signature, String[] exceptions) {
        super(inheritanceMap, passthroughDataFlow, api, mv, owner, access, name, desc, signature, exceptions);
        this.classMap = classMap;
        this.inheritanceMap = inheritanceMap;
        this.passthroughDataFlow = passthroughDataFlow;
        this.serializableDecider = serializableDeciderMap;
        this.access = access;
        this.desc = desc;
        returnTaint = new HashSet<>();
    }


    /**
     * 这部分主要是方法开始前的本地变量表初始化
     * 也就是形参入本地变量表，如果是实例方法，则本地变量表的第0个元素代表的就是this，如果是静态方法，则第0个元素代表的就是实际方法参数
     */
    @Override
    public void visitCode() {
        // 需要注意super的调用，这里调用super.visitCode()就是为了初始化元素为空的本地变量表空间
        super.visitCode();

        int localIndex = 0;
        int argIndex = 0;
        if ((this.access & Opcodes.ACC_STATIC) == 0) {
            // 非静态方法，第一个局部变量应该为对象实例this
            // 注意，这里存储的是本地变量表，不是操作数栈
            setLocalTaint(localIndex, argIndex);
            localIndex += 1;
            argIndex += 1;
        }
        for (Type argType : Type.getArgumentTypes(desc)) {
            // 判断参数类型，得出变量占用空间大小，然后存储
            // 注意，这里存储的是本地变量表，不是操作数栈
            setLocalTaint(localIndex, argIndex);
            localIndex += argType.getSize();
            argIndex += 1;
        }
    }


    /**
     *  访问无操作数的指令，例如NOP, ACONST_NULL等等
     */
    @Override
    public void visitInsn(int opcode) {
        // 这里只针对return指令进行特殊处理: 获取操作数栈栈顶元素，加入到returnTaint中
        // 注意，这里只是获取，并没有pop，pop操作在super.visitInsn(opcode)中进行
        switch(opcode) {
            // 从当前方法返回int
            case Opcodes.IRETURN:
            // 从当前方法返回float
            case Opcodes.FRETURN:
            // 从当前方法返回对象引用
            case Opcodes.ARETURN:
                // 从操作数栈的栈顶取元素
                returnTaint.addAll(getStackTaint(0));
                break;
            // 从当前方法返回long
            case Opcodes.LRETURN:
            // 从当前方法返回double
            case Opcodes.DRETURN:
                // long和double占两个slot，所以index为1
                returnTaint.addAll(getStackTaint(1));
                break;
            // 从当前方法返回void
            case Opcodes.RETURN:
                break;
            default:
                break;
        }
        // 这里才是进行真正的指令处理
        super.visitInsn(opcode);
    }


    /**
     * visit field instruction
     * A field instruction is an instruction that loads or stores the value of a field of an object.
     * 也就是操作对象的属性的时候会调用本方法,支持的指令: GETSTATIC, PUTSTATIC, GETFIELD, PUTFIELD.
     */
    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String desc) {
        switch (opcode) {
            case Opcodes.GETSTATIC:
                break;
            case Opcodes.PUTSTATIC:
                // TODO 把这个污点传播断了的接上,适配传统漏洞的污点传播分析
                // 对于反序列化Gadget而言，无法控制静态成员，所以这里直接跳过了
                break;
            case Opcodes.GETFIELD:
                // GETFIELD指令，原本是pop先拿到一个objectref，然后根据指针拿到实际的value，然后就可以push了
                // GI没处理objectref，并且GI原本只是为了挖反序列化Gadget的（反序列化Gadget能直接控制FILED），所以这个地方只要判断FILED能序列化，FILED的Object如果存在污点，就能直接传播下去，但是如果是非反序列化查找，
                Type type = Type.getType(desc);
                if (type.getSize() == 1) {
                    Boolean isTransient = null;

                    // If a field type could not possibly be serialized, it's effectively transient
                    // 判断调用的字段类型是否可序列化
                    if (!couldBeSerialized(serializableDecider, inheritanceMap, new ClassReference.Handle(type.getInternalName()))) {
                        isTransient = Boolean.TRUE;
                    } else {
                        // 若调用的字段的Java类型可被序列化，则取当前类实例的所有字段，找出调用的字段，去判断是否被标识了transient
                        ClassReference clazz = classMap.get(new ClassReference.Handle(owner));
                        while (clazz != null) {
                            // 遍历字段，判断是否是transient类型，以确定是否可被序列化
                            for (ClassReference.Member member : clazz.getMembers()) {
                                if (member.getName().equals(name)) {
                                    isTransient = (member.getModifiers() & Opcodes.ACC_TRANSIENT) != 0;
                                    break;
                                }
                            }
                            if (isTransient != null) {
                                break;
                            }
                            // 如果仔当前类找不到这个属性，则向上父类查找，继续遍历，看看是否标示了transient
                            clazz = classMap.get(new ClassReference.Handle(clazz.getSuperClass()));
                        }
                    }

                    // 只有寻找反序列化利用链的时候才需要考虑transient
                    if(!"".equals(Command.type) && !"jserial".equals(Command.type)){
                        isTransient = false;
                    }

                    Set<Integer> taint;
                    if (!Boolean.TRUE.equals(isTransient)) {
                        // 若不是Transient字段，代表可序列化
                        // 则从栈顶取出它，取出的是this或某实例变量，即字段所属实例
                        // 这个地方取出来的就是visitCode时候，放在本地变量表中的第0个元素: 代表this的0
                        taint = getStackTaint(0);
                    } else {
                        taint = new HashSet<>();
                    }
                    super.visitFieldInsn(opcode, owner, name, desc);
                    // 这个地方的作用其实很简单: 保留污点传播，防止断开
                    setStackTaint(0, taint);
                    return;
                }
                break;
            case Opcodes.PUTFIELD:
                // TODO 这个地方应该是有bug，如果我GETFIELD之前进行了PUTFIELD，按这个逻辑就会丢失污点
                break;
            default:
                throw new IllegalStateException("Unsupported opcode: " + opcode);
        }
        super.visitFieldInsn(opcode, owner, name, desc);
    }


    /**
     * 方法内，每一个方法调用都会执行该方法
     * 此时就是分析方法内的call-site了
     * Visits a method instruction. A method instruction is an instruction that invokes a method.
     * @param opcode 调用操作码： INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC or INVOKEINTERFACE.
     * @param owner 被调用的类名
     * @param name 被调用的方法
     * @param desc 被调用方法的描述
     * @param itf 被调用的类是否接口
     */
    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
        // 获取callee参数类型
        Type[] argTypes = Type.getArgumentTypes(desc);
        if (opcode != Opcodes.INVOKESTATIC) {
            // 如果执行的非静态方法，则把数组第一个元素类型设置为该实例对象的类名，类比局部变量表
            Type[] extendedArgTypes = new Type[argTypes.length+1];
            System.arraycopy(argTypes, 0, extendedArgTypes, 1, argTypes.length);
            extendedArgTypes[0] = Type.getObjectType(owner);
            argTypes = extendedArgTypes;
        }
        // 获取callee返回值类型大小
        int retSize = Type.getReturnType(desc).getSize();

        Set<Integer> resultTaint;
        switch (opcode) {
            // 调用静态方法
            case Opcodes.INVOKESTATIC:
            // 调用实例方法
            case Opcodes.INVOKEVIRTUAL:
            // 调用超类构造方法，实例初始化方法，私有方法
            case Opcodes.INVOKESPECIAL:
            // 调用接口方法
            case Opcodes.INVOKEINTERFACE:
                // 构造污染参数集合
                final List<Set<Integer>> argTaint = new ArrayList<Set<Integer>>(argTypes.length);
                for (int i = 0; i < argTypes.length; i++) {
                    argTaint.add(null);
                }

                int stackIndex = 0;
                for (int i = 0; i < argTypes.length; i++) {
                    Type argType = argTypes[i];
                    if (argType.getSize() > 0) {
                        // 根据参数类型大小，从栈顶获取入参
                        // 注意这里不会进行操作数pop操作，只是获取，并不pop，实际的pop操作在super.visitMethodInsn(opcode, owner, name, desc, itf)中
                        argTaint.set(argTypes.length - 1 - i, getStackTaint(stackIndex + argType.getSize() - 1));
                    }
                    stackIndex += argType.getSize();
                }

                // TODO 考虑下是否构造方法需要无差别进行污点传播
                // 调用构造函数前，早就通过NEW实例了一个对象进入操作数栈，此时到这里拿到的是那个对象的副本，也就是argTaint.get(0)
                // 构造方法的调用
                if (name.equals("<init>")) {
                    // Pass result taint through to original taint set; the initialized object is directly tainted by parameters
                    // argTaint.get(0)代表被调用方法的实例
                    resultTaint = argTaint.get(0);
                } else {
                    resultTaint = new HashSet<>();
                }

                // 这里是一个重点，因为做过逆拓扑排序，才能保证每次总能从 passthroughDataFlow 中获取被调用方法的污点参数
                // 最最开始运行的时候，passthroughDataFlow是空的，只能在super中进行处理，super中定义了一个硬编码的passthrough专门用于处理这种底层的方法
                Set<Integer> passthrough = passthroughDataFlow.get(new MethodReference.Handle(new ClassReference.Handle(owner), name, desc));
                if (passthrough != null) {
                    // 这里只是说明callee的第x,y...个参数可以传递污点，但是实际上污点传播到这个参数里了没，并不一定
                    for (Integer passthroughDataflowArg : passthrough) {
                        resultTaint.addAll(argTaint.get(passthroughDataflowArg));
                    }
                }
                break;
            default:
                throw new IllegalStateException("Unsupported opcode: " + opcode);
        }

        super.visitMethodInsn(opcode, owner, name, desc, itf);

        // 只有callee的retSize大于0，表示callee存在返回值，这样才能污点传播下去
        // 如果callee没返回值，那么就算callee可以传播污点，那这条路径上，污点也断开了
        if (retSize > 0) {
            getStackTaint(retSize-1).addAll(resultTaint);
        }
        // 构造方法虽然没返回值，但是调用NEW指令的时候，就push了一个objectref进操作数栈
        // 如果认为当前调用的构造方法可以污染构造的对象，那么应该对前面NEW的那个objectref进行污点传播
        // TODO 斟酌下，应该可以放一个别的标志
//        if (name.equals("<init>") && retSize == 0){
//            getStackTaint(0).addAll(resultTaint);
//        }

    }
}
