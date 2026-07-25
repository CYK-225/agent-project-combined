package org.example.skillEvolver.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

/**
 * Skill 版本快照实体。
 * <p>
 * 每一轮迭代产出的 SKILL.md 版本，含 trial 结果摘要，
 * 供 trace 分析和最终版本选择使用。
 *
 * @author zhilin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(value = "evolver_skill_version", schema = "agent_test")
public class EvolverSkillVersionEntity {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private String versionId;

    /** 所属进化任务 ID */
    private String taskId;

    /** 迭代号（从 0 开始） */
    private Integer iteration;

    /** 版本标签（如 v0, v1, v2） */
    private String versionLabel;

    /** 策略变体序号（同轮内多个变体用 1,2,3... 区分，最终合并版为 0） */
    @Builder.Default
    private Integer variantIndex = 0;

    /** SKILL.md 内容 */
    @Column(value = "skill_markdown")
    private String skillMarkdown;

    /** 该版本在探索中的通过率（0.0~1.0） */
    private Double passRate;

    /** 该版本的平均奖励分数 */
    private Double meanReward;

    /** 成功 trace 摘要（JSON 数组） */
    @Column(value = "success_traces", jdbcType = JdbcType.VARCHAR)
    private String successTraces;

    /** 失败 trace 摘要（JSON 数组） */
    @Column(value = "failure_traces", jdbcType = JdbcType.VARCHAR)
    private String failureTraces;

    /** 分析结论（LLM 对成功 vs 失败的差异分析） */
    @Column(value = "analysis")
    private String analysis;

    /** 是否为最终选定的最佳版本 */
    @Builder.Default
    private Boolean isBest = false;

    private LocalDateTime createdAt;
}
