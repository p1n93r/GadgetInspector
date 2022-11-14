package fun.pinger.visitor.methodVisitor;

import fun.pinger.core.SerializableDecider;
import fun.pinger.model.ClassReference;
import fun.pinger.model.InheritanceMap;
import fun.pinger.model.MethodReference;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AnalyzerAdapter;
import java.util.*;

public class TaintTrackingMethodVisitor<T> extends MethodVisitor {


    /**
     * 其实这个列表特别重要,代表call-site是否能传播污点
     * 这个列表影响逆拓扑call-site中，最深层call-site的taintArg的结果
     * 实际上逆拓扑call-site中，最深层call-site的tiantArg其实不是计算出来的，而是通过这个列表硬编码得到的
     * 所以这个列表需要慎重维护
     * TODO: 持续维护
     *
     * 一些说明：
     * 例如{ "org/springframework/web/multipart/MultipartFile", "getInputStream", "()Ljava/io/InputStream;", 0 }
     * 表示对于对应的call-site，污点会流入call-site的返回值中，也就是MultipartFile#getInputStream()的返回值中（返回值被标记为污点0）
     */
    private static final Object[][] PASSTHROUGH_DATAFLOW = new Object[][] {

            { "java/io/FileInputStream", "<init>", "(Ljava/lang/String;)V", 1 },
            { "org/xml/sax/InputSource", "<init>", "(Ljava/io/InputStream;)V", 1 },
            { "org/xml/sax/InputSource", "<init>", "(Ljava/io/Reader;)V", 1 },
            { "javax/xml/transform/stream/StreamSource", "<init>", "(Ljava/lang/String;)V", 1 },

            { "org/springframework/web/multipart/MultipartFile", "getBytes", "()[B", 0 },
            { "org/springframework/web/multipart/MultipartFile", "getInputStream", "()Ljava/io/InputStream;", 0 },
            { "org/springframework/web/multipart/MultipartFile", "getOriginalFilename", "()Ljava/lang/String;", 0 },


            { "java/util/Base64$Decoder", "decode", "(Ljava/lang/String;)[B", 1 },
            { "java/lang/String", "format", "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;", 1 },
            { "java/io/StringReader", "<init>", "(Ljava/lang/String;)V", 1 },
            { "java/io/InputStreamReader", "<init>", "(Ljava/io/InputStream;Ljava/lang/String;)V", 1 },
            { "java/io/BufferedReader", "<init>", "(Ljava/io/Reader;)V", 1 },
            { "java/io/StringBufferInputStream", "<init>", "(Ljava/lang/String;)V", 1 },
            { "java/util/Scanner", "<init>", "(Ljava/io/InputStream;)V", 1 },
            { "java/util/Scanner", "useDelimiter", "(Ljava/lang/String;)Ljava/util/Scanner;", 0 },
            { "java/util/Scanner", "next", "()Ljava/lang/String;", 0 },
            { "java/lang/String", "replaceAll", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", 0 },


            { "java/lang/Object", "toString", "()Ljava/lang/String;", 0 },

            // Taint from ObjectInputStream. Note that defaultReadObject() is handled differently below
            { "java/io/ObjectInputStream", "readObject", "()Ljava/lang/Object;", 0},
            { "java/io/ObjectInputStream", "readFields", "()Ljava/io/ObjectInputStream$GetField;", 0},
            { "java/io/ObjectInputStream$GetField", "get", "(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;", 0 },

            // Pass taint from class name to returned class
            { "java/lang/Object", "getClass", "()Ljava/lang/Class;", 0 },
            { "java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", 0 },
            // Pass taint from class or method name to returned method
            { "java/lang/Class", "getMethod", "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", 0, 1 },
            // Pass taint from class to methods
            { "java/lang/Class", "getMethods", "()[Ljava/lang/reflect/Method;", 0 },

            { "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V", 0, 1 },
            { "java/lang/StringBuilder", "<init>", "(Ljava/lang/CharSequence;)V", 0, 1 },
            { "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", 0, 1 },
            { "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", 0, 1 },
            { "java/lang/StringBuilder", "append", "(Ljava/lang/StringBuffer;)Ljava/lang/StringBuilder;", 0, 1 },
            { "java/lang/StringBuilder", "append", "(Ljava/lang/CharSequence;)Ljava/lang/StringBuilder;", 0, 1 },
            { "java/lang/StringBuilder", "append", "(Ljava/lang/CharSequence;II)Ljava/lang/StringBuilder;", 0, 1 },
            { "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", 0 },

            { "java/io/ByteArrayInputStream", "<init>", "([B)V", 1 },
            { "java/io/ByteArrayInputStream", "<init>", "([BII)V", 1 },
            { "java/io/ObjectInputStream", "<init>", "(Ljava/io/InputStream;)V", 1},
            { "java/io/File", "<init>", "(Ljava/lang/String;I)V", 1},
            { "java/io/File", "<init>", "(Ljava/lang/String;Ljava/io/File;)V", 1},
            { "java/io/File", "<init>", "(Ljava/lang/String;)V", 1},
            { "java/io/File", "<init>", "(Ljava/lang/String;Ljava/lang/String;)V", 1},

            { "java/nio/paths/Paths", "get", "(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;", 0},

            { "java/net/URL", "<init>", "(Ljava/lang/String;)V", 1 },

            //===========================================HttpServletRequest-start=====================================
            // ServletRequest-parameter相关
            { "javax/servlet/http/HttpServletRequest", "getParameter", "(Ljava/lang/String;)Ljava/lang/String;", 0 },
            { "javax/servlet/http/HttpServletRequest", "getQueryString", "()Ljava/lang/String;", 0 },
            { "javax/servlet/http/HttpServletRequest", "getParameterNames", "()Ljava/util/Enumeration;", 0 },
            { "javax/servlet/http/HttpServletRequest", "getParameterValues", "(Ljava/lang/String;)[Ljava/lang/String;", 0 },
            { "javax/servlet/http/HttpServletRequest", "getParameterMap", "()Ljava/util/Map;", 0 },

            // ServletRequest-URI相关
            { "javax/servlet/http/HttpServletRequest", "getRequestURI", "()Ljava/lang/String;", 0 },
            { "javax/servlet/http/HttpServletRequest", "getRequestURL", "()Ljava/lang/StringBuffer;", 0 },

            // ServletRequest-cookie相关
            { "javax/servlet/http/HttpServletRequest", "getCookies", "()[Ljavax/servlet/http/Cookie;", 0 },
            // 污点流入到Cookie对象后，再从Cookie对象的getName和getValue流出
            { "javax/servlet/http/Cookie", "getName", "()Ljava/lang/String;", 0 },
            { "javax/servlet/http/Cookie", "getValue", "()Ljava/lang/String;", 0 },
            { "org/springframework/web/util/WebUtils", "getCookie", "(Ljavax/servlet/http/HttpServletRequest;Ljava/lang/String;)Ljavax/servlet/http/Cookie;", 0 },

            // ServletRequest-header相关
            { "javax/servlet/http/HttpServletRequest", "getHeader", "(Ljava/lang/String;)Ljava/lang/String;", 0 },
            { "javax/servlet/http/HttpServletRequest", "getHeaderNames", "()Ljava/util/Enumeration;", 0 },
            { "javax/servlet/http/HttpServletRequest", "getHeaders", "(Ljava/lang/String;)Ljava/util/Enumeration;", 0 },

            // 有点类似集合类的处理，认为里面的所有元素都是污点
            { "java/util/Enumeration", "nextElement", "()Ljava/lang/Object;", 0 },

            // ServletRequest-文件上传相关
            { "javax/servlet/http/HttpServletRequest", "getPart", "(Ljava/lang/String;)Ljavax/servlet/http/Part;", 0 },
            { "javax/servlet/http/HttpServletRequest", "getParts", "()Ljava/util/Collection;", 0 },
            { "javax/servlet/http/HttpServletRequest", "getInputStream", "()Ljavax/servlet/ServletInputStream;", 0 },

            // ServletRequest-content-type相关
            { "javax/servlet/http/HttpServletRequest", "getContentType", "()Ljava/lang/String;", 0 },

            // ServletRequest-sessionId相关
            { "javax/servlet/http/HttpServletRequest", "getRequestedSessionId", "()Ljava/lang/String;", 0 },

            // ServletRequest-Path相关
            { "javax/servlet/http/HttpServletRequest", "getPathInfo", "()Ljava/lang/String;", 0 },
            { "javax/servlet/http/HttpServletRequest", "getPathTranslated", "()Ljava/lang/String;", 0 },
            //===========================================ServletRequest-end=====================================

            // java.lang.String.getBytes()
            { "java/lang/String", "getBytes", "()[B", 0 },

            // gadgetinspector默认查找的是反序列化的链，它认为每个方法的0参对象都是可以被控制的，但查找sql注入不一样，对于部分构造方法，需要自己明确哪个参数可以污染，要不然污点分析走不下去
            { "org/springframework/jdbc/core/JdbcTemplate$1QueryStatementCallback", "<init>", "(Lorg/springframework/jdbc/core/JdbcTemplate;Ljava/lang/String;Lorg/springframework/jdbc/core/ResultSetExtractor;)V", 2 },
    };

    /**
     * 维护本地变量表和操作数栈
     */
    private static class SavedVariableState<T> {
        /**
         * 本地变量表
         */
        List<Set<T>> localVars;

        /**
         * 操作数栈
         */
        List<Set<T>> stackVars;

        public SavedVariableState() {
            localVars = new ArrayList<>();
            stackVars = new ArrayList<>();
        }

        public SavedVariableState(SavedVariableState<T> copy) {
            this.localVars = new ArrayList<>(copy.localVars.size());
            this.stackVars = new ArrayList<>(copy.stackVars.size());

            for (Set<T> original : copy.localVars) {
                this.localVars.add(new HashSet<>(original));
            }
            for (Set<T> original : copy.stackVars) {
                this.stackVars.add(new HashSet<>(original));
            }
        }

        public void combine(SavedVariableState<T> copy) {
            for (int i = 0; i < copy.localVars.size(); i++) {
                while (i >= this.localVars.size()) {
                    this.localVars.add(new HashSet<T>());
                }
                this.localVars.get(i).addAll(copy.localVars.get(i));
            }
            for (int i = 0; i < copy.stackVars.size(); i++) {
                while (i >= this.stackVars.size()) {
                    this.stackVars.add(new HashSet<T>());
                }
                this.stackVars.get(i).addAll(copy.stackVars.get(i));
            }
        }
    }

    private final InheritanceMap inheritanceMap;
    private final Map<MethodReference.Handle, Set<Integer>> passthroughDataFlow;

    private final AnalyzerAdapter analyzerAdapter;
    private final int access;
    private final String name;
    private final String desc;
    private final String signature;
    private final String[] exceptions;

    public TaintTrackingMethodVisitor(InheritanceMap inheritanceMap,
                                      Map<MethodReference.Handle, Set<Integer>> passthroughDataFlow,
                                      final int api, final MethodVisitor mv, final String owner, int access,
                                      String name, String desc, String signature, String[] exceptions) {
        super(api, new AnalyzerAdapter(owner, access, name, desc, mv));
        this.inheritanceMap = inheritanceMap;
        this.passthroughDataFlow = passthroughDataFlow;
        this.analyzerAdapter = (AnalyzerAdapter)this.mv;
        this.access = access;
        this.name = name;
        this.desc = desc;
        this.signature = signature;
        this.exceptions = exceptions;
    }

    private SavedVariableState<T> savedVariableState = new SavedVariableState<T>();
    private Map<Label, SavedVariableState<T>> gotoStates = new HashMap<Label, SavedVariableState<T>>();
    private Set<Label> exceptionHandlerLabels = new HashSet<Label>();

    @Override
    public void visitCode() {
        super.visitCode();
        // 刚进入方法，需要清空本地变量表和操作数栈
        savedVariableState.localVars.clear();
        savedVariableState.stackVars.clear();

        // 如果方法是public、private、protected修饰的非static方法
        if ((this.access & Opcodes.ACC_STATIC) == 0) {
            // 非static方法，进入方法时，本地变量表首先添加的就是this，这里用一个空Set代表了
            savedVariableState.localVars.add(new HashSet<T>());
        }
        // 现在就是将形参入本地变量表
        for (Type argType : Type.getArgumentTypes(desc)) {
            for (int i = 0; i < argType.getSize(); i++) {
                savedVariableState.localVars.add(new HashSet<T>());
            }
        }
    }

    /**
     * 操作数栈添加元素
     * 如果形参为空，则push一个空set，也就是这个操作数不带污点
     */
    private void push(T ... possibleValues) {
        Set<T> vars = new HashSet<>();
        for (T s : possibleValues) {
            vars.add(s);
        }
        savedVariableState.stackVars.add(vars);
    }

    /**
     * 操作数栈添加元素
     */
    private void push(Set<T> possibleValues) {
        // Intentionally make this a reference to the same set
        savedVariableState.stackVars.add(possibleValues);
    }

    /**
     * 操作数栈删除栈顶元素
     */
    private Set<T> pop() {
        return savedVariableState.stackVars.remove(savedVariableState.stackVars.size()-1);
    }

    /**
     * 从栈顶开始，获取stackIndex位置的元素,并不会pop
     */
    private Set<T> get(int stackIndex) {
        return savedVariableState.stackVars.get(savedVariableState.stackVars.size()-1-stackIndex);
    }

    @Override
    public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
        // 遇到if else或者类似switch的语句时，很多变量或栈数据的作用域会失效，所以需要对前面栈frame的清除，接着扩展新栈frame
        if (type != Opcodes.F_NEW) {
            throw new IllegalStateException("Compressed frame encountered; class reader should use accept() with EXPANDED_FRAMES option.");
        }
        int stackSize = 0;
        for (int i = 0; i < nStack; i++) {
            Object typ = stack[i];
            int objectSize = 1;
            if (typ.equals(Opcodes.LONG) || typ.equals(Opcodes.DOUBLE)) {
                objectSize = 2;
            }
            for (int j = savedVariableState.stackVars.size(); j < stackSize+objectSize; j++) {
                savedVariableState.stackVars.add(new HashSet<T>());
            }
            stackSize += objectSize;
        }
        int localSize = 0;
        for (int i = 0; i < nLocal; i++) {
            Object typ = local[i];
            int objectSize = 1;
            if (typ.equals(Opcodes.LONG) || typ.equals(Opcodes.DOUBLE)) {
                objectSize = 2;
            }
            for (int j = savedVariableState.localVars.size(); j < localSize+objectSize; j++) {
                savedVariableState.localVars.add(new HashSet<T>());
            }
            localSize += objectSize;
        }
        for (int i = savedVariableState.stackVars.size() - stackSize; i > 0; i--) {
            savedVariableState.stackVars.remove(savedVariableState.stackVars.size()-1);
        }
        for (int i = savedVariableState.localVars.size() - localSize; i > 0; i--) {
            savedVariableState.localVars.remove(savedVariableState.localVars.size()-1);
        }

        super.visitFrame(type, nLocal, local, nStack, stack);

        sanityCheck();
    }

