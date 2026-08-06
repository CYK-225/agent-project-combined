package org.example.skillEvolver.agent;

import io.agentscope.core.model.Model;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.SkillBox;
import lombok.extern.log4j.Log4j2;
import org.example.agentScope.framework.annotation.AgentDefinition;
import org.example.agentScope.framework.core.AbstractAgentTemplate;
import org.example.agentScope.framework.core.AgentComponentFacade;
import org.example.agentScope.mas.classSkillManager.ClasspathSkillManager;
import org.example.agentScope.util.skill.BaseSkillBoxFactory;
import org.example.skillEvolver.config.ApplicationContextProvider;
import org.example.skillEvolver.tools.TrialTools;

import java.util.List;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * Trial 实际执行 Agent — 加载试炼工具，在隔离工作区中执行任务。
 * <p>
 * <b>上下文传递：</b>通过 {@link TrialContext} ThreadLocal 传递，
 * 避免 AgentPoolManager 返回单例模板时的线程安全问题。
 * <p>
 * <b>调用链：</b>ExploreNode → AgentPoolManager.getAgentWithSession("TrialTestAgent")
 * → buildAgentWithSession → setupSysPrompt/setupSkills → ReActAgent.call()
 * <p>
 * scope=prototype：每次 trial 获取新实例，保证隔离。
 *
 * @author zhilin
 */
@Log4j2
@AgentDefinition(
        name = "TrialTestAgent",
        scope = "prototype",
        enableMemory = false,
        enablePersistence = false,
        maxIters = 40,
        description = "Trial 实际执行 Agent — 加载试炼工具，执行隔离任务"
)
public class TrialTestAgent extends AbstractAgentTemplate {

    // ======================== ThreadLocal 上下文传递 ========================

    /**
     * Trial 上下文 — 通过 ThreadLocal 从 ExploreNode 传递到 TrialTestAgent。
     * <p>
     * 使用流程：
     * <ol>
     *   <li>ExploreNode 调用 {@code TrialContext.set(ctx)} 设置上下文</li>
     *   <li>AgentPoolManager 内部调用 buildAgent → setupSysPrompt/setupSkills 读取上下文</li>
     *   <li>ExploreNode 调用 {@code TrialContext.clear()} 清理（避免内存泄漏）</li>
     * </ol>
     */
    public static final class TrialContext {
        private final String strategySkillContent;
        private final String workspaceDir;
        private final String evolverTaskId;
        private final int iteration;
        private final int variantIndex;
        private final String verifier; // JSON 验证规则，从 SubmitRequest 传入

        public TrialContext(String evolverTaskId, int iteration, int variantIndex,
                           String strategySkillContent, String workspaceDir, String verifier) {
            this.evolverTaskId = evolverTaskId;
            this.iteration = iteration;
            this.variantIndex = variantIndex;
            this.strategySkillContent = strategySkillContent;
            this.workspaceDir = workspaceDir;
            this.verifier = verifier;
        }

        /** 兼容旧构造（无 verifier） */
        public TrialContext(String evolverTaskId, int iteration, int variantIndex,
                           String strategySkillContent, String workspaceDir) {
            this(evolverTaskId, iteration, variantIndex, strategySkillContent, workspaceDir, null);
        }

        public String getStrategySkillContent() { return strategySkillContent; }
        public String getWorkspaceDir() { return workspaceDir; }
        public String getEvolverTaskId() { return evolverTaskId; }
        public int getIteration() { return iteration; }
        public int getVariantIndex() { return variantIndex; }
        public String getVerifier() { return verifier; }
    }

    private static final ThreadLocal<TrialContext> CONTEXT = new ThreadLocal<>();

    // ======================== 跨线程 Verifier 传递 ========================
    // ThreadLocal 在 agent 框架的虚拟线程中会丢失，用 ConcurrentHashMap 按 workDir 存储 verifier
    private static final java.util.concurrent.ConcurrentHashMap<String, String> VERIFIER_MAP =
            new java.util.concurrent.ConcurrentHashMap<>();

    // ======================== 跨线程验证结果传递 ========================
    // verify_trial_result 的 passed/score 通过此 Map 传回 ExploreNode
    private static final java.util.concurrent.ConcurrentHashMap<String, String> VERIFY_RESULT_MAP =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final com.fasterxml.jackson.databind.ObjectMapper SHARED_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /** 规范化路径为 key：统一用正斜杠，去除尾部斜杠，避免 Windows 下分隔符不一致 */
    private static String normalizePathKey(String path) {
        if (path == null) return "";
        return path.replace('\\', '/').replaceAll("/+$", "");
    }

