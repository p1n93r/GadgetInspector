package fun.pinger.source.base;


import fun.pinger.core.ImplementationFinder;
import fun.pinger.core.SerializableDecider;
import fun.pinger.model.MethodReference;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;



public class SimpleImplementationFinder implements ImplementationFinder {
    private final SerializableDecider serializableDecider;
    private final Map<MethodReference.Handle, Set<MethodReference.Handle>> methodImplMap;

    public SimpleImplementationFinder(SerializableDecider serializableDecider, Map<MethodReference.Handle, Set<MethodReference.Handle>> methodImplMap) {
        this.serializableDecider = serializableDecider;
        this.methodImplMap = methodImplMap;
    }

    @Override
    public Set<MethodReference.Handle> getImplementations(MethodReference.Handle target) {
        Set<MethodReference.Handle> allImpls = new HashSet<>();

        // Assume that the target method is always available, even if not serializable; the target may just be a local instance rather than something an attacker can control.
        allImpls.add(target);

        Set<MethodReference.Handle> subClassImpls = methodImplMap.get(target);
        if (subClassImpls != null) {
            for (MethodReference.Handle subClassImpl : subClassImpls) {
                // 方法所在类是否可以被序列化，非jserial扫描类型使用的是AllPassSerializableDecider，永远返回TRUE
                if (Boolean.TRUE.equals(serializableDecider.apply(subClassImpl.getClassReference()))) {
                    allImpls.add(subClassImpl);
                }
            }
        }

        return allImpls;
    }
}
