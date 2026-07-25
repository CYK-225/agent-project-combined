package org.example.skillOpt.graph;

import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.extern.slf4j.Slf4j;
import org.example.graph.createGraph.node.SimpleNodeAction;
import org.example.graph.workflow.annotation.NodeAction;
import org.example.skillEvolver.config.ApplicationContextProvider;
import org.example.skillOpt.edit.SkillEdit;
import org.example.skillOpt.edit.SkillEditor;
import org.example.skillOpt.entity.SkillOptEditLogEntity;
import org.example.skillOpt.mapper.SkillOptEditLogMapper;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 阶段 5: Update — 应用编辑到 skill 文档（optimizer.step）。
 *
 * @author zhilin
 */
@Slf4j
@NodeAction(value = "skillopt-update", description = "阶段5: Update — 应用编辑到 skill")
public class UpdateNode extends SimpleNodeAction {

    @Override
    @SuppressWarnings("unchecked")
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String jobId = (String) state.value("jobId").orElse("");
        int epoch = (int) state.value("currentEpoch").orElse(0);
        String currentSkill = (String) state.value("currentSkill").orElse("");
        List<String> protectedRegions = (List<String>) state.value("protectedRegions").orElse(List.of());

        List<SkillEdit> selectedEdits =
                (List<SkillEdit>) state.value("selectedEdits").orElse(List.of());

        log.info("[UpdateNode] jobId={}, epoch={}, edits={}", jobId, epoch, selectedEdits.size());

        String candidateSkill;

        if (selectedEdits.isEmpty()) {
            log.info("[UpdateNode] 无编辑，保留当前 skill");
            candidateSkill = currentSkill;
        } else {
            // 过滤受保护区域
            List<SkillEdit> filteredEdits = SkillEditor.filterByProtectedRegions(
                    selectedEdits, protectedRegions);

            if (filteredEdits.isEmpty()) {
                log.info("[UpdateNode] 所有编辑被受保护区域过滤");
                candidateSkill = currentSkill;
            } else {
                // 应用编辑
                candidateSkill = SkillEditor.applyEdits(currentSkill, filteredEdits);
                log.info("[UpdateNode] 应用了 {}/{} 个编辑", filteredEdits.size(), selectedEdits.size());
            }

            // 持久化编辑日志
            persistEditLogs(jobId, epoch, selectedEdits);
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("candidateSkill", candidateSkill);
        output.put("previousSkill", currentSkill);
        output.put("updateAction", selectedEdits.isEmpty() ? "skip" : "update");
        return output;
    }

    private void persistEditLogs(String jobId, int epoch, List<SkillEdit> edits) {
        try {
            SkillOptEditLogMapper mapper =
                    ApplicationContextProvider.getBean(SkillOptEditLogMapper.class);
            for (int i = 0; i < edits.size(); i++) {
                SkillEdit edit = edits.get(i);
                SkillOptEditLogEntity entity = SkillOptEditLogEntity.builder()
                        .jobId(jobId)
                        .epoch(epoch)
                        .editIndex(i)
                        .editType(edit.getType().name())
                        .targetSection(edit.getTargetSection())
                        .content(edit.getContent())
                        .rationale(edit.getRationale())
                        .priority(edit.getPriority())
                        .applied(true)
                        .createdAt(LocalDateTime.now())
                        .build();
                mapper.insert(entity);
            }
        } catch (Exception e) {
            log.warn("[UpdateNode] 编辑日志持久化失败: {}", e.getMessage());
        }
    }
}
