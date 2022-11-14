package fun.pinger.source.struts;

import fun.pinger.core.ImplementationFinder;
import fun.pinger.core.ScanTypeConfig;
import fun.pinger.core.SerializableDecider;
import fun.pinger.discovery.SourceDiscovery;
import fun.pinger.model.ClassReference;
import fun.pinger.model.InheritanceMap;
import fun.pinger.model.MethodReference;
import fun.pinger.source.base.AllPassSerializableDecider;
import fun.pinger.source.base.SimpleImplementationFinder;

import java.util.Map;
import java.util.Set;

/**
 * @author : P1n93r
 * @date : 2022/4/21 10:09
 */
public class StrutsSourceConfig implements ScanTypeConfig {

    @Override
    public String getName() {
        return "struts";
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public SerializableDecider getSerializableDecider(Map<MethodReference.Handle, MethodReference> methodMap, InheritanceMap inheritanceMap) {
        return new AllPassSerializableDecider(inheritanceMap);
    }

    @Override
    public ImplementationFinder getImplementationFinder(
            Map<MethodReference.Handle, MethodReference> methodMap,
            Map<MethodReference.Handle, Set<MethodReference.Handle>> methodImplMap,
            InheritanceMap inheritanceMap,
            Map<ClassReference.Handle, Set<MethodReference.Handle>> methodsByClass) {
        return new SimpleImplementationFinder(getSerializableDecider(methodMap, inheritanceMap), methodImplMap);
    }

    @Override
    public SourceDiscovery getSourceDiscovery() {
        return new StrutsSourceDiscovery();
    }

}