package org.example.dbagent.agent;

import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.agentScope.framework.annotation.AgentDefinition;
import org.example.agentScope.framework.core.AbstractAgentTemplate;
import org.example.agentScope.framework.core.AgentComponentFacade;
import org.example.dbagent.prompt.CodeGenPrompt;
import org.example.dbagent.prompt.CodeGenPromptFactory;
import org.example.dbagent.tool.FileWriteTool;
import org.springframework.stereotype.Component;

/**
 * DB代码生成Agent
 * 负责根据表结构生成Entity、Mapper、Service代码并写入文件
 * 支持通过参数动态切换框架模板（mybatis-flex, mybatis-plus等）
 */
@Slf4j
@AgentDefinition(
        name = "DBCodeGen",
        description = "数据库代码生成Agent，根据数据库表结构生成DAO层代码，支持MyBatis-Flex和MyBatis-Plus框架",
        modelType = "工具",
        group = "dbagent",
        scope = "prototype"
)
@Component
public class DBCodeGenAgent extends AbstractAgentTemplate {

    /** 默认框架 */
    private static final String DEFAULT_FRAMEWORK = "mybatis-flex";

    /** 当前使用的框架（可通过skillName参数动态设置） */
    private String currentFramework = DEFAULT_FRAMEWORK;

    protected DBCodeGenAgent(AgentComponentFacade components) {
        super(components);
    }

    /**
     * 默认系统提示词（使用默认框架）
     */
    @Override
    protected String setupSysPrompt() {
        return setupSysPrompt(DEFAULT_FRAMEWORK);
    }

    /**
     * 根据框架名称动态生成系统提示词
     * @param framework 框架名称（如 mybatis-flex, mybatis-plus）
     */
    @Override
    protected String setupSysPrompt(String framework) {
        // 如果传入的不是框架名称，使用默认框架
        if (framework == null || framework.isBlank() || !CodeGenPromptFactory.isSupported(framework)) {
            framework = DEFAULT_FRAMEWORK;
        }

        this.currentFramework = framework;
        log.info("使用框架模板: {}", framework);

        // 从工厂获取对应的提示词组件
        CodeGenPrompt prompt = CodeGenPromptFactory.getPrompt(framework);

        // 构建完整的系统提示词
        return prompt.buildFullPrompt();
    }

    @Override
    protected Model setupCustomModel() {
        return components.model().dashScope().buildDashScopeModel("工具");
    }

    @Override
    protected Toolkit setupTools() {
        return components.toolkit().create()
                .addTools(new FileWriteTool(new ObjectMapper()))
                .build();
    }

    /**
     * 获取当前使用的框架名称
     */
    public String getCurrentFramework() {
        return currentFramework;
    }
}