    /** 注册 verifier 规则（在 ExploreNode 线程调用） */
    public static void registerVerifier(String workDir, String verifierJson) {
        if (verifierJson != null && !verifierJson.isBlank()) {
            VERIFIER_MAP.put(normalizePathKey(workDir), verifierJson);
        }
    }

    /** 获取 verifier 规则（在 agent 工具线程调用）— 不立即删除，下一轮迭代 registerVerifier 会覆盖 */
    public static String consumeVerifier(String workDir) {
        return VERIFIER_MAP.get(normalizePathKey(workDir));
    }

    /** 清理 verifier 规则（在 ExploreNode finally 中调用，释放内存） */
    public static void cleanupVerifier(String workDir) {
        VERIFIER_MAP.remove(normalizePathKey(workDir));
    }

    /** 注册验证结果（在 TraceCollector 回调中调用，跨线程安全） */
    public static void registerVerifyResult(String workDir, boolean passed, double score, String message) {
        try {
            java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("passed", passed);
            result.put("score", score);
            result.put("message", message != null ? message : "");
            VERIFY_RESULT_MAP.put(normalizePathKey(workDir), SHARED_MAPPER.writeValueAsString(result));
        } catch (Exception e) {
            log.warn("[TrialTestAgent] registerVerifyResult 序列化失败", e);
        }
    }

    /** 获取验证结果（在 ExploreNode 中调用） */
    public static java.util.Map<String, Object> consumeVerifyResult(String workDir) {
        String json = VERIFY_RESULT_MAP.get(normalizePathKey(workDir));
        if (json == null) return null;
        try {
            return SHARED_MAPPER.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
        } catch (Exception e) { return null; }
    }

    /** 清理验证结果（在 ExploreNode finally 中调用） */
    public static void cleanupVerifyResult(String workDir) {
        VERIFY_RESULT_MAP.remove(normalizePathKey(workDir));
    }

    public static void setContext(TrialContext ctx) { CONTEXT.set(ctx); }
    public static TrialContext getContext() { return CONTEXT.get(); }
    public static void clearContext() { CONTEXT.remove(); }

    // ======================== 构造器 ========================

    public TrialTestAgent(AgentComponentFacade components) {
        super(components);
    }

    // ======================== AbstractAgentTemplate 实现 ========================

    @Override
    protected String setupSysPrompt() {
        TrialContext ctx = getContext();
        String skillSection = "";
        String workspace = "(未指定工作区)";
        String evolverTaskId = "unknown";
        int iteration = 0;
        int variantIndex = 0;

        if (ctx != null) {
            if (ctx.getStrategySkillContent() != null && !ctx.getStrategySkillContent().isBlank()) {
                skillSection = "## 你的 Skill 指导\n\n" + ctx.getStrategySkillContent() + "\n\n";
            }
            workspace = ctx.getWorkspaceDir() != null ? ctx.getWorkspaceDir() : workspace;
            evolverTaskId = ctx.getEvolverTaskId() != null ? ctx.getEvolverTaskId() : evolverTaskId;
            iteration = ctx.getIteration();
            variantIndex = ctx.getVariantIndex();
        }

        if (skillSection.isBlank()) {
            skillSection = "## 你的 Skill 指导\n\n（无 — 这是第一轮探索，自由发挥）\n\n";
        }

        return """
                你是 SkillEvolver 的 Trial 执行者 — 在隔离工作区中执行任务。

                ## 任务上下文
                - **进化任务 ID**: %s
                - **迭代**: %d
                - **策略变体**: %d
                - **工作区**: %s

                %s

                ## 执行流程
                1. 先调用 list_trial_files 查看工作区现有文件
                2. 如果需要脚本，调用 write_trial_file 写入
                3. 调用 execute_trial_task 执行任务
                4. 调用 verify_trial_result 验证结果
                5. 简要汇报：passed? score? 关键步骤?

                ## 关键约束
                - 只在工作区内操作，不触碰外部路径
                - 严格遵循 Skill 指导（如果有）
                - 失败的 trace 同样有价值
                """.formatted(evolverTaskId, iteration, variantIndex, workspace, skillSection);
    }

    @Override
    protected Model setupCustomModel() {
        return components.model().dashScope().buildDashScopeModel();
    }

