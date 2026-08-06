package org.example.skillEvolver.agent;

import io.agentscope.core.model.Model;
import io.agentscope.core.skill.SkillBox;
import lombok.extern.log4j.Log4j2;
import org.example.agentScope.framework.annotation.AgentDefinition;
import org.example.agentScope.framework.core.AbstractAgentTemplate;
import org.example.agentScope.framework.core.AgentComponentFacade;
import org.example.skillEvolver.skill.EvolverSkillFactory;
import org.example.skillEvolver.tools.OrchestratorTools;
import org.example.skillEvolver.tools.SkillVaultTools;
import org.example.skillEvolver.tools.TraceAnalyzerTools;
import org.springframework.beans.factory.annotation.Value;

/**
 * Skill 进化主调度 Agent。
 * <p>
 * 执行 SkillEvolver 论文的核心循环：Explore → Analyze → Update。
 * 每轮迭代：
 * <ol>
 *   <li><b>Strategize</b> — 基于 current skill 生成 K 个策略变体</li>
 *   <li><b>Explore</b> — 并行跑 K 个 trial（由 Graph ExploreNode 通过 AgentPoolManager 获取 TrialTestAgent 执行）</li>
 *   <li><b>Analyze</b> — 对比成功/失败 trace，找 Δ（缺失的指导）</li>
 *   <li><b>Update</b> — 合成 v_{k+1} 版本的 SKILL.md</li>
 * </ol>
 * 最终选择最佳版本，验证并持久化。
 *
 * @author zhilin
 */
@Log4j2
@AgentDefinition(
        name = "EvolverOrchestrator",
        enableMemory = true,
        enablePersistence = true,
        maxIters = 80,
        description = "Skill 进化主调度 Agent — 执行 Explore → Analyze → Update 循环迭代优化 SKILL.md"
)
public class EvolverOrchestratorAgent extends AbstractAgentTemplate {

    @org.springframework.beans.factory.annotation.Autowired
    private org.example.skillEvolver.mapper.EvolverTaskMapper taskMapper;

    @org.springframework.beans.factory.annotation.Autowired
    private org.example.skillEvolver.mapper.EvolverSkillVersionMapper versionMapper;

    @Value("${evolver.skill-output-dir:./evolved-skills}")
    private String skillOutputDir;

    public EvolverOrchestratorAgent(AgentComponentFacade components) {
        super(components);
    }

    @Override
    protected String setupSysPrompt() {
        return """
                你是 SkillEvolver — 一个自动进化 Agent Skill 的元技能系统。
                
                ## 核心使命
                通过反复的 **Explore → Analyze → Update** 循环，迭代优化一个可复用的 SKILL.md，
                使其在目标任务上的表现超越人工编写的 skill。
                
                ## 循环结构
                每轮迭代 k = 0, 1, ..., R-1 执行相同的三阶段：
                
                ### 阶段 1: Strategize + Explore（策略生成 + 探索）
                1. 读取当前 skill 状态（第一轮为空，后续轮读取上一轮的 v_{k-1}）
                2. 生成 K 个策略变体（每个变体是一个独立的 SKILL.md 草稿）
                   - 变体之间必须有明确的差异（不同约束、不同侧重点、不同方法论）
                   - 可以保留上一轮最佳版本的核心，但必须尝试新的方向
                3. 为每个变体创建独立的工作区
                4. 对每个变体执行 trial（调用 trial runner 或通过 graph 工作流）
                5. 收集 trial 结果：pass/fail + reward + trace
                
                ### 阶段 2: Analyze Traces（差异分析）
                1. 将 trial 结果分为成功组和失败组
                2. 调用 analyze_trial_traces 进行结构化对比
                3. 识别关键差异：成功 trace 有什么，失败 trace 缺什么
                4. 找出当前 skill 的盲点（遗漏的指导）和冗余（导致弯路的指导）
                
                ### 阶段 3: Update Skill（更新 Skill）
                1. 基于分析结论，合成 v_{k+1} 版本
                2. 保留有效指导，补充缺失指导，删除导致弯路的指导
                3. 新版本必须包含：原子操作、失败教训、成功对比、条件决策规则
                4. 保存版本快照
                
                ### 最终阶段: Select Best + Validate（选择 + 验证）
                1. 比较所有迭代的版本，选择综合表现最佳的
                2. 奖励模式 discrete → 看通过率；continuous → 看平均奖励
                3. 对最佳版本运行 N 次验证 trial（使用未见过的数据）
                4. 持久化到文件系统（兼容 LocalSkillLoader）
                
                ## 关键原则
                - **每轮必须做新的探索**，不能重跑上一轮完全相同的 skill
                - **Skill 是可复用的知识制品**，不是任务本地的实现笔记
                - **避免硬编码**：Skill 不应包含特定任务的文件名、路径、具体数值
                - **保留有效的、补充缺失的、删除有害的**
                - 完成后输出 "PIPELINE COMPLETE" 并结束
                
                ## 奖励信号
                - `discrete` 模式：通过率是主要信号，奖励是辅助
                - `continuous` 模式：平均奖励是主要信号，通过率是辅助
                """;
    }

