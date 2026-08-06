package org.example.skillEvolver.graph;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import lombok.extern.slf4j.Slf4j;
import org.example.agentScope.framework.core.AgentPoolManager;
import org.example.graph.createGraph.node.SimpleNodeAction;
import org.example.graph.workflow.annotation.NodeAction;
import org.example.skillEvolver.agent.EvolverReasonerAgent;
import org.example.skillEvolver.config.ApplicationContextProvider;
import org.example.skillEvolver.service.EvolverTaskService;

import java.util.*;

/**
 * LLM 验证规则自动生成节点。
 * <p>
 * 在 strategize 之后、dispatch-trials 之前执行。
 * 当 state["verifier"] 为空时，通过 LLM 根据 taskInstruction 自动生成 verifier JSON。
 * 当用户已手动传入 verifier 时，跳过生成（用户覆盖优先级最高）。
 * <p>
 * 生成的 verifier 写回 state，并在数据库中持久化以便审计。
 *
 * @author zhilin
 */
@Slf4j
@NodeAction(value = "evolver-generate-verifier", description = "LLM 验证规则自动生成：基于任务指令生成 verifier JSON")
public class GenerateVerifierNode extends SimpleNodeAction {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Set<String> KNOWN_KEYS = Set.of(
            "exitCodeMustBe", "fileExists", "fileContains",
            "stdoutContains", "stdoutMatches", "command",
            "containsColumn", "targetFile"
    );

    private static final String SYSTEM_PROMPT = """
            你是 SkillEvolver 的验证规则生成器。你的任务是分析一个任务指令，自动生成验证规则 JSON，
            用于自动检查 Agent 是否正确完成了任务。

            ## 可用规则类型（所有字段可选，只包含你有把握的规则）

            1. **exitCodeMustBe** (integer): 期望的程序退出码。默认为 0。
               例: `"exitCodeMustBe": 0`

            2. **fileExists** (string[]): 任务完成后必须存在的文件列表（相对路径）。
               例: `"fileExists": ["calc.py", "test_calc.py"]`

            3. **fileContains** ({file: string, text: string}[]): 检查指定文件必须包含的文本。
               例: `"fileContains": [{"file": "calc.py", "text": "def add"}]`

            4. **stdoutContains** (string[]): 程序 stdout 中必须包含的关键词。
               例: `"stdoutContains": ["passed", "OK"]`

            5. **stdoutMatches** (string): 应用于完整 stdout 的正则表达式（Java 正则，全匹配，带 (?s) DOTALL 标志）。
               若要部分匹配，请用 `.*keyword.*` 形式。
               例: `"stdoutMatches": ".*\\\\d+ passed.*"`

            6. **command** (string): 在工作区执行的额外验证命令。exit code 为 0 表示通过。
               命令在 Windows 上通过 cmd /c 执行。仅用于轻量级检查（如语法验证、导入检查）。
               例: `"command": "python -c \\"import ast; ast.parse(open('calc.py').read())\\""`

            7. **containsColumn** (string[]) + **targetFile** (string): 检查输出文件中是否包含指定列名/字段名。
               targetFile 默认为 "output.json"。
               例: `"containsColumn": ["columns", "means"], "targetFile": "result.json"`

            ## 约束
            - 只包含你能从任务描述中**高置信度推断**的规则
            - 不确定时宁可不加规则，也不要加入可能误判的规则
            - fileExists 路径是相对于任务工作区的
            - command 规则要确保在 Windows 上可以运行
            - stdoutMatches 是**全匹配**（不是部分匹配），要加 `.*...*` 包裹
            - 输出**仅** JSON 对象，不要 markdown 围栏，不要解释文字
            """;

    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String existingVerifier = (String) state.value("verifier").orElse("");

        // 若已有 verifier（用户手动传入 或 前一轮已生成），跳过
        if (existingVerifier != null && !existingVerifier.isBlank()) {
            log.info("[GenerateVerifierNode] verifier 已存在（长度={}），跳过自动生成",
                    existingVerifier.length());
            return Map.of();
        }

        String taskId = (String) state.value("taskId").orElse("unknown");
        String instruction = (String) state.value("taskInstruction").orElse("");
        String taskData = (String) state.value("taskData").orElse("");

        log.info("[GenerateVerifierNode] 开始自动生成 verifier, taskId={}, instruction 长度={}",
                taskId, instruction.length());

        String taskPrompt = buildVerifierPrompt(instruction, taskData);

        // 调用 LLM（遵循项目标准 7 步模式）
        AgentPoolManager poolManager = ApplicationContextProvider.getBean(AgentPoolManager.class);
        EvolverReasonerAgent.setContext(
                new EvolverReasonerAgent.ReasonerContext(SYSTEM_PROMPT, taskPrompt));

