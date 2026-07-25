package org.example.skillOpt.graph;

import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.extern.slf4j.Slf4j;
import org.example.graph.createGraph.node.SimpleNodeAction;
import org.example.graph.workflow.annotation.NodeAction;
import org.example.skillEvolver.config.ApplicationContextProvider;
import org.example.skillOpt.entity.SkillOptTrainingJobEntity;
import org.example.skillOpt.env.EnvAdapter;
import org.example.skillOpt.env.qa.QAEnvAdapter;
import org.example.skillOpt.env.search.SearchQAEnvAdapter;
import org.example.skillOpt.service.SkillOptTrainingJobService;

import java.util.*;

/**
 * 初始化训练任务 — 从数据库加载配置，准备训练/验证数据。
 *
 * @author zhilin
 */
@Slf4j
@NodeAction(value = "skillopt-init-job", description = "初始化 SkillOpt 训练任务，加载配置和数据")
public class InitJobNode extends SimpleNodeAction {

    @Override
    @SuppressWarnings("unchecked")
    protected Map<String, Object> execute(OverAllState state) throws Exception {
        String jobId = (String) state.value("jobId").orElse("");
        log.info("[InitJobNode] 初始化训练任务: jobId={}", jobId);

        SkillOptTrainingJobService jobService =
                ApplicationContextProvider.getBean(SkillOptTrainingJobService.class);
        SkillOptTrainingJobEntity job = jobService.getByJobId(jobId);

        if (job == null) {
            throw new RuntimeException("训练任务不存在: " + jobId);
        }

        // 创建环境适配器
        EnvAdapter envAdapter = createEnvAdapter(job.getEnvAdapterType());

        // 解析训练/验证数据
        List<Map<String, Object>> trainBatch = envAdapter.prepareTrainBatch(
                job.getTrainData(), job.getBatchSize());
        List<Map<String, Object>> valBatch = envAdapter.prepareValBatch(
                job.getValData(), job.getBatchSize());

        String initialSkill = job.getInitialSkill() != null ? job.getInitialSkill() : "";

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("taskDescription", job.getTaskDescription());
        output.put("currentSkill", initialSkill);
        output.put("previousSkill", "");
        output.put("bestSkill", initialSkill);
        output.put("bestValidationScore", 0.0);
        output.put("previousValidationScore", 0.0);
        output.put("metaSkill", "");
        output.put("protectedRegions", List.of());
        output.put("maxEpochs", job.getMaxEpochs());
        output.put("currentEpoch", 0);
        output.put("batchSize", job.getBatchSize());
        output.put("editBudgetBase", job.getEditBudgetBase());
        output.put("lrSchedulerType", job.getLrSchedulerType());
        output.put("gateType", job.getGateType());
        output.put("envAdapterType", job.getEnvAdapterType());
        output.put("trainBatchRaw", trainBatch);
        output.put("valBatchRaw", valBatch);

        log.info("[InitJobNode] 初始化完成: maxEpochs={}, batchSize={}, lrScheduler={}, gate={}, trainSize={}, valSize={}",
                job.getMaxEpochs(), job.getBatchSize(), job.getLrSchedulerType(),
                job.getGateType(), trainBatch.size(), valBatch.size());

        return output;
    }

    private EnvAdapter createEnvAdapter(String type) {
        if ("llm-qa".equals(type)) {
            return new QAEnvAdapter();
        }
        if ("search-qa".equals(type)) {
            return new SearchQAEnvAdapter();
        }
        // 默认使用 QA 适配器
        log.warn("[InitJobNode] 未知适配器类型 '{}'，使用 llm-qa", type);
        return new QAEnvAdapter();
    }
}
