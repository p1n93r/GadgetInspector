package fun.pinger.core;

import fun.pinger.discovery.SourceDiscovery;
import fun.pinger.model.ClassReference;
import fun.pinger.model.InheritanceMap;
import fun.pinger.model.MethodReference;
import java.util.Map;
import java.util.Set;

/**
 * @author : P1n93r
 * @date : 2022/4/8 12:19
 */
public interface ScanTypeConfig {

    /**
     * @return 扫描类型
     * 每种扫描类型都有对应的sourceDiscovery
     */
    String getName();

    SerializableDecider getSerializableDecider(Map<MethodReference.Handle, MethodReference> methodMap, InheritanceMap inheritanceMap);

    ImplementationFinder getImplementationFinder(
        Map<MethodReference.Handle, MethodReference> methodMap,
        Map<MethodReference.Handle, Set<MethodReference.Handle>> methodImplMap,
        InheritanceMap inheritanceMap,
        Map<ClassReference.Handle, Set<MethodReference.Handle>> methodsByClass
    );

    SourceDiscovery getSourceDiscovery();

    String toString();

}
