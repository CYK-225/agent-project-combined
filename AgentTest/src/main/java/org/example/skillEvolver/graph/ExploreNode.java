package org.example.skillEvolver.graph;

import com.alibaba.cloud.ai.graph.OverAllState;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import lombok.extern.slf4j.Slf4j;
import org.example.agentScope.framework.core.AgentPoolManager;
import org.example.graph.createGraph.node.SimpleNodeAction;
import org.example.graph.workflow.annotation.NodeAction;
import org.example.skillEvolver.agent.TrialTestAgent;
import org.example.skillEvolver.config.ApplicationContextProvider;
import org.example.skillEvolver.hook.TrialTraceHook;

import java.util.*;

/**
 * 单次 Trial 执行节点。
 * <p>
 * 通过 AgentPoolManager.getAgentWithSession 获取独立的 TrialTestAgent，
 * 不同 threadId 自动创建不同会话，FanOut 并行时互不干扰。
 * <p>
 * 执行流程：
 * <ol>
 *   <li>创建 TrialTraceHook（每个 variant 独立）</li>
 *   <li>设置 TrialContext ThreadLocal</li>
 *   <li>通过 AgentPoolManager 获取 TrialTestAgent（挂载 TrialTraceHook）</li>
 *   <li>调用 agent.call(prompt) 执行 trial</li>
 *   <li>从 TrialTraceHook 提取 trace</li>
 * </ol>
 *
 * @author zhilin
 */
@Slf4j
@NodeAction(value = "evolver-explore-1", description = "Trial 执行节点 - 变体 1")
public class ExploreNode extends SimpleNodeAction {

    /** 当前节点对应的变体序号（从 1 开始），子类可覆盖 */
    protected int getVariantIndex() {
        return 1;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        int variantIndex = getVariantIndex();
        String taskId = (String) state.value("taskId").orElse("");
        String instruction = (String) state.value("taskInstruction").orElse("");
        String taskData = (String) state.value("taskData").orElse("");
        String verifier = (String) state.value("verifier").orElse("");
        int iteration = (int) state.value("currentIteration").orElse(0);

        // 取出对应的策略变体
        List<Map<String, Object>> variants =
                (List<Map<String, Object>>) state.value("strategyVariants").orElse(List.of());

        Map<String, Object> myVariant = null;
        for (Map<String, Object> v : variants) {
            if (Objects.equals(v.get("variantIndex"), variantIndex)) {
                myVariant = v;
                break;
            }
        }

        if (myVariant == null) {
            log.warn("[ExploreNode-{}] 未找到策略变体 {}, fallback 到空策略", variantIndex, variantIndex);
            myVariant = Map.of("variantIndex", variantIndex, "strategyHint", "（未指定策略）", "skillMarkdown", "");
        }

        String strategyHint = (String) myVariant.getOrDefault("strategyHint", "");
        String skillMarkdown = (String) myVariant.getOrDefault("skillMarkdown", "");

        log.info("[ExploreNode-{}] 开始执行 trial, taskId={}, iteration={}, strategyHint={}",
                variantIndex, taskId, iteration, strategyHint);

        // 构建隔离工作区
        String workDir = System.getProperty("java.io.tmpdir")
                + "/evolver-trial/" + taskId + "/variant-" + variantIndex;

        // ======================== 通过 AgentPoolManager 获取独立 TrialTestAgent ========================

        // 1. 创建独立的 TrialTraceHook（每个 variant 一份）
        TrialTraceHook traceHook = new TrialTraceHook();

        // 2. 设置 TrialContext ThreadLocal（TrialTestAgent 的 setupSysPrompt/setupSkills 会读取）
        TrialTestAgent.TrialContext ctx = new TrialTestAgent.TrialContext(
                taskId, iteration, variantIndex, skillMarkdown, workDir, verifier);
        TrialTestAgent.setContext(ctx);
        // 同时注册到 ConcurrentHashMap，防止虚拟线程中 ThreadLocal 丢失
        TrialTestAgent.registerVerifier(workDir, verifier);

        Map<String, Object> result;
        try {
            // 3. 通过 AgentPoolManager 获取 TrialTestAgent，不同 threadId 自动隔离会话
            String threadId = "evolver-trial-" + taskId + "-iter" + iteration + "-v" + variantIndex;
            AgentPoolManager poolManager = ApplicationContextProvider.getBean(AgentPoolManager.class);

            List<Hook> dynamicHooks = new ArrayList<>();
            dynamicHooks.add(traceHook);

            ReActAgent testAgent = poolManager.getAgentWithSession(
                    "TrialTestAgent", threadId, null, dynamicHooks);

            // 4. 构建 trial prompt 并执行
            String trialPrompt = buildTrialPrompt(taskId, iteration, variantIndex,
                    workDir, strategyHint, skillMarkdown, instruction, taskData);

            log.info("[ExploreNode-{}] 调用 testAgent, threadId={}, prompt 长度={}",
                    variantIndex, threadId, trialPrompt.length());

            Msg userMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .content(List.of(TextBlock.builder().text(trialPrompt).build()))
                    .build();

            // 超时保护：5 分钟（URL 等复杂任务可能需要较长时间）
            Msg response;
            try {
                response = testAgent.call(userMsg)
                        .timeout(java.time.Duration.ofMinutes(5))
                        .block();
            } catch (Exception timeoutEx) {
                log.warn("[ExploreNode-{}] trial 超时 (5min), taskId={}", variantIndex, taskId);
                response = Msg.builder().role(MsgRole.ASSISTANT)
                        .content(List.of(TextBlock.builder()
                                .text("TRIAL TIMEOUT: Agent exceeded 5-minute limit.")
                                .build())).build();
            }
            String agentReply = (response != null) ? response.getTextContent() : "(no response)";

            // 5. 从 Hook 提取完整 trace
            String traceMarkdown = traceHook.toMarkdown();
            int traceSize = traceHook.getTraceSize();

            log.info("[ExploreNode-{}] trial 完成, taskId={}, reply 长度={}, trace 条目数={}",
                    variantIndex, taskId, agentReply.length(), traceSize);

            // 6. 查询验证结果（由 TraceCollector 回调注册）
            Map<String, Object> verifyResult = TrialTestAgent.consumeVerifyResult(workDir);
            String status = "COMPLETED";
            Double reward = null;
            String verifyMessage = null;
            if (verifyResult != null) {
                boolean passed = Boolean.TRUE.equals(verifyResult.get("passed"));
                reward = ((Number) verifyResult.getOrDefault("score", 0)).doubleValue();
                verifyMessage = (String) verifyResult.get("message");
                if (passed) {
                    status = "PASSED";
                }
                log.info("[ExploreNode-{}] 验证结果: passed={}, score={}, status={}",
                        variantIndex, passed, reward, status);
            } else {
                log.info("[ExploreNode-{}] 未捕获到验证结果（agent 可能未调用 verify_trial_result）", variantIndex);
            }

            result = buildTrialResult(variantIndex, workDir, strategyHint,
                    skillMarkdown, taskId, instruction, status,
                    agentReply, traceMarkdown, traceSize, reward, verifyMessage);

        } catch (Exception e) {
            log.error("[ExploreNode-{}] trial 执行失败, taskId={}", variantIndex, taskId, e);
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            result = buildTrialResult(variantIndex, workDir, strategyHint,
                    skillMarkdown, taskId, instruction, "FAILED",
                    "ERROR: " + e.getMessage() + "\n\nSTACK:\n" + sw,
                    "_Error: " + e.getMessage() + "_", 0, null, null);
        } finally {
            // 7. 清理 ThreadLocal + 缓存（释放内存，下一轮迭代会重新注册）
            TrialTestAgent.clearContext();
            TrialTestAgent.cleanupVerifier(workDir);
            TrialTestAgent.cleanupVerifyResult(workDir);
        }

        String resultKey = "trialResult_" + variantIndex;
        return Map.of(
                resultKey, result,
                "trialWorkDir_" + variantIndex, workDir
        );
    }