    /**
     * javadoc: <a href="https://asm.ow2.io/javadoc/org/objectweb/asm/MethodVisitor.html#visitInsn(int)">visitInsn(int)</a>
     * 总共107条无操作数指令
     */
    @Override
    public void visitInsn(int opcode) {
        Set<T> saved0, saved1, saved2, saved3;
        sanityCheck();
        switch(opcode) {
            case Opcodes.NOP:
                break;
            case Opcodes.ACONST_NULL:
            case Opcodes.ICONST_M1:
            case Opcodes.ICONST_0:
            case Opcodes.ICONST_1:
            case Opcodes.ICONST_2:
            case Opcodes.ICONST_3:
            case Opcodes.ICONST_4:
            case Opcodes.ICONST_5:
            case Opcodes.FCONST_0:
            case Opcodes.FCONST_1:
            case Opcodes.FCONST_2:
                push();
                break;
            case Opcodes.LCONST_0:
            case Opcodes.LCONST_1:
            case Opcodes.DCONST_0:
            case Opcodes.DCONST_1:
                push();
                push();
                break;
            case Opcodes.IALOAD:
            case Opcodes.FALOAD:
            case Opcodes.AALOAD:
            case Opcodes.BALOAD:
            case Opcodes.CALOAD:
            case Opcodes.SALOAD:
                pop();
                pop();
                push();
                break;
            case Opcodes.LALOAD:
            case Opcodes.DALOAD:
                pop();
                pop();
                push();
                push();
                break;
            case Opcodes.IASTORE:
            case Opcodes.FASTORE:
            case Opcodes.AASTORE:
            case Opcodes.BASTORE:
            case Opcodes.CASTORE:
            case Opcodes.SASTORE:
                pop();
                pop();
                pop();
                break;
            case Opcodes.LASTORE:
            case Opcodes.DASTORE:
                pop();
                pop();
                pop();
                pop();
                break;
            case Opcodes.POP:
                pop();
                break;
            case Opcodes.POP2:
                pop();
                pop();
                break;
            case Opcodes.DUP:
                push(get(0));
                break;
            case Opcodes.DUP_X1:
                saved0 = pop();
                saved1 = pop();
                push(saved0);
                push(saved1);
                push(saved0);
                break;
            case Opcodes.DUP_X2:
                saved0 = pop(); // a
                saved1 = pop(); // b
                saved2 = pop(); // c
                push(saved0); // a
                push(saved2); // c
                push(saved1); // b
                push(saved0); // a
                break;
            case Opcodes.DUP2:
                // a b
                push(get(1)); // a b a
                push(get(1)); // a b a b
                break;
            case Opcodes.DUP2_X1:
                // a b c
                saved0 = pop();
                saved1 = pop();
                saved2 = pop();
                push(saved1); // b
                push(saved0); // c
                push(saved2); // a
                push(saved1); // b
                push(saved0); // c
                break;
            case Opcodes.DUP2_X2:
                // a b c d
                saved0 = pop();
                saved1 = pop();
                saved2 = pop();
                saved3 = pop();
                push(saved1); // c
                push(saved0); // d
                push(saved3); // a
                push(saved2); // b
                push(saved1); // c
                push(saved0); // d
                break;
            case Opcodes.SWAP:
                saved0 = pop();
                saved1 = pop();
                push(saved0);
                push(saved1);
                break;
            case Opcodes.IADD:
            case Opcodes.FADD:
            case Opcodes.ISUB:
            case Opcodes.FSUB:
            case Opcodes.IMUL:
            case Opcodes.FMUL:
            case Opcodes.IDIV:
            case Opcodes.FDIV:
            case Opcodes.IREM:
            case Opcodes.FREM:
                pop();
                pop();
                push();
                break;
            case Opcodes.LADD:
            case Opcodes.DADD:
            case Opcodes.LSUB:
            case Opcodes.DSUB:
            case Opcodes.LMUL:
            case Opcodes.DMUL:
            case Opcodes.LDIV:
            case Opcodes.DDIV:
            case Opcodes.LREM:
            case Opcodes.DREM:
                pop();
                pop();
                pop();
                pop();
                push();
                push();
                break;
            case Opcodes.INEG:
            case Opcodes.FNEG:
                pop();
                push();
                break;
            case Opcodes.LNEG:
            case Opcodes.DNEG:
                pop();
                pop();
                push();
                push();
                break;
            case Opcodes.ISHL:
            case Opcodes.ISHR:
            case Opcodes.IUSHR:
                pop();
                pop();
                push();
                break;
            case Opcodes.LSHL:
            case Opcodes.LSHR:
            case Opcodes.LUSHR:
                pop();
                pop();
                pop();
                push();
                push();
                break;
            case Opcodes.IAND:
            case Opcodes.IOR:
            case Opcodes.IXOR:
                pop();
                pop();
                push();
                break;
            case Opcodes.LAND:
            case Opcodes.LOR:
            case Opcodes.LXOR:
                pop();
                pop();
                pop();
                pop();
                push();
                push();
                break;
            case Opcodes.I2B:
            case Opcodes.I2C:
            case Opcodes.I2S:
            case Opcodes.I2F:
                pop();
                push();
                break;
            case Opcodes.I2L:
            case Opcodes.I2D:
                pop();
                push();
                push();
                break;
            case Opcodes.L2I:
            case Opcodes.L2F:
                pop();
                pop();
                push();
                break;
            case Opcodes.D2L:
            case Opcodes.L2D:
                pop();
                pop();
                push();
                push();
                break;
            case Opcodes.F2I:
                pop();
                push();
                break;
            case Opcodes.F2L:
            case Opcodes.F2D:
                pop();
                push();
                push();
                break;
            case Opcodes.D2I:
            case Opcodes.D2F:
                pop();
                pop();
                push();
                break;
            case Opcodes.LCMP:
                pop();
                pop();
                pop();
                pop();
                push();
                break;
            case Opcodes.FCMPL:
            case Opcodes.FCMPG:
                pop();
                pop();
                push();
                break;
            case Opcodes.DCMPL:
            case Opcodes.DCMPG:
                pop();
                pop();
                pop();
                pop();
                push();
                break;
            case Opcodes.IRETURN:
            case Opcodes.FRETURN:
            case Opcodes.ARETURN:
                pop();
                break;
            case Opcodes.LRETURN:
            case Opcodes.DRETURN:
                pop();
                pop();
                break;
            case Opcodes.RETURN:
                break;
            case Opcodes.ARRAYLENGTH:
                pop();
                push();
                break;
            case Opcodes.ATHROW:
                pop();
                break;
            case Opcodes.MONITORENTER:
            case Opcodes.MONITOREXIT:
                pop();
                break;
            default:
                throw new IllegalStateException("Unsupported opcode: " + opcode);
        }

        super.visitInsn(opcode);

        sanityCheck();
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
        switch(opcode) {
            case Opcodes.BIPUSH:
            case Opcodes.SIPUSH:
                push();
                break;
            case Opcodes.NEWARRAY:
                pop();
                push();
                break;
            default:
                throw new IllegalStateException("Unsupported opcode: " + opcode);
        }

        super.visitIntInsn(opcode, operand);

        sanityCheck();
    }

