package org.example.graph.createGraph.edge;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;

import java.util.concurrent.CompletableFuture;

/**
 * 边动作抽象基类。
 * 子类只需实现 execute() 方法，返回目标节点名称即可，无需处理 CompletableFuture。
 *
 * <p>用法：
 * <pre>
 * public class TypeRouter extends SimpleEdgeAction {
 *     protected String execute(OverAllState state) throws Exception {
 *         return "question".equals(state.value("type").orElse(""))
 *             ? "answerNode" : "fallbackNode";
 *     }
 * }
 *
 * builder.addConditionalEdges("router", new TypeRouter())
 *        .route("answerNode", "answerNode")
 *        .route("fallbackNode", "fallbackNode")
 *        .done();
 * </pre>
 */
public abstract class SimpleEdgeAction implements AsyncEdgeAction {

    @Override
    public final CompletableFuture<String> apply(OverAllState state) {
        try {
            return CompletableFuture.completedFuture(execute(state));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * 执行路由逻辑。
     *
     * @param state 当前图状态
     * @return 目标节点名称
     * @throws Exception 业务异常
     */
    protected abstract String execute(OverAllState state) throws Exception;
}
