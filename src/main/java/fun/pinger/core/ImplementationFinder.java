package fun.pinger.core;

import fun.pinger.model.MethodReference;
import java.util.Set;

/**
 * @author P1n93r
 */
public interface ImplementationFinder {

    /**
     * 用于查找目标方法的实现方法
     */
    Set<MethodReference.Handle> getImplementations(MethodReference.Handle target);

}
