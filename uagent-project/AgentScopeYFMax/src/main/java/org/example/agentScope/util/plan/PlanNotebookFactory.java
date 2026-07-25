package org.example.agentScope.util.plan;

import io.agentscope.core.plan.PlanNotebook;
import io.agentscope.core.plan.hint.DefaultPlanToHint;
import io.agentscope.core.plan.hint.PlanToHint;
import io.agentscope.core.plan.model.SubTask;
import io.agentscope.core.plan.model.SubTaskState;
import io.agentscope.core.plan.storage.InMemoryPlanStorage;
import io.agentscope.core.plan.storage.PlanStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
//TODO 使用方法：@Resouce注入
public class PlanNotebookFactory{

    private final PlanNotebookProperties properties;



    public Builder builder() {
        return new Builder(properties);
    }

    // 为了兼容旧代码，保留生成 SubTask 对象的快捷方法
    public SubTask createSubTask(String name, String description, String expectedOutput) {
        return new SubTask(name, description, expectedOutput);
    }

    // ================== 主构建器 ==================
    public static class Builder {
        // 配置属性
        private Integer maxSubTasks;
        private PlanToHint planToHint;
        private PlanStorage planStorage;
        private Boolean needConfirm;

        // 初始化计划相关
        private boolean shouldInitTasks = false;
        private String initName;
        private String initDescription;
        private String initGoal;

        // 核心容器：所有途径添加的任务最终都汇聚到这里
        private final List<SubTask> pendingSubTasks = new ArrayList<>();

        public Builder(PlanNotebookProperties properties) {
            this.maxSubTasks = properties.getDefaultMaxSubtasks();
            this.needConfirm = properties.getDefaultNeedConfirm();
            this.planToHint = new DefaultPlanToHint();
            this.planStorage = new InMemoryPlanStorage();
        }

        public Builder maxSubtasks(Integer maxSubTasks) {
            if (maxSubTasks != null) this.maxSubTasks = maxSubTasks;
            return this;
        }

        public Builder planToHint(PlanToHint planToHint) {
            if (planToHint != null) this.planToHint = planToHint;
            return this;
        }

        public Builder storage(PlanStorage planStorage) {
            if (planStorage != null) this.planStorage = planStorage;
            return this;
        }

        public Builder needConfirm(Boolean needConfirm) {
            if (needConfirm != null) this.needConfirm = needConfirm;
            return this;
        }

        /**
         * 方式 A: 纯流式 - 设置基本信息 (之后通过 .addTask 或 .task() 添加任务)
         */
        public Builder initPlanInfo(String name, String description, String goal) {
            this.shouldInitTasks = true;
            this.initName = name;
            this.initDescription = description;
            this.initGoal = goal;
            return this;
        }

        /**
         * 方式 B: 传统 List - 设置信息并批量传入任务列表
         * (使用 addAll，支持与流式添加混合使用)
         */
        public Builder withInitialPlan(String name, String description, String goal, List<SubTask> predefinedTasks) {
            this.shouldInitTasks = true;
            this.initName = name;
            this.initDescription = description;
            this.initGoal = goal;
            if (predefinedTasks != null && !predefinedTasks.isEmpty()) {
                this.pendingSubTasks.addAll(predefinedTasks);
            }
            return this;
        }

        /**
         * 方式 C: 单个追加 - 快速添加简单任务
         */
        public Builder addTask(String name, String description, String expectedOutput) {
            this.pendingSubTasks.add(new SubTask(name, description, expectedOutput));
            return this;
        }

        /**
         * 方式 D: 嵌套 Builder - 添加复杂任务
         */
        public SubTaskBuilder task() {
            return new SubTaskBuilder(this);
        }

        /**
         * 最终构建逻辑
         */
        public PlanNotebook build() {
            PlanNotebook notebook = PlanNotebook.builder()
                    .maxSubtasks(this.maxSubTasks)
                    .planToHint(this.planToHint)
                    .storage(this.planStorage)
                    .needUserConfirm(this.needConfirm)
                    .build();

            // 只要标记了初始化，或者列表里有东西，就开始初始化流程
            if (this.shouldInitTasks || !this.pendingSubTasks.isEmpty()) {
                initTasksInternal(notebook);
            }
            return notebook;
        }

        private void initTasksInternal(PlanNotebook notebook) {
            // 如果没设置名字，给个默认值防止报错
            String safeName = (initName == null) ? "Unnamed Plan" : initName;

            if (notebook.getCurrentPlan().getSubtasks().isEmpty()) {
                notebook.createPlanWithSubTasks(
                        safeName,
                        initDescription,
                        initGoal,
                        pendingSubTasks // 传入累积的所有任务
                ).block();
            }
            // 将第一个子任务状态设为进行中
            if (!notebook.getCurrentPlan().getSubtasks().isEmpty()) {
                notebook.getCurrentPlan().getSubtasks().getFirst().setState(SubTaskState.IN_PROGRESS);
            }
            //如果上面的方法不生效，则可以尝试把所有任务都设置为进行中
        }
    }

    // ================== 子任务构建器 ==================
    public static class SubTaskBuilder {
        private final Builder parent;
        private String name;
        private String description;
        private String expectedOutput;

        public SubTaskBuilder(Builder parent) { this.parent = parent; }

        public SubTaskBuilder name(String name) { this.name = name; return this; }
        public SubTaskBuilder desc(String description) { this.description = description; return this; }
        public SubTaskBuilder output(String expectedOutput) { this.expectedOutput = expectedOutput; return this; }

        public Builder add() {
            parent.pendingSubTasks.add(new SubTask(this.name, this.description, this.expectedOutput));
            return parent;
        }
    }
}