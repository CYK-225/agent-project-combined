package org.example.skillEvolver.graph;

import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.extern.slf4j.Slf4j;
import org.example.graph.createGraph.node.SimpleNodeAction;
import org.example.graph.workflow.annotation.NodeAction;
import org.example.skillEvolver.config.ApplicationContextProvider;
import org.example.skillEvolver.service.EvolverSkillVersionService;
import org.example.skillEvolver.service.EvolverTaskService;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 最终验证 + 持久化节点。
 * <p>
 * 选择最佳版本，执行最终验证，将 Skill 持久化到文件系统
 * （兼容 AgentScope LocalSkillLoader 格式）。
 * 同时更新数据库：evolver_task 状态 + evolver_skill_version 最佳标记。
 *
 * @author zhilin
 */
@Slf4j
@NodeAction(value = "evolver-finalize", description = "最终验证 + 选择最佳版本 + 持久化 Skill + 更新数据库")
public class FinalizeNode extends SimpleNodeAction {

    @Override
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String currentSkill = (String) state.value("currentSkill").orElse("");
        String skillVersion = (String) state.value("skillVersion").orElse("v-final");
        String taskId = (String) state.value("taskId").orElse("unknown");
        int iteration = (int) state.value("currentIteration").orElse(0);
        Double bestReward = (Double) state.value("bestReward").orElse(0.0);
        Double trialPassRate = (Double) state.value("trialPassRate").orElse(0.0);

        // 最优版本追踪（优先使用 reward 最高的版本）
        String bestSkillContent = (String) state.value("bestSkillContent").orElse("");
        String bestSkillVersion = (String) state.value("bestSkillVersion").orElse("");
        double bestRewardEver = (Double) state.value("bestRewardEver").orElse(0.0);

        String finalSkill = bestSkillContent.isEmpty() ? currentSkill : bestSkillContent;
        String finalVersion = bestSkillVersion.isEmpty() ? skillVersion : bestSkillVersion;
        Double finalReward = bestRewardEver > 0.0 ? bestRewardEver : bestReward;

        log.info("[FinalizeNode] 最终处理: taskId={}, iterations={}, skillVersion={}, bestVersion={} (reward={})",
                taskId, iteration, skillVersion, finalVersion, String.format("%.3f", finalReward));

        // 1. 文件系统持久化
        String skillName = "evolved-" + taskId;
        String outputDir = System.getProperty("evolver.skill-output-dir", "./evolved-skills");
        Path skillDir = Paths.get(outputDir, skillName);
        Files.createDirectories(skillDir);

        // 写 SKILL.md
        Path skillMd = skillDir.resolve("SKILL.md");
        Files.writeString(skillMd, currentSkill, StandardCharsets.UTF_8);

        // 写版本快照
        Path versionsDir = skillDir.resolve("versions");
        Files.createDirectories(versionsDir);
        Path versionFile = versionsDir.resolve(skillVersion + ".md");
        Files.writeString(versionFile, currentSkill, StandardCharsets.UTF_8);

        // 写进化元数据
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("taskId", taskId);
        metadata.put("totalIterations", iteration);
        metadata.put("finalVersion", skillVersion);
        metadata.put("bestReward", bestReward);
        metadata.put("trialPassRate", trialPassRate);
        metadata.put("finalizedAt", new Date().toString());
        Path metaFile = skillDir.resolve("evolution-metadata.properties");
        StringBuilder propContent = new StringBuilder();
        metadata.forEach((k, v) -> propContent.append(k).append("=").append(v).append("\n"));
        Files.writeString(metaFile, propContent.toString(), StandardCharsets.UTF_8);

        log.info("[FinalizeNode] Skill 已持久化: {} ({} 字符)", skillDir, currentSkill.length());

        // 2. 数据库持久化
        persistToDatabase(taskId, skillVersion, currentSkill, bestReward, trialPassRate, iteration);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("status", "COMPLETED");
        output.put("outputDir", skillDir.toAbsolutePath().toString());
        output.put("skillMdPath", skillMd.toAbsolutePath().toString());
        output.put("skillName", skillName);
        output.put("skillLength", finalSkill.length());
        output.put("totalIterations", iteration);
        return output;
    }

    private void persistToDatabase(String taskId, String skillVersion, String currentSkill,
                                    Double bestReward, Double trialPassRate, int iteration) {
        try {
            // 更新任务表：最佳 skill + 完成状态 + 迭代轮次
            EvolverTaskService taskService = ApplicationContextProvider.getBean(EvolverTaskService.class);
            taskService.updateCurrentIteration(taskId, iteration);
            taskService.updateBestSkill(taskId, skillVersion, currentSkill, bestReward);
            taskService.updateValidationResult(taskId, trialPassRate, 0L);

            // 标记最佳版本
            EvolverSkillVersionService versionService = ApplicationContextProvider.getBean(EvolverSkillVersionService.class);
            versionService.markAsBest(taskId, skillVersion);

            log.info("[FinalizeNode] 数据库已更新: taskId={}, version={}, passRate={}",
                    taskId, skillVersion, trialPassRate);
        } catch (Exception e) {
            log.warn("[FinalizeNode] 数据库持久化失败（文件已保存）: {}", e.getMessage(), e);
        }
    }
}
