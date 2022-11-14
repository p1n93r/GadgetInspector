package fun.pinger.source.servlet;

import fun.pinger.discovery.SourceDiscovery;
import fun.pinger.model.*;

import java.util.Map;
import java.util.Set;

/**
 * @author : P1n93r
 * @date : 2022/4/11 10:08
 * TODO: 后续还要把J2ee的Filter、Interceptor也加上
 */
public class HttpServletRequestSourceDiscovery extends SourceDiscovery {

    @Override
    public void discover(Map<ClassReference.Handle, ClassReference> classMap,
                         Map<MethodReference.Handle, MethodReference> methodMap,
                         InheritanceMap inheritanceMap, Map<MethodReference.Handle, Set<CallGraph>> callGraphMap) {

        for (MethodReference.Handle method : methodMap.keySet()) {
            // method/callerMethod ---> HttpServlet#doPost(request,response)
            Set<CallGraph> callGraphs = callGraphMap.get(method);
            if (callGraphs == null) {
                continue;
            }
            for (CallGraph callGraph : callGraphs) {
                if (
                        (
                            // 取参数相关方法
                            callGraph.getTargetMethod().getName().equals("getQueryString") ||
                            callGraph.getTargetMethod().getName().equals("getParameter") ||
                            callGraph.getTargetMethod().getName().equals("getParameterNames") ||
                            callGraph.getTargetMethod().getName().equals("getParameterValues") ||
                            callGraph.getTargetMethod().getName().equals("getParameterMap") ||
                            // URI相关
                            callGraph.getTargetMethod().getName().equals("getRequestURI") ||
                            callGraph.getTargetMethod().getName().equals("getRequestURL") ||
                            // cookie相关
                            callGraph.getTargetMethod().getName().equals("getCookies") ||
                            // header相关
                            callGraph.getTargetMethod().getName().equals("getHeader") ||
                            callGraph.getTargetMethod().getName().equals("getHeaderNames") ||
                            callGraph.getTargetMethod().getName().equals("getHeaders") ||
                            // 文件上传相关
                            callGraph.getTargetMethod().getName().equals("getPart") ||
                            callGraph.getTargetMethod().getName().equals("getParts") ||
                            callGraph.getTargetMethod().getName().equals("getInputStream") ||
                            // content-type相关
                            callGraph.getTargetMethod().getName().equals("getContentType") ||
                            // sessionId相关
                            callGraph.getTargetMethod().getName().equals("getRequestedSessionId") ||
                            // Path相关
                            callGraph.getTargetMethod().getName().equals("getPathInfo") ||
                            callGraph.getTargetMethod().getName().equals("getPathTranslated")
                        )
                        &&
                        (
                            // targetMethod（callee）需要限定为 ServletRequest 的方法
                            inheritanceMap.isSubclassOf(callGraph.getTargetMethod().getClassReference(),new ClassReference.Handle("javax/servlet/ServletRequest"))
                        )
                        &&
                        (
                            // 对callerMethod进行限定,callerMethod需要限定为HttpServlet/GenericServlet类的
                            inheritanceMap.isSubclassOf(callGraph.getCallerMethod().getClassReference(),new ClassReference.Handle("javax/servlet/http/HttpServlet"))||
                            inheritanceMap.isSubclassOf(callGraph.getCallerMethod().getClassReference(),new ClassReference.Handle("javax/servlet/GenericServlet"))
                        )
                        &&
                        (
                            // 限定callerMethod的方法名为doGet/doPost/service
                            ((method.getName().equals("doGet")||method.getName().equals("doPost"))&&(method.getDesc().equals("(Ljavax/servlet/http/HttpServletRequest;Ljavax/servlet/http/HttpServletResponse;)V"))) ||
                            (method.getName().equals("service")&&(method.getDesc().equals("(Ljavax/servlet/ServletRequest;Ljavax/servlet/ServletResponse;)V")))
                        )
                ) {
                    addDiscoveredSource(new Source(method, callGraph.getCallerArgIndex()));
                }
            }
        }
    }

}