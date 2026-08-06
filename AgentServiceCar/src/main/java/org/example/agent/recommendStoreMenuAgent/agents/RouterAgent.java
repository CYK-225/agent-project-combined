package org.example.agent.recommendStoreMenuAgent.agents;


import io.agentscope.core.ReActAgent;
import io.agentscope.core.formatter.dashscope.DashScopeChatFormatter;
import io.agentscope.core.model.DashScopeChatModel;
import org.example.agentScope.util.modelFactory.DashScopeModelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouterAgent {

    @Value("${qwen.apiKey}")
    private String qwen_api_key;

    @Bean("MenuRecommendRouterAgent")
    public ReActAgent createMenuRecommendRouterAgent() {
        String routerPrompt = """
                你是一个技能路由器，需要根据用户问题选择最合适的技能。

                可用技能：

                1. search_merchant_Info
                用途：商家信息查询技能，当用户想要查询某个商家的详细信息（如地址、负责人、联系电话、上线时间、好吃top菜品、现场图片、商家近期评价分析等）时，调用该技能。

                2. recommend_menu
                用途：团餐菜单推荐技能，当用户提到为某一个商家推荐/设计/生成菜单时，调用该技能。调用该技能时，严格按照SKILL.md中的输出要求进行输出。注意，若当前对话中已经存在当前商家的菜单信息，则无需重复推荐，直接根据当前菜单信息进行调整即可，除非用户明确要求重新推荐。
                
                2.data_analyze
                用途：智能数据分析技能，当用户想要查询/分析/统计某个商家或客户的供餐数据、订单情况、销量、营业额、评价、投诉等经营数据时调用，
                     例如"帮我分析XX的供餐数据"、"查下XX这个客户3月第一周的订单"、"看看XX商家最近的销量怎么样"、"统计一下XX公司的投诉情况"等。

                规则：
                只返回技能名称，不要解释。

                示例：
                用户：查询海底捞信息
                输出：search_merchant_Info

                用户：帮我推荐海底捞下周菜单
                输出：recommend_menu
                
                用户：帮我查询下数据
                输出：data_analyze
                """;
        return ReActAgent.builder()
                .name("SkillRouter")
                .sysPrompt(routerPrompt)
                .model(
                        DashScopeChatModel.builder()
                                .apiKey(qwen_api_key)
                                .modelName("qwen-plus")
                                .stream(true)
                                .enableThinking(false)
                                .formatter(new DashScopeChatFormatter())
                                .build()).build();

    }

}
