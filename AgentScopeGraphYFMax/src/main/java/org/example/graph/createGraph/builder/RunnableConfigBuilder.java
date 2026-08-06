package org.example.graph.createGraph.builder;

import com.alibaba.cloud.ai.graph.RunnableConfig;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * RunnableConfig.Builder 的流式包装器。
 *
 * <p>上游真实 API（1.1.2.2）：
 * <pre>
 *   Builder threadId(String)
 *   Builder checkPointId(String)
 *   Builder addMetadata(String, Object)    // 逐条添加
 *   Builder defaultParallelExecutor(Executor) // 全局默认并行执行器
 *   Builder addParallelNodeExecutor(String node, Executor) // 按节点指定
 *   RunnableConfig build()
 * </pre>
 */
public class RunnableConfigBuilder {

    private String threadId;
    private String checkPointId;
    private Map<String, Object> metadata;
    private Executor parallelNodeExecutor;

    public static RunnableConfigBuilder create() {
        return new RunnableConfigBuilder();
    }

    public RunnableConfigBuilder threadId(String id) {
        this.threadId = id;
        return this;
    }

    public RunnableConfigBuilder checkPointId(String id) {
        this.checkPointId = id;
        return this;
    }

    public RunnableConfigBuilder metadata(Map<String, Object> meta) {
        this.metadata = meta;
        return this;
    }

    public RunnableConfigBuilder addParallelNodeExecutor(Executor executor) {
        this.parallelNodeExecutor = executor;
        return this;
    }

    public RunnableConfig build() {
        RunnableConfig.Builder rb = RunnableConfig.builder();
        if (threadId != null) rb.threadId(threadId);
        if (checkPointId != null) rb.checkPointId(checkPointId);
        if (metadata != null) {
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                rb.addMetadata(entry.getKey(), entry.getValue());
            }
        }
        if (parallelNodeExecutor != null) {
            rb.defaultParallelExecutor(parallelNodeExecutor);
        }
        return rb.build();
    }
}
