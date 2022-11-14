package fun.pinger.source.struts;

import fun.pinger.model.*;
import fun.pinger.source.servlet.HttpServletRequestSourceDiscovery;
import java.util.Map;
import java.util.Set;

/**
 * @author : P1n93r
 * @date : 2022/4/21 10:11
 */
public class StrutsSourceDiscovery extends HttpServletRequestSourceDiscovery {


    /**
     * Action的父类可能很多，各种自定义的Action基类，所以最好还是不要限定基类了
     * 无脑根据方法的形参列表判定为source吧
     */
    @Override
    public void discover(Map<ClassReference.Handle, ClassReference> classMap, Map<MethodReference.Handle, MethodReference> methodMap, InheritanceMap inheritanceMap, Map<MethodReference.Handle, Set<CallGraph>> callGraphMap) {
        methodMap.values().forEach(item -> {
            Set<CallGraph> callGraphs = callGraphMap.get(item.getHandle());
            if (callGraphs == null) {
                return;
            }
            for (CallGraph call : callGraphs) {
                // 获取方法的形参列表
                String desc = item.getDesc();
                String actionDesc = "(Lorg/apache/struts/action/ActionMapping;Lorg/apache/struts/action/ActionForm;Ljavax/servlet/http/HttpServletRequest;Ljavax/servlet/http/HttpServletResponse;)";
                if(desc.startsWith(actionDesc)){
                    addDiscoveredSource(new Source(item.getHandle(), call.getCallerArgIndex()));
                }
            }
        });
        super.discover(classMap, methodMap, inheritanceMap, callGraphMap);
    }
}