    @Override
    public void visitVarInsn(int opcode, int var) {
        // Extend local variable state to make sure we include the variable index
        for (int i = savedVariableState.localVars.size(); i <= var; i++) {
            savedVariableState.localVars.add(new HashSet<T>());
        }

        // 变量操作，var为操作的本地变量索引
        Set<T> saved0;
        switch(opcode) {
            case Opcodes.ILOAD:
            case Opcodes.FLOAD:
                // INT和FLOAT无法传播污点，不需要处理污点传播
                push();
                break;
            case Opcodes.LLOAD:
            case Opcodes.DLOAD:
                // long和double也不能传播污点，不需要处理污点传播
                push();
                push();
                break;
            case Opcodes.ALOAD:
                // Load reference from local variable
                // 从本地变量表加载变量引用，此时可能导致污点传播
                push(savedVariableState.localVars.get(var));
                break;
            case Opcodes.ISTORE:
            case Opcodes.FSTORE:
                // INT和FLOAT无法传播污点，不需要处理污点传播
                pop();
                savedVariableState.localVars.set(var, new HashSet<T>());
                break;
            case Opcodes.DSTORE:
            case Opcodes.LSTORE:
                // long和double也不能传播污点，不需要处理污点传播
                pop();
                pop();
                savedVariableState.localVars.set(var, new HashSet<T>());
                break;
            case Opcodes.ASTORE:
                // 从栈中取出数据存到本地变量表，这个数据可能是被污染的（主要还是得看调用的方法，返回值是否可被污染）
                saved0 = pop();
                savedVariableState.localVars.set(var, saved0);
                break;
            case Opcodes.RET:
                // Return from subroutine
                // No effect on stack
                break;
            default:
                throw new IllegalStateException("Unsupported opcode: " + opcode);
        }

        super.visitVarInsn(opcode, var);

        sanityCheck();
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        switch(opcode) {
            case Opcodes.NEW:
                push();
                break;
            case Opcodes.ANEWARRAY:
                pop();
                push();
                break;
            case Opcodes.CHECKCAST:
                // No-op
                break;
            case Opcodes.INSTANCEOF:
                pop();
                push();
                break;
            default:
                throw new IllegalStateException("Unsupported opcode: " + opcode);
        }

        super.visitTypeInsn(opcode, type);

        sanityCheck();
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String desc) {
        int typeSize = Type.getType(desc).getSize();
        switch (opcode) {
            case Opcodes.GETSTATIC:
                for (int i = 0; i < typeSize; i++) {
                    push();
                }
                break;
            case Opcodes.PUTSTATIC:
                for (int i = 0; i < typeSize; i++) {
                    pop();
                }
                break;
            case Opcodes.GETFIELD:
                // pop objectref
                pop();
                for (int i = 0; i < typeSize; i++) {
                    // push object's fileref value
                    push();
                }
                break;
            case Opcodes.PUTFIELD:
                // pop objectref and value
                for (int i = 0; i < typeSize; i++) {
                    pop();
                }
                // this is objectref
                pop();
                break;
            default:
                throw new IllegalStateException("Unsupported opcode: " + opcode);
        }

        super.visitFieldInsn(opcode, owner, name, desc);

        sanityCheck();
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
        final MethodReference.Handle methodHandle = new MethodReference.Handle(new ClassReference.Handle(owner), name, desc);
        // 注意，这个是被调用方法的形参列表，外加一个 "被调用方法的实例对象的Type"，且位于第0个元素
        Type[] argTypes = Type.getArgumentTypes(desc);
        if (opcode != Opcodes.INVOKESTATIC) {
            Type[] extendedArgTypes = new Type[argTypes.length+1];
            System.arraycopy(argTypes, 0, extendedArgTypes, 1, argTypes.length);
            // argType[0] 为被调用方法的实例对象的Type
            extendedArgTypes[0] = Type.getObjectType(owner);
            argTypes = extendedArgTypes;
        }

        final Type returnType = Type.getReturnType(desc);
        final int retSize = returnType.getSize();

        switch (opcode) {
            // java的几种函数调用
            case Opcodes.INVOKESTATIC:
            case Opcodes.INVOKEVIRTUAL:
            case Opcodes.INVOKESPECIAL:
            case Opcodes.INVOKEINTERFACE:
                final List<Set<T>> argTaint = new ArrayList<Set<T>>(argTypes.length);
                for (int i = 0; i < argTypes.length; i++) {
                    argTaint.add(null);
                }

                for (int i = 0; i < argTypes.length; i++) {
                    Type argType = argTypes[i];
                    if (argType.getSize() > 0) {
                        for (int j = 0; j < argType.getSize() - 1; j++) {
                            // 这里才是真正地出栈
                            pop();
                        }
                        // long和double的参数长度为两个slot，所以污点放在第一个slot中，所以前面需要根据参数的长度先pop掉一个
                        // 这里pop出来的数据才可能存在污点
                        argTaint.set(argTypes.length - 1 - i, pop());
                    }
                }
                Set<T> resultTaint;
                if (name.equals("<init>")) {
                    // Pass result taint through to original taint set; the initialized object is directly tainted by
                    // parameters
                    resultTaint = argTaint.get(0);
                } else {
                    resultTaint = new HashSet<>();
                }

                // If calling defaultReadObject on a tainted ObjectInputStream, that taint passes to "this"
                if (owner.equals("java/io/ObjectInputStream") && name.equals("defaultReadObject") && desc.equals("()V")) {
                    savedVariableState.localVars.get(0).addAll(argTaint.get(0));
                }

                // 污染例外关联，不通过参数关联污点
                // 在名单内的方法的调用，已预置哪个参数可以污染返回值
                // 污染名单，固定哪个参数可以污染下去
                for (Object[] passthrough : PASSTHROUGH_DATAFLOW) {
                    if (passthrough[0].equals(owner) && passthrough[1].equals(name) && passthrough[2].equals(desc)) {
                        // 从index=3开始，后面定义的数字都是代表taintArg
                        for (int i = 3; i < passthrough.length; i++) {
                            resultTaint.addAll(argTaint.get((Integer)passthrough[i]));
                        }
                    }
                }

                // 前面已做逆拓扑，调用链最末端最先被visit，因此，调用到的方法必然已被visit分析过
                // 通过PassthroughDiscovery发现的参数和返回值污染
                if (passthroughDataFlow != null) {
                    Set<Integer> passthroughArgs = passthroughDataFlow.get(methodHandle);
                    if (passthroughArgs != null) {
                        for (int arg : passthroughArgs) {
                            resultTaint.addAll(argTaint.get(arg));
                        }
                    }
                }

                // Heuristic; if the object implements java.util.Collection or java.util.Map, assume any method accepting an object
                // taints the collection. Assume that any method returning an object returns the taint of the collection.
                if (opcode != Opcodes.INVOKESTATIC && argTypes[0].getSort() == Type.OBJECT) {
                    // 获取被调用函数的所有父类
                    Set<ClassReference.Handle> parents = inheritanceMap.getSuperClasses(new ClassReference.Handle(argTypes[0].getClassName().replace('.', '/')));
                    if (parents != null && (parents.contains(new ClassReference.Handle("java/util/Collection")) ||
                            parents.contains(new ClassReference.Handle("java/util/Map")))) {
                        // 如果该类为集合类，callee的所有形参都是污点，把污点标记加入到this中
                        for (int i = 1; i < argTaint.size(); i++) {
                            argTaint.get(0).addAll(argTaint.get(i));
                        }

                        if (returnType.getSort() == Type.OBJECT || returnType.getSort() == Type.ARRAY) {
                            resultTaint.addAll(argTaint.get(0));
                        }
                    }
                }
                if (retSize > 0) {
                    // 传播污点，污点放在retSize对应的第0个push
                    push(resultTaint);
                    for (int i = 1; i < retSize; i++) {
                        push();
                    }
                }
                break;
            default:
                throw new IllegalStateException("Unsupported opcode: " + opcode);
        }

        super.visitMethodInsn(opcode, owner, name, desc, itf);

        sanityCheck();
    }