    @Override
    protected Model setupCustomModel() {
        return components.model().dashScope().buildDashScopeModel();
    }

    @Override
    protected SkillBox setupSkills() {
        // --- 构造 OrchestratorTools 的函数式依赖 ---
        OrchestratorTools.TaskReader taskReader =
                (taskId) -> taskMapper.selectOneById(taskId);

        OrchestratorTools.TaskUpdater taskUpdater =
                (taskId, status, iteration) -> {
                    org.example.skillEvolver.entity.EvolverTaskEntity entity =
                            taskMapper.selectOneById(taskId);
                    if (entity == null) return false;
                    entity.setStatus(status);
                    if (iteration != null) entity.setCurrentIteration(iteration);
                    entity.setUpdatedAt(java.time.LocalDateTime.now());
                    return taskMapper.update(entity) > 0;
                };

        OrchestratorTools.VersionSaver versionSaver =
                (entity) -> {
                    versionMapper.insert(entity);
                    return entity;
                };

        OrchestratorTools.VersionReader versionReader =
                (taskId) -> {
                    com.mybatisflex.core.query.QueryWrapper qw =
                            com.mybatisflex.core.query.QueryWrapper.create()
                                    .where(org.example.skillEvolver.entity.EvolverSkillVersionEntity::getTaskId)
                                    .eq(taskId)
                                    .orderBy(org.example.skillEvolver.entity.EvolverSkillVersionEntity::getIteration, true)
                                    .orderBy(org.example.skillEvolver.entity.EvolverSkillVersionEntity::getVariantIndex, true);
                    return versionMapper.selectListByQuery(qw);
                };

        OrchestratorTools.BestSkillUpdater bestSkillUpdater =
                (taskId, versionId, content, reward, passRate) -> {
                    org.example.skillEvolver.entity.EvolverTaskEntity entity =
                            taskMapper.selectOneById(taskId);
                    if (entity == null) return false;
                    entity.setBestSkillVersion(versionId);
                    entity.setBestSkillContent(content);
                    entity.setBestReward(reward);
                    entity.setValidationPassRate(passRate);
                    entity.setUpdatedAt(java.time.LocalDateTime.now());
                    return taskMapper.update(entity) > 0;
                };

        // --- 构造 SkillVaultTools ---
        SkillVaultTools skillVaultTools = new SkillVaultTools(
                () -> skillOutputDir
        );

        // --- 构造 TraceAnalyzerTools ---
        TraceAnalyzerTools traceAnalyzerTools = new TraceAnalyzerTools();

        // --- 注册到 SkillBox ---
        OrchestratorTools orchestratorTools = new OrchestratorTools(
                taskReader, taskUpdater, versionSaver, versionReader, bestSkillUpdater
        );

        return components.skillBox().create(getToolkit())
                .addSkillWithTools(
                        EvolverSkillFactory.createOrchestratorSkill(),
                        new Object[]{orchestratorTools, traceAnalyzerTools, skillVaultTools}
                )
                .buildSkillBox();
    }
}
