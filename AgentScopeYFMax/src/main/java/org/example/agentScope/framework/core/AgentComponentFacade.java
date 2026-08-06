package org.example.agentScope.framework.core;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import org.example.agentScope.mas.mailbox.core.MailboxCenter;
import org.example.agentScope.util.hooksManager.HookListFactory;
import org.example.agentScope.util.memory.AutoContextMemoryFactory;
import org.example.agentScope.util.modelFactory.ModelFactoryFacade;
import org.example.agentScope.util.plan.PlanNotebookFactory;
import org.example.agentScope.util.session.PostgresSession;
import org.example.agentScope.util.skill.BaseSkillBoxFactory;
import org.example.agentScope.util.tool.ToolkitFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Getter // 使用 Lombok 自动生成 getter 方法
@Accessors(fluent = true) // 开启流式访问，getter 方法名将不再有 get 前缀（例如直接使用 toolkit() 而不是 getToolkit()）
public class AgentComponentFacade {
    private final ToolkitFactory toolkit;
    private final AutoContextMemoryFactory memory;
    private final PlanNotebookFactory plan;
    private final HookListFactory hooks;
    private final BaseSkillBoxFactory skillBox;
    private final ModelFactoryFacade model;
    private final PostgresSession postgresSession;
    private final MailboxCenter mailboxCenter;
    private final AgentPoolManager agentPoolManager;
    private final SkillRepoRegistry skillRepoRegistry;
}
