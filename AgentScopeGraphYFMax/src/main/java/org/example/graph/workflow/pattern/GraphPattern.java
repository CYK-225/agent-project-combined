package org.example.graph.workflow.pattern;

import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import org.example.graph.createGraph.builder.GraphBuilder;

/**
 * 可复用图拓扑的策略接口。
 * 类似于 AgentScopeYFMax MsgHub 系统中的 HubPattern。
 */
public interface GraphPattern {

    /** 模式名称，用于标识。 */
    String getName();

    /** 人类可读的描述。 */
    String getDescription();

    /**
     * 将此模式应用到 GraphBuilder，返回入口和终端节点信息。
     *
     * <p>注意：apply 不再自动连接到 END，调用方需通过返回的
     * {@link PatternResult} 显式决定终端节点的去向：
     * <pre>
     * PatternResult r = pattern.apply(builder);
     * r.connectToEnd(builder);     // 简单场景：全部到 END
     * r.connectTo("next", builder); // 组合场景：接到下一个模式
     * </pre>
     */
    PatternResult apply(GraphBuilder builder) throws GraphStateException;
}
