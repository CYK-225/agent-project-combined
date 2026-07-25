package org.example.graph.createGraph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 节点动作抽象基类。
 * 子类只需实现 execute() 方法，返回 Map 即可，无需处理 CompletableFuture。
 *
 * <p>用法：
 * <pre>
 * public class AnalyzeNode extends SimpleNodeAction {
 *     protected Map&lt;String, Object&gt; execute(OverAllState state) throws Exception {
 *         return Map.of("result", "分析: " + state.value("input").orElse(""));
 *     }
 * }
 *
 * builder.addNode("analyze", new AnalyzeNode());
 * </pre>
 *
 * <p>也可以用 lambda 直接替代（适用于简单逻辑）：
 * <pre>
 * builder.addNode("analyze", state -> CompletableFuture.completedFuture(
 *     Map.of("result", "分析: " + state.value("input").orElse(""))));
 * </pre>
 */
public abstract class SimpleNodeAction implements AsyncNodeAction {

    @Override
    public final CompletableFuture<Map<String, Object>> apply(OverAllState state) {
        try {
            return CompletableFuture.completedFuture(execute(state));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * 执行节点逻辑。
     *
     * @param state 当前图状态
     * @return 需要更新的状态键值对
     * @throws Exception 业务异常
     */
    protected abstract Map<String, Object> execute(OverAllState state) throws Exception;
}
