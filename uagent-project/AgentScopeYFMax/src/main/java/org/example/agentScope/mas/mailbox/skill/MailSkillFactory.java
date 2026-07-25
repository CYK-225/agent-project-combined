package org.example.agentScope.mas.mailbox.skill;

import io.agentscope.core.skill.AgentSkill;
import org.example.agentScope.framework.core.AgentPoolManager;
import org.example.agentScope.mas.mailbox.core.MailboxCenter;
import org.example.agentScope.mas.mailbox.core.MailboxState;
import org.example.agentScope.mas.mailbox.hook.MailHook;
import org.example.agentScope.mas.mailbox.tool.MailTools;
import org.example.agentScope.util.skill.BaseSkillBoxFactory;

/**
 * 邮件技能工厂 — 纯静态工具类，适配 addSkillWithTools
 * <p>
 * <b>遵循能力层设计规范</b>：不自建 Toolkit / SkillBox，产出 AgentSkill + 工具对象，
 * 由 Agent Template 的 {@code addSkillWithTools} 消费。
 * <p>
 * <h3>使用方式（在 AbstractAgentTemplate 子类中）</h3>
 * <pre>{@code
 * @AgentDefinition(name = "Alice", enableMail = true)
 * public class AliceAgent extends AbstractAgentTemplate {
 *
 *     @Override
 *     protected SkillBox setupSkills() {
 *         return skillBoxFactory.create()
 *             .addSkillWithTools(
 *                 MailSkillFactory.createSkill(),
 *                 MailSkillFactory.createTools(getMailboxState(), getMailboxCenter(), getAgentPoolManager(), getThreadID())
 *             )
 *             .buildSkillBox();
 *     }
 *
 *     @Override
 *     protected List<Hook> setupCustomHooks() {
 *         List<Hook> hooks = new ArrayList<>(super.setupCustomHooks());
 *         if (getMailboxState() != null) {
 *             hooks.add(MailSkillFactory.createHook(getMailboxState(), getMailboxCenter(), getThreadID()));
 *         }
 *         return hooks;
 *     }
 * }
 * }</pre>
 *
 * @see MailTools
 * @see MailHook
 * @see BaseSkillBoxFactory.SkillBoxBuilder#addSkillWithTools
 */
public class MailSkillFactory {

    private MailSkillFactory() {} // 工具类禁止实例化

    // ======================== createSkill ========================

    /**
     * 创建邮件通信技能描述（纯静态，无运行时依赖）
     * <p>
     * LLM 通过这个 Skill 知道自己有邮件通信能力。
     */
    public static AgentSkill createSkill() {
        return BaseSkillBoxFactory.createBaseAgentSkill(
                "mail_communication",
                "邮件通信工具集",
                MAIL_SKILL_PROMPT
        );
    }

    // ======================== createTools ========================

    /**
     * 创建邮件工具实例（@Tool POJO，需要运行时参数）
     * <p>
     * 返回的 {@code MailTools} 对象会被 {@code addSkillWithTools} 的 default 分支
     * 通过反射扫描 {@code @Tool} 注解方法注册。
     *
     * @param mailboxState  该 Agent 的内存邮箱
     * @param mailboxCenter 中心化邮局单例
     * @param agentPoolManager Agent 池管理器（用于查询可通信的 Agent）
     * @param threadId      当前会话 ID
     * @return 工具对象数组（传给 addSkillWithTools 的第二个参数）
     */
    public static Object[] createTools(MailboxState mailboxState,
                                       MailboxCenter mailboxCenter,
                                       AgentPoolManager agentPoolManager,
                                       String threadId) {
        return new Object[]{new MailTools(mailboxState, mailboxCenter, agentPoolManager, threadId)};
    }

    // ======================== createHook ========================

    /**
     * 创建邮件 Hook — 覆盖 Agent 完整生命周期：注册 → 通知 → 注销
     * <p>
     * <ul>
     *   <li>handlePreCall  — 注册到 MailboxCenter，从 DB 恢复历史邮件</li>
     *   <li>handlePreReasoning — 通知未读邮件</li>
     *   <li>handlePostCall  — 注销并同步状态到 DB</li>
     * </ul>
     *
     * @param mailboxState  该 Agent 的内存邮箱
     * @param mailboxCenter 中心化邮局单例
     * @param threadId      当前会话 ID
     * @return MailHook 实例（传给 setupCustomHooks）
     */
    public static MailHook createHook(MailboxState mailboxState,
                                      MailboxCenter mailboxCenter,
                                      String threadId) {
        return new MailHook(mailboxState, mailboxCenter, threadId);
    }

    // ======================== Skill Prompt ========================

    private static final String MAIL_SKILL_PROMPT = """
            ## 📬 邮件通信能力

            你拥有团队邮件系统，可以与其他 Agent 进行异步通信。
            即使对方当前不在线，邮件也会被自动保存并在其上线后送达。

            ### 工作方式
            系统会在你每轮推理前通知未读邮件数量，但不会自动展示内容。
            你需要主动使用工具查看和处理邮件。

            ### 可用工具
            - `list_agents`: 获取当前会话中可通信的 Agent 列表（在线/离线状态）
            - `check_mailbox`: 查看收件箱所有邮件的列表（ID、发件人、主题、状态）
            - `read_mail`: 读取指定邮件的完整内容（自动标记已读）
            - `send_mail`: 向其他 Agent 发送邮件（参数: to, subject, body）
            - `reply_mail`: 回复收到的邮件（参数: mailId, body，自动反转收发方向）
            - `mark_mail_read`: 标记邮件已读（参数: mailId）

            ### 使用规则
            1. 发送邮件前，先用 list_agents 查询可通信的 Agent
            2. 每轮推理前，系统会通知你有几封未读邮件（只显示 ID + 发件人 + 主题）
            3. 使用 check_mailbox 查看收件箱列表
            4. 使用 read_mail 阅读感兴趣的邮件（会自动标记已读）
            5. 需要回复时使用 reply_mail
            6. 不感兴趣的邮件可以用 mark_mail_read 标记已读，避免反复提醒

            ### 通信礼仪
            - 主题要简洁明确
            - 正文要说清楚需求或反馈
            - 收到邮件后及时处理
            """;
}