    /**
     * 注册 TrialTools — 使用 ClasspathSkillManager 加载 skill-evolver-trial，
     * 把 TrialTools 注册到该 Skill 下，同时保留 SKILL.md/references/scripts 文件资源。
     */
    @Override
    protected SkillBox setupSkills() {
        // 自包含工具 — 无外部依赖注入
        TrialTools.CommandRunner commandRunner = this::runCommand;
        TrialTools.Verifier verifier = this::verifyResult;
        TrialTools.TraceCollector traceCollector = (taskId, iteration, variantIndex, passed, reward, traceSummary) -> {
            TrialContext ctx = TrialTestAgent.getContext();
            String wd = (ctx != null) ? ctx.getWorkspaceDir() : null;
            if (wd != null) {
                TrialTestAgent.registerVerifyResult(wd, passed, reward, traceSummary);
            }
            log.debug("[TrialTestAgent] TraceCollector 回调: taskId={}, passed={}, reward={}", taskId, passed, reward);
        };

        TrialTools trialTools = new TrialTools(commandRunner, verifier, traceCollector);

        // 通过 ClasspathSkillManager 加载 skill-evolver-trial（含 SKILL.md + references/ + scripts/）
        ClasspathSkillManager classpathSkillManager = ApplicationContextProvider.getBean(ClasspathSkillManager.class);
        List<AgentSkill> classpathSkills = classpathSkillManager.loadSkills("skills");

        // 找到 skill-evolver-trial
        AgentSkill trialSkill = classpathSkills.stream()
                .filter(s -> "skill-evolver-trial".equals(s.getName()))
                .findFirst()
                .orElse(null);

        if (trialSkill != null) {
            log.info("[TrialTestAgent] 从 classpath 加载 Skill: {}", trialSkill.getName());
            // 先注册 classpath skill（含文件资源），再把 TrialTools 注册到同一 skill 下
            return components.skillBox().create(getToolkit())
                    .addClasspathSkills("skills", false)           // 注册 skill 文件资源（不开代码执行）
                    .addSkillWithTools(trialSkill, new Object[]{trialTools})  // 注册 Java @Tool 方法
                    .buildSkillBox();
        } else {
            log.warn("[TrialTestAgent] classpath 中未找到 skill-evolver-trial，使用占位 Skill");
            AgentSkill placeholderSkill = BaseSkillBoxFactory.createBaseAgentSkill(
                    "trial-tools", "Trial execution tools",
                    "Tools for executing tasks in isolated workspaces: list files, write files, run commands, verify results.");
            return components.skillBox().create(getToolkit())
                    .addSkillWithTools(placeholderSkill, new Object[]{trialTools})
                    .buildSkillBox();
        }
    }

    // ======================== 内部工具实现 ========================

