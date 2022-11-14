package fun.pinger.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author : P1n93r
 * @date : 2022/4/8 17:03
 * 自定义sink
 */
@Data
@NoArgsConstructor
public class CustomSink {
    private String className;
    private String method;
    private String desc;

    /**
     * 污点参数
     * 如果是-1，则代表无视污点参数，只要调用到了sink方法则认为存在调用链
     */
    private int taintArg;

    /**
     * 是否匹配子类重写方法
     */
    private boolean matchSubClassMethod;

    /**
     * 注释
     */
    private String note;

    /**
     * sink的类型
     */
    private String type;
}