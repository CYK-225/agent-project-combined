package org.example.skillOpt.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.agentScope.framework.core.SkillRepoRegistry;
import org.example.agentScope.mas.skillManager.core.GitSkillManager;
import org.example.skillOpt.dto.TrainingJobRequest;
import org.example.skillOpt.service.SkillOptTrainingJobService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Skill 训练统一管理 Controller。
 * <p>
 * 提供完整的训练流程 API：
 * <ul>
 *   <li>POST /api/skill-training/register-third-party — 注册第三方 Skill 仓库</li>
 *   <li>POST /api/skill-training/prepare-data — 准备训练数据</li>
 *   <li>POST /api/skill-training/submit — 提交训练任务</li>
 *   <li>GET  /api/skill-training/status/{jobId} — 查询训练状态</li>
 *   <li>GET  /api/skill-training/result/{jobId} — 获取训练结果</li>
 *   <li>POST /api/skill-training/validate — 验证输入文档格式</li>
 * </ul>
 *
 * @author zhilin
 */
@Slf4j
@RestController
@RequestMapping("/api/skill-training")
@RequiredArgsConstructor
public class SkillTrainingController {

    private final SkillRepoRegistry skillRepoRegistry;
    private final GitSkillManager gitSkillManager;
    private final SkillOptTrainingJobService jobService;
    private final SkillOptController skillOptController;

    // ======================== DTO ========================

    /**
     * 第三方 Skill 仓库注册请求
     */
    @Data
    public static class ThirdPartySkillRequest {
        /** Agent 名称（如 SkillOptToolTarget） */
        private String agentName;
        /** Git 仓库 URL */
        private String repoUrl;
        /** Skill 名称过滤正则（可选） */
        private String[] skillPatterns;
        /** 是否立即刷新仓库 */
        private boolean refreshImmediately = true;
    }

    /**
     * 训练数据准备请求
     */
    @Data
    public static class TrainingDataRequest {
        /** 任务描述 */
        private String taskDescription;
        /** 初始 Skill 内容（Markdown 格式） */
        private String initialSkill;
        /** 训练数据 JSON 字符串 */
        private String trainDataJson;
        /** 验证数据 JSON 字符串 */
        private String valDataJson;
        /** 训练数据文件路径（可选，与 trainDataJson 二选一） */
        private String trainDataPath;
        /** 验证数据文件路径（可选，与 valDataJson 二选一） */
        private String valDataPath;
    }

    /**
     * 完整训练请求
     */
    @Data
    public static class FullTrainingRequest {
        // ============ Skill 仓库配置 ============
        /** 第三方 Skill 仓库 URL（可选） */
        private String skillRepoUrl;
        /** Skill 名称过滤正则（可选） */
        private String[] skillPatterns;

        // ============ 训练数据 ============
        /** 任务描述 */
        private String taskDescription;
        /** 初始 Skill 内容（Markdown 格式） */
        private String initialSkill;
        /** 训练数据 JSON */
        private String trainData;
        /** 验证数据 JSON */
        private String valData;

        // ============ 训练参数 ============
        /** 环境适配器类型（默认 llm-qa） */
        private String envAdapterType = "llm-qa";
        /** LR 调度器类型（默认 cosine） */
        private String lrSchedulerType = "cosine";
        /** 验证门控类型（默认 mixed） */
        private String gateType = "mixed";
        /** 最大 epoch 数（默认 5） */
        private Integer maxEpochs = 5;
        /** 每 epoch rollout 数量（默认 4） */
        private Integer batchSize = 4;
        /** 基础编辑预算（默认 5） */
        private Integer editBudgetBase = 5;
    }

    /**
     * 输入文档验证结果
     */
    @Data
    public static class ValidationResult {
        private boolean valid;
        private List<String> errors = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
        private Map<String, Object> summary = new LinkedHashMap<>();
    }

    // ======================== API ========================

