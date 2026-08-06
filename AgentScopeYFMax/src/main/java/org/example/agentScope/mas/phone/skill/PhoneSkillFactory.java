package org.example.agentScope.mas.phone.skill;

import io.agentscope.core.skill.AgentSkill;
import org.example.agentScope.mas.phone.tool.*;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 手机操控能力工厂 — 产出 AgentSkill + 工具数组，适配 addSkillWithTools
 * <p>
 * <h3>设计思路</h3>
 * 手机操控工具（点击/滑动/输入/截屏）由外部容器执行，框架工具只触发挂起等待容器回调。
 * <p>
 * <h3>使用方式（在 Agent Template 的 setupSkills 中）</h3>
 * <pre>{@code
 * @Override
 * protected SkillBox setupSkills() {
 *     return skillBoxFactory.create()
 *         .addSkillWithTools(
 *             PhoneSkillFactory.createSkill(),
 *             PhoneSkillFactory.createTools()
 *         )
 *         .buildSkillBox();
 * }
 * }</pre>
 */
public final class PhoneSkillFactory {

    private PhoneSkillFactory() {} // 禁止实例化

    /** 手机操控技能描述 — 告诉 LLM "你有操控手机的能力" */
    private static final String PHONE_SKILL_PROMPT = """
            ## 📱 手机操控能力

            你可以通过工具操控手机屏幕，完成用户指定的任务。

            ### 可用工具
            - `gui_click`: 点击屏幕指定坐标（参数: x, y）
            - `gui_swipe`: 滑动屏幕（参数: startX, startY, endX, endY, duration）
            - `gui_input`: 输入文字（参数: text）
            - `gui_screenshot`: 截取当前屏幕

            ### 使用规则
            1. 每次操作前你会看到当前屏幕截图，根据截图内容决定下一步操作
            2. 点击时请尽量精确坐标，参考屏幕上元素的位置
            3. 滑动时 duration 单位为毫秒，建议 300-500ms
            4. 输入文字前确保焦点在正确的输入框中
            5. 操作后系统会自动截取新截图，你可以根据新截图继续操作
            6. 完成任务后，向用户报告结果
            """;

    // ======================== 工厂方法 ========================

    /**
     * 创建手机操控技能描述
     */
    public static AgentSkill createSkill() {
        return AgentSkill.builder()
                .name("phone_control")
                .description("手机屏幕操控能力：点击、滑动、输入、截屏")
                .skillContent(PHONE_SKILL_PROMPT)
                .build();
    }

    /**
     * 创建手机操控工具数组
     * <p>
     * 返回 {@code Object[]}，由 {@code addSkillWithTools(AgentSkill, Object...)} 内部
     * 反射扫描 {@code @Tool} 注解自动注册。
     */
    public static Object[] createTools() {
        return new Object[]{
                new HrTools()
        };
    }
}
