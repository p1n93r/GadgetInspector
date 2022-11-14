package fun.pinger.discovery;

import fun.pinger.model.*;
import lombok.Getter;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;


/**
 * @author P1n93r
 * 污染源发现，将发现的污染源存储在 discoveredSources 中
 * 这是一个抽象类，需要根据实际场景实现自己的SourceDescovery
 */
public abstract class SourceDiscovery {

    @Getter
    private final List<Source> discoveredSources = new ArrayList<>();

    protected final void addDiscoveredSource(Source source) {
        discoveredSources.add(source);
    }

    public void discover() throws IOException {
        Map<ClassReference.Handle, ClassReference> classMap = DataLoader.loadClasses();
        Map<MethodReference.Handle, MethodReference> methodMap = DataLoader.loadMethods();
        InheritanceMap inheritanceMap = InheritanceMap.load();

        // caller的所有相关的call-site
        Map<MethodReference.Handle, Set<CallGraph>> graphCallMap = new HashMap<>();

        for (CallGraph graphCall : DataLoader.loadData(Paths.get("callgraph.dat"), new CallGraph.Factory())) {
            MethodReference.Handle caller = graphCall.getCallerMethod();
            if (!graphCallMap.containsKey(caller)) {
                Set<CallGraph> graphCalls = new HashSet<>();
                graphCalls.add(graphCall);
                graphCallMap.put(caller, graphCalls);
            } else {
                graphCallMap.get(caller).add(graphCall);
            }
        }

        discover(classMap, methodMap, inheritanceMap, graphCallMap);
    }

    public abstract void discover(Map<ClassReference.Handle, ClassReference> classMap,
                                  Map<MethodReference.Handle, MethodReference> methodMap,
                                  InheritanceMap inheritanceMap, Map<MethodReference.Handle, Set<CallGraph>> graphCallMap);

    public void save() throws IOException {
        DataLoader.saveData(Paths.get("sources.dat"), new Source.Factory(), discoveredSources);
    }
}
