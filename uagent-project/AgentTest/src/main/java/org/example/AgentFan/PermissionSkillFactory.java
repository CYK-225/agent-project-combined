package org.example.AgentFan;

import io.agentscope.core.skill.AgentSkill;

import static org.example.agentScope.util.skill.BaseSkillBoxFactory.createBaseAgentSkill;

/**
 * 权限注入 Skill + Tool 工厂
 * <p>
 * 纯静态工厂，产出 {@code AgentSkill + Object[]}，
 * 由 Agent Template 的 {@code addSkillWithTools} 消费。
 */
public class PermissionSkillFactory {

    private PermissionSkillFactory() {
    }

    // ======================== Permission Skill ========================

    public static AgentSkill createPermissionSkill() {
        return createBaseAgentSkill(
                "permission_injection",
                "权限注入与 SQL 过滤",
                "你是权限注入 Agent，负责根据用户 token 获取公司和商户权限，并在 SQL 查询中注入过滤条件。"
        );
    }

    public static Object[] createPermissionTools() {
        return new Object[]{new PermissionTools()};
    }
}
