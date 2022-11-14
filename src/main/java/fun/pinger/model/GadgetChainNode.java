package fun.pinger.model;

import lombok.Getter;
import lombok.Setter;

/**
 * @author : P1n93r
 * @date : 2022/4/8 18:22
 */
@Getter
@Setter
public class GadgetChainNode {

    private final MethodReference.Handle method;
    private final int taintedArgIndex;

    public GadgetChainNode(MethodReference.Handle method, int taintedArgIndex) {
        this.method = method;
        this.taintedArgIndex = taintedArgIndex;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        GadgetChainNode that = (GadgetChainNode) o;

        if (taintedArgIndex != that.taintedArgIndex) {
            return false;
        }
        return method != null ? method.equals(that.method) : that.method == null;
    }

    @Override
    public int hashCode() {
        int result = method != null ? method.hashCode() : 0;
        result = 31 * result + taintedArgIndex;
        return result;
    }
}
