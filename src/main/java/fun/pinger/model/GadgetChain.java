package fun.pinger.model;


import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * @author : P1n93r
 * @date : 2022/4/8 18:21
 */
@Getter
@Setter
public class GadgetChain {

    private final List<GadgetChainNode> links;

    public GadgetChain(List<GadgetChainNode> links) {
        this.links = links;
    }

    /**
     * 在原来的GadgetChain的基础上，再增加一个节点形成新的GadgetChain
     */
    public GadgetChain(GadgetChain gadgetChain, GadgetChainNode node) {
        List<GadgetChainNode> links = new ArrayList<>(gadgetChain.links);
        links.add(node);
        this.links = links;
    }
}