    /**
     * 注册第三方 Skill 仓库
     * <p>
     * 示例：
     * <pre>{@code
     * {
     *   "agentName": "SkillOptToolTarget",
     *   "repoUrl": "https://github.com/third-party/skills.git",
     *   "skillPatterns": ["code-review.*", "vuln-scan.*"],
     *   "refreshImmediately": true
     * }
     * }</pre>
     */
    @PostMapping("/register-third-party")
    public ResponseEntity<Map<String, Object>> registerThirdPartySkill(
            @RequestBody ThirdPartySkillRequest request) {
        log.info("[SkillTraining] 注册第三方 Skill: agent={}, repo={}",
                request.getAgentName(), request.getRepoUrl());

        // 验证参数
        if (request.getAgentName() == null || request.getAgentName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "agentName is required"));
        }
        if (request.getRepoUrl() == null || request.getRepoUrl().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "repoUrl is required"));
        }

        // 注册到 Registry
        skillRepoRegistry.put(request.getAgentName(), request.getRepoUrl(), request.getSkillPatterns());

        // 可选：立即刷新仓库
        List<String> availableSkills = new ArrayList<>();
        if (request.isRefreshImmediately()) {
            try {
                gitSkillManager.refreshSkills(request.getRepoUrl());
                availableSkills = gitSkillManager.listSkillNames(request.getRepoUrl());
                log.info("[SkillTraining] 刷新仓库成功，可用 Skill: {}", availableSkills);
            } catch (Exception e) {
                log.warn("[SkillTraining] 刷新仓库失败: {}", e.getMessage());
            }
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "agentName", request.getAgentName(),
                "repoUrl", request.getRepoUrl(),
                "skillPatterns", request.getSkillPatterns() != null
                        ? Arrays.toString(request.getSkillPatterns()) : "all",
                "availableSkills", availableSkills,
                "message", "第三方 Skill 仓库注册成功"
        ));
    }

    /**
     * 验证输入文档格式
     * <p>
     * 在提交训练前，先验证输入文档是否符合规范
     */
    @PostMapping("/validate")
    public ResponseEntity<ValidationResult> validateInput(@RequestBody FullTrainingRequest request) {
        ValidationResult result = new ValidationResult();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, Object> summary = new LinkedHashMap<>();

        // 1. 验证任务描述
        if (request.getTaskDescription() == null || request.getTaskDescription().isBlank()) {
            errors.add("taskDescription is required");
        } else {
            summary.put("taskDescription", request.getTaskDescription().substring(0,
                    Math.min(50, request.getTaskDescription().length())) + "...");
        }

        // 2. 验证初始 Skill（可选，但建议提供）
        if (request.getInitialSkill() == null || request.getInitialSkill().isBlank()) {
            warnings.add("initialSkill is empty — will start from scratch");
        } else {
            summary.put("initialSkillLength", request.getInitialSkill().length());
        }

        // 3. 验证训练数据
        if (request.getTrainData() == null || request.getTrainData().isBlank()) {
            errors.add("trainData is required");
        } else {
            try {
                List<?> trainList = parseJsonArray(request.getTrainData());
                summary.put("trainDataSize", trainList.size());
                if (trainList.size() < 2) {
                    warnings.add("trainData has less than 2 samples — training may not be effective");
                }
            } catch (Exception e) {
                errors.add("trainData JSON parse error: " + e.getMessage());
            }
        }

        // 4. 验证验证数据
        if (request.getValData() == null || request.getValData().isBlank()) {
            errors.add("valData is required");
        } else {
            try {
                List<?> valList = parseJsonArray(request.getValData());
                summary.put("valDataSize", valList.size());
                if (valList.isEmpty()) {
                    warnings.add("valData is empty — validation will be skipped");
                }
            } catch (Exception e) {
                errors.add("valData JSON parse error: " + e.getMessage());
            }
        }

        // 5. 验证训练参数
        if (request.getMaxEpochs() != null && request.getMaxEpochs() < 1) {
            errors.add("maxEpochs must be >= 1");
        }
        if (request.getBatchSize() != null && request.getBatchSize() < 1) {
            errors.add("batchSize must be >= 1");
        }

        // 6. 验证第三方 Skill 仓库（可选）
        if (request.getSkillRepoUrl() != null && !request.getSkillRepoUrl().isBlank()) {
            try {
                List<String> skills = gitSkillManager.listSkillNames(request.getSkillRepoUrl());
                summary.put("thirdPartySkills", skills);
                if (skills.isEmpty()) {
                    warnings.add("No skills found in the specified repo URL");
                }
            } catch (Exception e) {
                warnings.add("Cannot access skill repo: " + e.getMessage());
            }
        }

        result.setValid(errors.isEmpty());
        result.setErrors(errors);
        result.setWarnings(warnings);
        result.setSummary(summary);

        return ResponseEntity.ok(result);
    }

    /**
     * 提交完整训练任务（一步到位）
     * <p>
     * 示例：
     * <pre>{@code
     * {
     *   "skillRepoUrl": "https://github.com/third-party/skills.git",
     *   "skillPatterns": ["code-review.*"],
     *   "taskDescription": "使用 code-review 工具进行代码安全审查",
     *   "initialSkill": "# Skill: 代码审查助手\n\n## 任务描述\n...",
     *   "trainData": "[{\"question\":\"审查SQL注入风险\",\"answer\":\"存在漏洞\"}]",
     *   "valData": "[{\"question\":\"审查XSS风险\",\"answer\":\"需要转义\"}]",
     *   "envAdapterType": "search-qa",
     *   "maxEpochs": 5,
     *   "batchSize": 4
     * }
     * }</pre>
     */
    @PostMapping("/submit")
    public ResponseEntity<Map<String, Object>> submitTraining(
            @RequestBody FullTrainingRequest request) {
        log.info("[SkillTraining] 提交训练任务: task={}", request.getTaskDescription());

        // 1. 如果有第三方 Skill 仓库，先注册
        if (request.getSkillRepoUrl() != null && !request.getSkillRepoUrl().isBlank()) {
            ThirdPartySkillRequest skillRequest = new ThirdPartySkillRequest();
            skillRequest.setAgentName("SkillOptToolTarget");
            skillRequest.setRepoUrl(request.getSkillRepoUrl());
            skillRequest.setSkillPatterns(request.getSkillPatterns());
            skillRequest.setRefreshImmediately(true);
            registerThirdPartySkill(skillRequest);
        }

        // 2. 构建训练请求
        TrainingJobRequest jobRequest = new TrainingJobRequest();
        jobRequest.setTaskDescription(request.getTaskDescription());
        jobRequest.setInitialSkill(request.getInitialSkill());
        jobRequest.setTrainData(request.getTrainData());
        jobRequest.setValData(request.getValData());
        jobRequest.setEnvAdapterType(request.getEnvAdapterType());
        jobRequest.setLrSchedulerType(request.getLrSchedulerType());
        jobRequest.setGateType(request.getGateType());
        jobRequest.setMaxEpochs(request.getMaxEpochs());
        jobRequest.setBatchSize(request.getBatchSize());
        jobRequest.setEditBudgetBase(request.getEditBudgetBase());

        // 3. 提交训练任务
        return skillOptController.submit(jobRequest);
    }

    /**
     * 查询训练状态
     */
    @GetMapping("/status/{jobId}")
    public ResponseEntity<Map<String, Object>> getTrainingStatus(@PathVariable String jobId) {
        var jobResponse = skillOptController.getJob(jobId);
        if (jobResponse.getStatusCode().isError()) {
            return ResponseEntity.status(jobResponse.getStatusCode())
                    .body(Map.of("error", "Job not found: " + jobId));
        }

        var job = jobResponse.getBody();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("jobId", job.getJobId());
        status.put("status", job.getStatus());
        status.put("currentEpoch", job.getCurrentEpoch());
        status.put("maxEpochs", job.getMaxEpochs());
        status.put("bestValidationScore", job.getBestValidationScore());
        status.put("bestSkillEpoch", job.getBestSkillEpoch());
        status.put("createdAt", job.getCreatedAt());
        status.put("updatedAt", job.getUpdatedAt());

        return ResponseEntity.ok(status);
    }

    /**
     * 获取训练结果（完整报告）
     */
    @GetMapping("/result/{jobId}")
    public ResponseEntity<Map<String, Object>> getTrainingResult(@PathVariable String jobId) {
        // 获取任务信息
        var jobResponse = skillOptController.getJob(jobId);
        if (jobResponse.getStatusCode().isError()) {
            return ResponseEntity.status(jobResponse.getStatusCode())
                    .body(Map.of("error", "Job not found: " + jobId));
        }

        var job = jobResponse.getBody();

        // 获取 Skill 进化历史
        var historyResponse = skillOptController.getSkillHistory(jobId);
        Map<String, Object> history = historyResponse.getBody();

        // 获取编辑日志
        var editsResponse = skillOptController.getEditLogs(jobId);
        var edits = editsResponse.getBody();

        // 构建完整报告
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobId", jobId);
        result.put("status", job.getStatus());
        result.put("taskDescription", job.getTaskDescription());

        // 最佳 Skill
        result.put("bestSkillContent", job.getBestSkillContent());
        result.put("bestValidationScore", job.getBestValidationScore());
        result.put("bestSkillEpoch", job.getBestSkillEpoch());

        // 训练统计
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalEpochs", job.getCurrentEpoch());
        stats.put("maxEpochs", job.getMaxEpochs());
        stats.put("batchSize", job.getBatchSize());
        if (history != null) {
            stats.put("scoreCurve", history.get("scoreCurve"));
        }
        result.put("trainingStats", stats);

        // 编辑日志
        result.put("editLogs", edits);

        // Skill 进化时间线
        if (history != null) {
            result.put("timeline", history.get("timeline"));
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 获取训练输入模板
     * <p>
     * 返回标准的输入文档模板，方便用户填写
     */
    @GetMapping("/template")
    public ResponseEntity<Map<String, Object>> getTrainingTemplate() {
        Map<String, Object> template = new LinkedHashMap<>();

        // 1. 第三方 Skill 配置模板
        template.put("thirdPartySkill", Map.of(
                "agentName", "SkillOptToolTarget",
                "repoUrl", "https://github.com/your-org/your-skills.git",
                "skillPatterns", List.of("skill-name-1.*", "skill-name-2.*"),
                "refreshImmediately", true
        ));

        // 2. 训练数据模板
        template.put("trainingData", Map.of(
                "taskDescription", "任务描述：使用 XX 工具完成 YY 任务",
                "initialSkill", "# Skill: XX助手\n\n## 任务描述\n...\n\n## 工具使用指南\n...",
                "trainData", List.of(
                        Map.of("question", "训练问题1", "answer", "期望答案1"),
                        Map.of("question", "训练问题2", "answer", "期望答案2")
                ),
                "valData", List.of(
                        Map.of("question", "验证问题1", "answer", "期望答案1")
                )
        ));

        // 3. 训练参数模板
        template.put("trainingParams", Map.of(
                "envAdapterType", "llm-qa | search-qa",
                "lrSchedulerType", "constant | linear | cosine | autonomous",
                "gateType", "hard | soft | mixed",
                "maxEpochs", 5,
                "batchSize", 4,
                "editBudgetBase", 5
        ));

        // 4. 评判标准说明
        template.put("evaluationCriteria", Map.of(
                "hardScore", "精确匹配：答案完全一致 = 1.0，否则 = 0.0",
                "softScore", "部分信用：基于 token 级别的 F1 分数 (0.0~1.0)",
                "mixedScore", "混合评分：alpha * hard + (1-alpha) * soft",
                "gatePolicy", Map.of(
                        "hard", "只看精确匹配分数",
                        "soft", "只看 F1 分数",
                        "mixed", "混合评分（推荐）"
                )
        ));

        return ResponseEntity.ok(template);
    }

    // ======================== 辅助方法 ========================

    private List<?> parseJsonArray(String json) throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        return mapper.readValue(json, List.class);
    }
}
