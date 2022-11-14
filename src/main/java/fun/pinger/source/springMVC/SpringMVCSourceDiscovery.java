package fun.pinger.source.springMVC;

import fun.pinger.model.*;
import fun.pinger.source.servlet.HttpServletRequestSourceDiscovery;

import java.util.*;

/**
 * @author : P1n93r
 * @date : 2022/4/12 14:46
 */
public class SpringMVCSourceDiscovery extends HttpServletRequestSourceDiscovery {

    @Override
    public void discover(Map<ClassReference.Handle, ClassReference> classMap, Map<MethodReference.Handle, MethodReference> methodMap, InheritanceMap inheritanceMap, Map<MethodReference.Handle, Set<CallGraph>> callGraphMap) {
        methodMap.values().forEach(item -> {
            ClassReference classReference = classMap.get(item.getClassReference());
            Set<CallGraph> callGraphs = callGraphMap.get(item.getHandle());
            if (callGraphs == null) {
                return;
            }
            for (CallGraph call : callGraphs) {
                if (
                    // 类限定
                    classReference != null && (classReference.getAnnotations()
                    .contains("Lorg/springframework/web/bind/annotation/RestController;") || classReference
                    .getAnnotations().contains("Lorg/springframework/stereotype/Controller;"))
                ) {
                    // 现在能保证是Controller类内的方法了，继续寻找Controller路由方法
                    String methodAnnotation = item.getMethodAnnotation();
                    // 保证存在SpringMVC的Controller相关的注解
                    List<String> annotations = Arrays.asList(
                            "Lorg/springframework/web/bind/annotation/RequestMapping;",
                            "Lorg/springframework/web/bind/annotation/GetMapping;",
                            "Lorg/springframework/web/bind/annotation/PostMapping;",
                            // 不太常用的，但是也是攻击面
                            "Lorg/springframework/web/bind/annotation/DeleteMapping;",
                            "Lorg/springframework/web/bind/annotation/PatchMapping;",
                            "Lorg/springframework/web/bind/annotation/PutMapping;"
                    );
                    annotations.forEach(anno->{
                        if(methodAnnotation.contains(anno)){
                            // controller方法的每个argIndex都被添加到了source
                            addDiscoveredSource(new Source(item.getHandle(), call.getCallerArgIndex()));
                        }
                    });
                }
            }
        });
        super.discover(classMap, methodMap, inheritanceMap, callGraphMap);
    }
}
