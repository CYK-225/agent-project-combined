package org.example.skillOpt.skill;

import io.agentscope.core.skill.AgentSkill;

import static org.example.agentScope.util.skill.BaseSkillBoxFactory.createBaseAgentSkill;

/**
 * SkillOpt Skill 工厂。
 *
 * @author zhilin
 */
public class SkillOptSkillFactory {

    private SkillOptSkillFactory() {
    }

    /**
     * 创建 SkillOpt 推理 Skill
     */
    public static AgentSkill createReasonerSkill() {
        return createBaseAgentSkill(
                "skillopt-reasoner",
                "SkillOpt Reasoner Skill",
                "Skill for Reflect/Aggregate/Select/Update/MetaSkill reasoning tasks in SkillOpt training pipeline.");
    }

    /**
     * 创建 SkillOpt Target Skill
     */
    public static AgentSkill createTargetSkill() {
        return createBaseAgentSkill(
                "skillopt-target",
                "SkillOpt Target Agent Skill",
                "Execute rollout tasks following skill guidance for SkillOpt training.");
    }
}
