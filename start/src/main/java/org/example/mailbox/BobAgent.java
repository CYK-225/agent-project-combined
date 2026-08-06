package org.example.mailbox;

import io.agentscope.core.model.Model;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.tool.Toolkit;
import org.example.agentScope.framework.annotation.AgentDefinition;
import org.example.agentScope.framework.core.AbstractAgentTemplate;
import org.example.agentScope.framework.core.AgentComponentFacade;
import org.example.agentScope.mas.mailbox.skill.MailSkillFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试 Agent Bob — 用于测试邮件系统
 */
@AgentDefinition(name = "MailBob", enableMail = true, enablePersistence = true, modelType = "工具",hooksType =  "log")
public class BobAgent extends AbstractAgentTemplate {

    public BobAgent(AgentComponentFacade components) {
        super(components);
    }

    @Override
    protected String setupSysPrompt() {
        return """
                你是 Bob，一个测试 Agent。
                你拥有邮件通信能力，可以和其他 Agent 收发邮件。
                
                规则：
                1. 收到邮件后，请用 reply_mail 回复确认
                2. 发送邮件前先用 list_agents 查询可用的 Agent
                3. 回复要简短，不超过一句话
                """;
    }

    @Override
    protected Model setupCustomModel() {
        return components.model().dashScope().buildDashScopeModel();
    }

    @Override
    protected Toolkit setupTools() {
        return components.toolkit().create()
                .addTools(MailSkillFactory.createTools(getMailboxState(), components.mailboxCenter(), getAgentPoolManager(), getThreadID()))
                .build();
    }

    @Override
    protected SkillBox setupSkills() {
        return components.skillBox().create(getToolkit())
                .addSkillWithTools(
                        MailSkillFactory.createSkill(),
                        MailSkillFactory.createTools(getMailboxState(), components.mailboxCenter(), getAgentPoolManager(), getThreadID())
                )
                .buildSkillBox();
    }

    @Override
    protected List<io.agentscope.core.hook.Hook> setupCustomHooks() {
        List<io.agentscope.core.hook.Hook> hooks = new ArrayList<>(super.setupCustomHooks());
        if (getMailboxState() != null) {
            hooks.add(MailSkillFactory.createHook(getMailboxState(), components.mailboxCenter(), getThreadID()));
        }
        return hooks;
    }
}