    private TrialTools.CommandResult runCommand(String workDir, String command, int timeoutSeconds) {
        try {
            Path dir = Paths.get(workDir);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            ProcessBuilder pb = new ProcessBuilder()
                    .command("cmd", "/c", command)
                    .directory(dir.toFile())
                    .redirectErrorStream(false);

            Process proc = pb.start();

            String stdout = new String(proc.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            String stderr = new String(proc.getErrorStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);

            boolean finished = proc.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            int exitCode = finished ? proc.exitValue() : -1;

            if (!finished) {
                proc.destroyForcibly();
                stderr += "\n⏰ 执行超时（" + timeoutSeconds + "秒）";
            }

            // 自动保存 .trial_result.json
            writeTrialResult(workDir, exitCode, stdout, stderr);

            return new TrialTools.CommandResult(exitCode, stdout, stderr);
        } catch (IOException | InterruptedException e) {
            return new TrialTools.CommandResult(-1, "", e.getMessage());
        }
    }

    /**
     * 执行命令但不保存 .trial_result.json（用于 verifier 的 command 规则，
     * 避免覆盖正常执行的 trial 结果）。
     */
    private TrialTools.CommandResult runCommandWithoutSave(String workDir, String command, int timeoutSeconds) {
        try {
            Path dir = Paths.get(workDir);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            ProcessBuilder pb = new ProcessBuilder()
                    .command("cmd", "/c", command)
                    .directory(dir.toFile())
                    .redirectErrorStream(false);

            Process proc = pb.start();

            String stdout = new String(proc.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            String stderr = new String(proc.getErrorStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);

            boolean finished = proc.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            int exitCode = finished ? proc.exitValue() : -1;

            if (!finished) {
                proc.destroyForcibly();
                stderr += "\n⏰ 执行超时（" + timeoutSeconds + "秒）";
            }

            return new TrialTools.CommandResult(exitCode, stdout, stderr);
        } catch (IOException | InterruptedException e) {
            return new TrialTools.CommandResult(-1, "", e.getMessage());
        }
    }

    private TrialTools.VerifyResult verifyResult(String workDir, String expectedData) {
        try {
            // 获取 verifier 规则 — 优先从 ConcurrentHashMap 获取（跨线程安全），
            // 回退到 TrialContext ThreadLocal（同线程场景）
            String verifierJson = consumeVerifier(workDir);
            if (verifierJson == null || verifierJson.isBlank()) {
                TrialContext ctx = getContext();
                if (ctx != null) {
                    verifierJson = ctx.getVerifier();
                }
            }
            log.info("[TrialTestAgent] verifyResult: workDir={}, verifierJson={}",
                    workDir, verifierJson != null ? verifierJson.substring(0, Math.min(80, verifierJson.length())) + "..." : "null");

            // ── 基础检查：读取 .trial_result.json ──
            Path resultFile = Paths.get(workDir, ".trial_result.json");
            int exitCode = -1;
            String stdout = "";
            String stderr = "";
            boolean resultFileExists = Files.exists(resultFile);

            if (resultFileExists) {
                String json = Files.readString(resultFile, java.nio.charset.StandardCharsets.UTF_8);
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(json);
                exitCode = node.has("exitCode") ? node.get("exitCode").asInt() : -1;
                stdout = node.has("stdout") ? node.get("stdout").asText() : "";
                stderr = node.has("stderr") ? node.get("stderr").asText() : "";
            }

            // ── 如果没有 verifier 规则，保持原有逻辑（仅看 exitCode） ──
            if (verifierJson == null || verifierJson.isBlank()) {
                if (resultFileExists) {
                    if (exitCode == 0) {
                        String preview = stdout.length() > 200 ? stdout.substring(0, 200) + "..." : stdout;
                        return new TrialTools.VerifyResult(true, 0.8,
                                "执行成功 (exitCode=0, 无外部验证规则), stdout: " + preview);
                    } else {
                        String errPreview = stderr.length() > 200 ? stderr.substring(0, 200) + "..." : stderr;
                        return new TrialTools.VerifyResult(false, 0.0,
                                "执行失败 (exitCode=" + exitCode + "), stderr: " + errPreview);
                    }
                }
                // 降级：检查 output.json
                Path outputFile = Paths.get(workDir, "output.json");
                if (Files.exists(outputFile)) {
                    String content = Files.readString(outputFile, java.nio.charset.StandardCharsets.UTF_8);
                    return new TrialTools.VerifyResult(true, 0.6,
                            "找到 output.json (无外部验证规则): " + content.substring(0, Math.min(200, content.length())));
                }
                return new TrialTools.VerifyResult(false, 0.0, "未找到执行结果文件");
            }

            // ── 有 verifier 规则：先检查 exitCode，再逐条验证 ──
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode verifierNode;
            try {
                verifierNode = mapper.readTree(verifierJson);
            } catch (Exception e) {
                log.warn("[TrialTestAgent] verifier JSON 解析失败，降级到 exitCode 检查: {}", e.getMessage());
                return new TrialTools.VerifyResult(exitCode == 0, exitCode == 0 ? 0.8 : 0.0,
                        "verifier JSON 解析失败, exitCode=" + exitCode);
            }

            // 先检查 exitCode（硬性前提）
            int requiredExitCode = verifierNode.has("exitCodeMustBe") ? verifierNode.get("exitCodeMustBe").asInt(0) : 0;
            if (exitCode != requiredExitCode) {
                String errPreview = stderr.length() > 200 ? stderr.substring(0, 200) + "..." : stderr;
                return new TrialTools.VerifyResult(false, 0.0,
                        "exitCode 不符合要求 (期望=" + requiredExitCode + ", 实际=" + exitCode + "), stderr: " + errPreview);
            }

            // 收集所有验证失败消息
            java.util.List<String> failures = new java.util.ArrayList<>();
            int totalChecks = 0;

            // ── 规则 1: fileExists — 检查文件是否存在 ──
            if (verifierNode.has("fileExists")) {
                com.fasterxml.jackson.databind.JsonNode filesNode = verifierNode.get("fileExists");
                if (filesNode.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode f : filesNode) {
                        totalChecks++;
                        String fileName = f.asText();
                        Path filePath = Paths.get(workDir, fileName);
                        if (!Files.exists(filePath)) {
                            failures.add("fileExists: 文件不存在 '" + fileName + "'");
                        }
                    }
                }
            }

            // ── 规则 2: fileContains — 检查文件包含指定文本 ──
            if (verifierNode.has("fileContains")) {
                com.fasterxml.jackson.databind.JsonNode containsNode = verifierNode.get("fileContains");
                if (containsNode.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode item : containsNode) {
                        totalChecks++;
                        String file = item.has("file") ? item.get("file").asText() : "";
                        String text = item.has("text") ? item.get("text").asText() : "";
                        if (!file.isEmpty() && !text.isEmpty()) {
                            Path filePath = Paths.get(workDir, file);
                            if (Files.exists(filePath)) {
                                String content = Files.readString(filePath, java.nio.charset.StandardCharsets.UTF_8);
                                if (!content.contains(text)) {
                                    failures.add("fileContains: '" + file + "' 不包含文本 '" + truncateStr(text, 50) + "'");
                                }
                            } else {
                                failures.add("fileContains: 文件 '" + file + "' 不存在");
                            }
                        }
                    }
                }
            }

            // ── 规则 3: stdoutContains — 检查 stdout 包含关键词 ──
            if (verifierNode.has("stdoutContains")) {
                com.fasterxml.jackson.databind.JsonNode keywordsNode = verifierNode.get("stdoutContains");
                if (keywordsNode.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode kw : keywordsNode) {
                        totalChecks++;
                        String keyword = kw.asText();
                        if (!stdout.contains(keyword)) {
                            failures.add("stdoutContains: stdout 不包含 '" + truncateStr(keyword, 50) + "'");
                        }
                    }
                }
            }

            // ── 规则 4: stdoutMatches — 检查 stdout 匹配正则 ──
            if (verifierNode.has("stdoutMatches")) {
                String regex = verifierNode.get("stdoutMatches").asText();
                totalChecks++;
                if (!stdout.matches("(?s)" + regex)) {
                    failures.add("stdoutMatches: stdout 不匹配正则 '" + truncateStr(regex, 50) + "'");
                }
            }

            // ── 规则 5: command — 执行额外验证命令并检查 exitCode（不覆盖 trial_result） ──
            if (verifierNode.has("command")) {
                String verifyCmd = verifierNode.get("command").asText();
                totalChecks++;
                TrialTools.CommandResult cmdResult = runCommandWithoutSave(workDir, verifyCmd, 120);
                if (cmdResult.exitCode() != 0) {
                    String cmdErr = cmdResult.stderr().length() > 200
                            ? cmdResult.stderr().substring(0, 200) + "..." : cmdResult.stderr();
                    failures.add("command: '" + truncateStr(verifyCmd, 60) + "' exitCode="
                            + cmdResult.exitCode() + ", stderr: " + cmdErr);
                }
            }

            // ── 规则 6: containsColumn — 检查输出文件中是否包含列名 ──
            if (verifierNode.has("containsColumn")) {
                com.fasterxml.jackson.databind.JsonNode colsNode = verifierNode.get("containsColumn");
                String targetFile = verifierNode.has("targetFile") ? verifierNode.get("targetFile").asText() : "output.json";
                if (colsNode.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode col : colsNode) {
                        totalChecks++;
                        String colName = col.asText();
                        Path filePath = Paths.get(workDir, targetFile);
                        if (Files.exists(filePath)) {
                            String content = Files.readString(filePath, java.nio.charset.StandardCharsets.UTF_8);
                            if (!content.contains(colName)) {
                                failures.add("containsColumn: '" + targetFile + "' 不包含列名 '" + colName + "'");
                            }
                        } else {
                            failures.add("containsColumn: 目标文件 '" + targetFile + "' 不存在");
                        }
                    }
                }
            }

