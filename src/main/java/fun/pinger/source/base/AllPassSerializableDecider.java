package fun.pinger.source.base;

import fun.pinger.core.SerializableDecider;
import fun.pinger.model.ClassReference;
import fun.pinger.model.InheritanceMap;

/**
 * @author : P1n93r
 * @date : 2022/4/11 14:23
 */
public class AllPassSerializableDecider implements SerializableDecider {
    private final InheritanceMap inheritanceMap;

    public AllPassSerializableDecider(InheritanceMap inheritanceMap) {
        this.inheritanceMap = inheritanceMap;
    }

    /**
     * 用于判断class是否可以被序列化
     */
    @Override
    public Boolean apply(ClassReference.Handle handle) {
        // 因为是非反序列化漏洞，所有跟能否反序列化没关系,全部通过
        return Boolean.TRUE;
    }

}