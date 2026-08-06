package org.example.agents.UserQuestion;

import io.agentscope.core.model.Model;
import lombok.EqualsAndHashCode;
import org.example.agentScope.framework.annotation.AgentDefinition;
import org.example.agentScope.framework.core.AbstractAgentTemplate;
import org.example.agentScope.framework.core.AgentComponentFacade;
import org.example.common.proptcraft.compositePrompt.ZeroShotPrompt;
import org.springframework.stereotype.Component;


@EqualsAndHashCode(callSuper = true)
@AgentDefinition("UserQuestion")
@Component
public class UserQuestion extends AbstractAgentTemplate {

    protected UserQuestion(AgentComponentFacade components) {
        super(components);
    }

    @Override
    protected String setupSysPrompt() {
        return buildChatTitleGeneratorPrompt();
    }

    @Override
    protected Model setupCustomModel() {
        return components.model().dashScope().buildDashScopeModel("聊天");
    }
    private String buildChatTitleGeneratorPrompt() {
        return new ZeroShotPrompt()
                // 1. 明确身份：极简对话标题生成器
                .system("你是一个对话标题生成助手。你的唯一职责是：根据用户的首条输入（第一句话），快速、精准地生成一个高度概括的简短对话标题。")

                // 2. 思维链（CoT）：指导模型如何思考和提炼
                .xml("思维链(CoT)",
                        "在生成标题前，请遵循以下步骤进行内在思考（无需输出思考过程）：\n" +
                                "1. 【意图分析】：判断用户的输入是提问、求写代码、文本翻译、还是日常闲聊？\n" +
                                "2. 【实体提取】：找出句子中的核心名词、动词、专有名词或场景（如“Java”、“前端报错”、“年度总结”、“翻译”）。\n" +
                                "3. 【降噪浓缩】：剔除所有无意义的客套话、代词和语气词（如“帮我”、“请问”、“你好”），将核心意图压缩成一个最精炼的短语。")

                // 3. 交付约束：确保直接输出干练的结果，无任何废话
                .rule("交付物规范（StrictConstraints）",
                        "🔴 极简长度：标题长度必须严格控制在 2 到 15 个字符之间。\n" +
                                "🔴 短语结构：必须是名词性短语或动宾短语，绝不能是完整的句子（例如：应输出“Java线程池配置”，禁止输出“如何配置Java线程池”）。\n" +
                                "🔴 禁止标点：标题末尾绝对禁止出现句号、问号、感叹号等任何标点符号。\n" +
                                "🔴 纯净输出：严禁输出任何多余的解释、寒暄或前缀（如“好的”、“标题是”）。你的回复只能包含标题文本本身。")

                // 4. 触发
                .of("确认状态。现在请接收用户的首条输入，直接返回生成的标题。")
                .render();
    }
}