            // ── 汇总结果 ──
            boolean allPassed = failures.isEmpty();
            double score;
            if (totalChecks == 0) {
                score = 0.8; // 只有 exitCode 检查
            } else {
                long passedChecks = totalChecks - failures.size();
                score = 0.3 + 0.7 * ((double) passedChecks / totalChecks);
                if (allPassed) score = Math.max(score, 0.9);
            }

            String message;
            if (allPassed) {
                message = "验证全部通过 (exitCode=" + exitCode + ", " + totalChecks + " 条规则全部满足)";
            } else {
                message = "验证未通过 (" + failures.size() + "/" + totalChecks + " 条规则失败): "
                        + String.join("; ", failures);
            }

            return new TrialTools.VerifyResult(allPassed, score, message);

        } catch (Exception e) {
            return new TrialTools.VerifyResult(false, 0.0, "验证异常: " + e.getMessage());
        }
    }

    private static String truncateStr(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    private void writeTrialResult(String workDir, int exitCode, String stdout, String stderr) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("exitCode", exitCode);
            result.put("stdout", stdout);
            result.put("stderr", stderr);
            result.put("timestamp", java.time.Instant.now().toString());

            Path resultFile = Paths.get(workDir, ".trial_result.json");
            Files.createDirectories(resultFile.getParent());
            Files.writeString(resultFile, mapper.writeValueAsString(result), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("[TrialTestAgent] 保存 .trial_result.json 失败: {}", e.getMessage());
        }
    }
}