    @Override
    public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
        int argsSize = 0;
        for (Type type : Type.getArgumentTypes(desc)) {
            argsSize += type.getSize();
        }
        int retSize = Type.getReturnType(desc).getSize();

        for (int i = 0; i < argsSize; i++) {
            pop();
        }
        for (int i = 0; i < retSize; i++) {
            push();
        }

        super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);

        sanityCheck();
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
        switch (opcode) {
            case Opcodes.IFEQ:
            case Opcodes.IFNE:
            case Opcodes.IFLT:
            case Opcodes.IFGE:
            case Opcodes.IFGT:
            case Opcodes.IFLE:
            case Opcodes.IFNULL:
            case Opcodes.IFNONNULL:
                pop();
                break;
            case Opcodes.IF_ICMPEQ:
            case Opcodes.IF_ICMPNE:
            case Opcodes.IF_ICMPLT:
            case Opcodes.IF_ICMPGE:
            case Opcodes.IF_ICMPGT:
            case Opcodes.IF_ICMPLE:
            case Opcodes.IF_ACMPEQ:
            case Opcodes.IF_ACMPNE:
                pop();
                pop();
                break;
            case Opcodes.GOTO:
                break;
            case Opcodes.JSR:
                push();
                super.visitJumpInsn(opcode, label);
                return;
            default:
                throw new IllegalStateException("Unsupported opcode: " + opcode);
        }

        mergeGotoState(label, savedVariableState);

        super.visitJumpInsn(opcode, label);

        sanityCheck();
    }

    @Override
    public void visitLabel(Label label) {
        if (gotoStates.containsKey(label)) {
            savedVariableState = new SavedVariableState(gotoStates.get(label));
        }
        if (exceptionHandlerLabels.contains(label)) {
            // Add the exception to the stack
            push(new HashSet<T>());
        }
        super.visitLabel(label);
        sanityCheck();
    }

    @Override
    public void visitLdcInsn(Object cst) {
        if (cst instanceof Long || cst instanceof Double) {
            push();
            push();
        } else {
            push();
        }

        super.visitLdcInsn(cst);

        sanityCheck();
    }

    @Override
    public void visitIincInsn(int var, int increment) {
        // No effect on stack
        super.visitIincInsn(var, increment);

        sanityCheck();
    }

    @Override
    public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
        // Operand stack has a switch index which gets popped
        pop();

        // Save the current state with any possible target labels
        mergeGotoState(dflt, savedVariableState);
        for (Label label : labels) {
            mergeGotoState(label, savedVariableState);
        }

        super.visitTableSwitchInsn(min, max, dflt, labels);

        sanityCheck();
    }

    @Override
    public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
        // Operand stack has a lookup index which gets popped
        pop();

        // Save the current state with any possible target labels
        mergeGotoState(dflt, savedVariableState);
        for (Label label : labels) {
            mergeGotoState(label, savedVariableState);
        }
        super.visitLookupSwitchInsn(dflt, keys, labels);

        sanityCheck();
    }

    @Override
    public void visitMultiANewArrayInsn(String desc, int dims) {
        for (int i = 0; i < dims; i++) {
            pop();
        }
        push();

        super.visitMultiANewArrayInsn(desc, dims);

        sanityCheck();
    }

    @Override
    public AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
        return super.visitInsnAnnotation(typeRef, typePath, desc, visible);
    }

    @Override
    public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
        exceptionHandlerLabels.add(handler);
        super.visitTryCatchBlock(start, end, handler, type);
    }

    @Override
    public AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
        return super.visitTryCatchAnnotation(typeRef, typePath, desc, visible);
    }

    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
        super.visitMaxs(maxStack, maxLocals);
    }

    @Override
    public void visitEnd() {
        super.visitEnd();
    }

    private void mergeGotoState(Label label, SavedVariableState savedVariableState) {
        if (gotoStates.containsKey(label)) {
            SavedVariableState combinedState = new SavedVariableState(gotoStates.get(label));
            // 如果是类似循环，又回到了之前的Lable，则需要更新本地变量表和操作数栈
            combinedState.combine(savedVariableState);
            gotoStates.put(label, combinedState);
        } else {
            // 在跳转到另外一个Lable前，先缓存当前状态的本地变量表和操作数栈
            gotoStates.put(label, new SavedVariableState(savedVariableState));
        }
    }

    private void sanityCheck() {
        if (analyzerAdapter.stack != null && savedVariableState.stackVars.size() != analyzerAdapter.stack.size()) {
            throw new IllegalStateException("Bad stack size.");
        }
    }

    protected Set<T> getStackTaint(int index) {
        // 获取栈顶元素，这个操作并不会导致栈元素pop，仅获取元素
        return savedVariableState.stackVars.get(savedVariableState.stackVars.size()-1-index);
    }

    protected void setStackTaint(int index, T ... possibleValues) {
        Set<T> values = new HashSet<T>();
        for (T value : possibleValues) {
            values.add(value);
        }
        // 对操作数栈的第n个元素进行数据填充
        savedVariableState.stackVars.set(savedVariableState.stackVars.size()-1-index, values);
    }

    protected void setStackTaint(int index, Collection<T> possibleValues) {
        Set<T> values = new HashSet<T>();
        values.addAll(possibleValues);
        savedVariableState.stackVars.set(savedVariableState.stackVars.size()-1-index, values);
    }

    protected Set<T> getLocalTaint(int index) {
        return savedVariableState.localVars.get(index);
    }

    protected void setLocalTaint(int index, T ... possibleValues) {
        Set<T> values = new HashSet<T>();
        for (T value : possibleValues) {
            values.add(value);
        }
        // 对本地变量表的第n个元素进行数据填充
        savedVariableState.localVars.set(index, values);
    }

    protected void setLocalTaint(int index, Collection<T> possibleValues) {
        Set<T> values = new HashSet<T>();
        values.addAll(possibleValues);
        savedVariableState.localVars.set(index, values);
    }

    protected static final boolean couldBeSerialized(SerializableDecider serializableDecider, InheritanceMap inheritanceMap, ClassReference.Handle clazz) {
        if (Boolean.TRUE.equals(serializableDecider.apply(clazz))) {
            return true;
        }
        //获取clazz的所有子类
        Set<ClassReference.Handle> subClasses = inheritanceMap.getSubClasses(clazz);
        if (subClasses != null) {
            //遍历clazz所有子类是否存在可被序列化的class
            for (ClassReference.Handle subClass : subClasses) {
                //使用各类型的serializableDecider中的apply方法判断class是否可序列化
                if (Boolean.TRUE.equals(serializableDecider.apply(subClass))) {
                    return true;
                }
            }
        }
        return false;
    }


}
