package fun.pinger.model;

/**
 * @author P1n93r
 */
public class MethodReference {
    private final ClassReference.Handle classReference;
    private final String name;
    private final String desc;
    private final boolean isStatic;

    /**
     * 方法注解
     */
    private final String methodAnnotation;

    /**
     * 方法形参注解
     */
    private final String paramAnnotation;


    public MethodReference(ClassReference.Handle classReference, String name, String desc,boolean isStatic,String methodAnnotation, String paramAnnotation) {
        this.classReference = classReference;
        this.name = name;
        this.desc = desc;
        this.isStatic = isStatic;
        // 新增两个属性：方法注解，形参注解
        this.methodAnnotation = methodAnnotation;
        this.paramAnnotation = paramAnnotation;
    }

    public ClassReference.Handle getClassReference() {
        return classReference;
    }

    public String getName() {
        return name;
    }

    public String getDesc() {
        return desc;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public String getMethodAnnotation() {
        return methodAnnotation;
    }

    public String getParamAnnotation() {
        return paramAnnotation;
    }

    public Handle getHandle() {
        return new Handle(classReference, name, desc);
    }

    public static class Handle {
        private final ClassReference.Handle classReference;
        private final String name;
        private final String desc;

        public Handle(ClassReference.Handle classReference, String name, String desc) {
            this.classReference = classReference;
            this.name = name;
            this.desc = desc;
        }

        public ClassReference.Handle getClassReference() {
            return classReference;
        }

        public String getName() {
            return name;
        }

        public String getDesc() {
            return desc;
        }

        /**
         * 方法名和方法形参可以唯一标识一个方法
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Handle handle = (Handle) o;

            if (classReference != null ? !classReference.equals(handle.classReference) : handle.classReference != null) {
                return false;
            }
            if (name != null ? !name.equals(handle.name) : handle.name != null) {
                return false;
            }
            return desc != null ? desc.equals(handle.desc) : handle.desc == null;
        }

        @Override
        public int hashCode() {
            int result = classReference != null ? classReference.hashCode() : 0;
            result = 31 * result + (name != null ? name.hashCode() : 0);
            result = 31 * result + (desc != null ? desc.hashCode() : 0);
            return result;
        }
    }

    public static class Factory implements DataFactory<MethodReference> {

        @Override
        public MethodReference parse(String[] fields) {
            return new MethodReference(
                    new ClassReference.Handle(fields[0]),
                    fields[1],
                    fields[2],
                    Boolean.parseBoolean(fields[3]),
                    fields[4],
                    fields[5]
            );
        }

        @Override
        public String[] serialize(MethodReference obj) {
            return new String[]{
                    obj.classReference.getName(),
                    obj.name,
                    obj.desc,
                    Boolean.toString(obj.isStatic),
                    obj.methodAnnotation,
                    obj.paramAnnotation,
            };
        }
    }
}