    /**
     * 构建 trial 执行提示词
     */
    private String buildTrialPrompt(String taskId, int iteration, int variantIndex,
                                     String workDir, String strategyHint,
                                     String skillMarkdown, String instruction,
                                     String taskData) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Trial 任务\n\n");
        sb.append("- **Task ID**: ").append(taskId).append("\n");
        sb.append("- **迭代**: ").append(iteration).append("\n");
        sb.append("- **变体**: ").append(variantIndex).append("\n");
        sb.append("- **工作区**: ").append(workDir).append("\n");
        sb.append("- **策略提示**: ").append(strategyHint).append("\n\n");

        if (skillMarkdown != null && !skillMarkdown.isBlank()) {
            sb.append("## 当前 Skill 指导\n\n").append(skillMarkdown).append("\n\n");
        }

        sb.append("## 任务指令\n\n").append(instruction).append("\n\n");

        if (taskData != null && !taskData.isBlank()) {
            sb.append("## 任务数据\n\n```\n").append(taskData).append("\n```\n\n");
        }

        sb.append("请按照以下步骤执行：\n");
        sb.append("1. 调用 `list_trial_files` 查看工作区\n");
        sb.append("2. 调用 `write_trial_file` 写入所需文件\n");
        sb.append("3. 调用 `execute_trial_task` 执行任务\n");
        sb.append("4. 调用 `verify_trial_result` 验证结果\n");
        sb.append("5. 简要汇报结果");

        return sb.toString();
    }

    private Map<String, Object> buildTrialResult(int variantIndex, String workDir,
                                                  String strategyHint, String skillMarkdown,
                                                  String taskId, String instruction,
                                                  String status, String agentResponse,
                                                  String traceMarkdown, int traceSize,
                                                  Double reward, String verifyMessage) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("variantIndex", variantIndex);
        r.put("workDir", workDir);
        r.put("strategyHint", strategyHint);
        r.put("skillMarkdown", skillMarkdown);
        r.put("taskId", taskId);
        r.put("instruction", instruction);
        r.put("status", status);
        r.put("agentResponse", agentResponse);
        r.put("traceMarkdown", traceMarkdown);
        r.put("traceSize", traceSize);
        if (reward != null) r.put("reward", reward);
        if (verifyMessage != null) r.put("verifyMessage", verifyMessage);
        return r;
    }
}