        try {
            String threadId = "evolver-verifier-gen-" + taskId + "-" + System.currentTimeMillis();
            ReActAgent reasoner = poolManager.getAgentWithSession(
                    "EvolverReasoner", threadId, null, List.of());

            Msg userMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .content(List.of(TextBlock.builder().text(taskPrompt).build()))
                    .build();

            Msg response = reasoner.call(userMsg).block();
            String llmResponse = (response != null) ? response.getTextContent() : "";

            log.info("[GenerateVerifierNode] LLM 响应长度={}", llmResponse.length());

            // 提取并校验 JSON
            String verifierJson = extractAndValidateVerifier(llmResponse);

            log.info("[GenerateVerifierNode] 生成成功, verifier={}",
                    verifierJson.length() > 200
                            ? verifierJson.substring(0, 200) + "..."
                            : verifierJson);

            // 回写到数据库（审计用）
            try {
                EvolverTaskService taskService = ApplicationContextProvider.getBean(EvolverTaskService.class);
                taskService.updateVerifier(taskId, verifierJson);
            } catch (Exception e) {
                log.warn("[GenerateVerifierNode] 更新数据库 verifier 失败（不影响运行）: {}", e.getMessage());
            }

            return Map.of("verifier", verifierJson);

        } catch (Exception e) {
            log.error("[GenerateVerifierNode] LLM 调用失败, 使用 fallback: {}", e.getMessage());
            String fallback = "{\"exitCodeMustBe\":0}";
            return Map.of("verifier", fallback);
        } finally {
            EvolverReasonerAgent.clearContext();
        }
    }

    private String buildVerifierPrompt(String instruction, String taskData) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 任务指令\n\n").append(instruction).append("\n\n");
        if (taskData != null && !taskData.isBlank()) {
            sb.append("## 任务数据\n\n```\n").append(taskData).append("\n```\n\n");
        }
        sb.append("## 要求\n\n");
        sb.append("基于以上任务，生成验证规则 JSON。请从以下维度分析：\n");
        sb.append("1. 任务会创建哪些文件？→ fileExists\n");
        sb.append("2. 关键文件应包含什么内容？→ fileContains\n");
        sb.append("3. 成功执行时 stdout 会出现什么关键词？→ stdoutContains\n");
        sb.append("4. 期望的退出码？→ exitCodeMustBe\n");
        sb.append("5. 是否可以用轻量命令做额外验证？→ command\n");
        sb.append("6. 输出文件中需要包含哪些字段？→ containsColumn + targetFile\n\n");
        sb.append("只包含你有把握的规则，不确定的不要加。\n");
        sb.append("严格输出 JSON 对象。\n");
        return sb.toString();
    }

    private String extractAndValidateVerifier(String llmResponse) {
        String fallback = "{\"exitCodeMustBe\":0}";

        if (llmResponse == null || llmResponse.isBlank()) {
            log.warn("[GenerateVerifierNode] LLM 响应为空, 使用 fallback");
            return fallback;
        }

        // 提取 JSON 对象
        String json = extractJsonObject(llmResponse);
        if (json == null) {
            log.warn("[GenerateVerifierNode] 无法从 LLM 响应中提取 JSON, 使用 fallback");
            return fallback;
        }

        // 修复常见的 LLM 输出问题
        json = json.replaceAll(",\\s*([}\\]])", "$1");           // 尾部逗号
        json = json.replaceAll("(?<=\"[^\"\\\\]*)\\n", " ");     // 字符串中的裸换行

        try {
            JsonNode node = MAPPER.readTree(json);
            if (!node.isObject()) {
                log.warn("[GenerateVerifierNode] LLM 输出不是 JSON 对象, 使用 fallback");
                return fallback;
            }

            ObjectNode obj = (ObjectNode) node;

            // 移除未知 key（防止 LLM 幻觉）
            List<String> unknownKeys = new ArrayList<>();
            obj.fieldNames().forEachRemaining(key -> {
                if (!KNOWN_KEYS.contains(key)) {
                    unknownKeys.add(key);
                }
            });
            unknownKeys.forEach(obj::remove);
            if (!unknownKeys.isEmpty()) {
                log.debug("[GenerateVerifierNode] 移除未知 key: {}", unknownKeys);
            }

            // 检查是否至少有一个已知 key
            boolean hasAnyKnownKey = false;
            for (String known : KNOWN_KEYS) {
                if (obj.has(known)) {
                    hasAnyKnownKey = true;
                    break;
                }
            }
            if (!hasAnyKnownKey) {
                log.warn("[GenerateVerifierNode] 生成的 JSON 不包含任何已知规则 key, 使用 fallback");
                return fallback;
            }

            return MAPPER.writeValueAsString(obj);

        } catch (Exception e) {
            log.warn("[GenerateVerifierNode] JSON 解析失败: {}, 使用 fallback", e.getMessage());
            return fallback;
        }
    }

    private String extractJsonObject(String text) {
        if (text == null || text.isBlank()) return null;
        String cleaned = text.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return cleaned.substring(start, end + 1);
        }
        return null;
    }
}
