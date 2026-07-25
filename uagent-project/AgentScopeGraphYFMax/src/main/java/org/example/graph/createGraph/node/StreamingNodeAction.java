package org.example.graph.createGraph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 流式节点动作抽象基类。
 * 子类实现 {@code executeStreaming()} 返回 {@code Flux<StreamingOutput>}，
 * 框架自动处理中间片段收集和最终结果汇总。
 *
 * <p>与 {@code SimpleNodeAction} 的区别：
 * <ul>
 *   <li>{@code SimpleNodeAction} — 返回最终结果 Map（同步）</li>
 *   <li>{@code StreamingNodeAction} — 返回流式片段 Flux + 最终结果（异步流式）</li>
 * </ul>
 *
 * <p>用法：
 * <pre>
 * public class LlmStreamingNode extends StreamingNodeAction {
 *     protected Flux&lt;StreamingOutput&gt; executeStreaming(OverAllState state) {
 *         String prompt = (String) state.value("input").orElse("");
 *         return callLlmStream(prompt)
 *             .map(chunk -> new StreamingOutput(chunk, chunk, nodeId(), state,
 *                   OutputType.AGENT_MODEL_STREAMING));
 *     }
 *     protected Map&lt;String, Object&gt; execute(OverAllState state) {
 *         return Map.of("result", getCollectedResult());
 *     }
 * }
 *
 * builder.addNode("llm", new LlmStreamingNode());
 * </pre>
 *
 * <p>执行方式：
 * <pre>
 * // 同步执行 — 只获取最终结果
 * OverAllState result = engine.invoke(graph, state, "thread-1", null);
 *
 * // 流式执行 — 获取中间片段
 * Flux&lt;NodeOutput&gt; stream = engine.stream(graph, state, "thread-1");
 * stream.filter(o -> o instanceof StreamingOutput)
 *       .cast(StreamingOutput.class)
 *       .subscribe(o -> System.out.println(o.node() + ": " + o.chunk()));
 * </pre>
 */
public abstract class StreamingNodeAction extends SimpleNodeAction {

    /** 已收集的文本片段（供 execute() 汇总使用） */
    private volatile String collectedResult = "";

    @Override
    protected final Map<String, Object> execute(OverAllState state) throws Exception {
        // 收集所有流式片段
        StringBuilder sb = new StringBuilder();
        executeStreaming(state)
                .doOnNext(output -> {
                    if (output.chunk() != null) {
                        sb.append(output.chunk());
                    }
                })
                .blockLast();  // 阻塞等待所有片段完成

        collectedResult = sb.toString();
        return buildResult(state, collectedResult);
    }

    /**
     * 执行流式逻辑，返回 StreamingOutput 片段流。
     *
     * <p>子类必须实现此方法，产生流式输出。每个 StreamingOutput 包含：
     * <ul>
     *   <li>{@code chunk} — 当前文本片段</li>
     *   <li>{@code nodeId} — 节点标识（用 {@code nodeId()} 获取）</li>
     *   <li>{@code outputType} — 输出类型（用 {@code streamingType()} 获取）</li>
     * </ul>
     *
     * @param state 当前图状态
     * @return 流式输出片段
     */
    protected abstract Flux<StreamingOutput> executeStreaming(OverAllState state);

    /**
     * 从收集的片段构建最终结果 Map。
     * 默认返回 {@code Map.of("result", collectedResult)}。
     * 子类可重写以自定义结果结构。
     */
    protected Map<String, Object> buildResult(OverAllState state, String collected) {
        return Map.of("result", collected);
    }

    /**
     * 获取已收集的文本片段（在 execute() 完成后可用）。
     */
    protected String getCollectedResult() {
        return collectedResult;
    }

    /**
     * 获取节点标识（子类可重写）。
     * 用于 StreamingOutput 的 nodeId 字段。
     */
    protected String nodeId() {
        return getClass().getSimpleName();
    }

    /**
     * 获取默认输出类型（子类可重写）。
     */
    protected OutputType streamingType() {
        return OutputType.GRAPH_NODE_STREAMING;
    }

    /**
     * 创建流式输出片段的便捷方法。
     */
    protected StreamingOutput createChunk(String chunk, OverAllState state) {
        return new StreamingOutput(chunk, chunk, nodeId(), state, streamingType());
    }

    /**
     * 创建流式输出片段（带原始数据）。
     */
    protected <T> StreamingOutput createChunk(T originData, String chunk, OverAllState state) {
        return new StreamingOutput(originData, chunk, nodeId(), state, streamingType());
    }
}
