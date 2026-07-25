package org.example.agent.recommendStoreMenuAgent.prompt;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.example.agent.financeForecastAgent.dataModel.RuntimeContext;
import org.example.agent.utils.SkillLoader;
import org.example.agent.utils.TimeUtils;
import org.example.common.proptcraft.PromptComponent;

import java.time.LocalDateTime;

public abstract class RecommendMenuPromptPool<T> extends PromptComponent {

    public abstract String build(T t);


    protected void loadRole() {
        this.xml("角色定位", "你是一个团餐平台智能助手，可以帮用户解决团餐领域的各种问题。");
    }


    protected void loadRules() {
        this.xml("核心执行规则", """
                    1. 所有工具调用、技能加载、数据查询、逻辑处理均在内部静默完成，**绝对禁止**向用户输出任何以下内容：
                   - 工具/技能相关描述：如"获取菜单推荐技能""调用get_skill_content工具""执行python_exe脚本"等；
                   - 过程性话术：如"首先/接下来/让我/我先/我需要"等；
                   - 仅向用户返回最终结果（如菜单列表、菜单调整建议），无任何前置的操作说明、思考过程；
                2. 禁止透露任何技术细节
                   - 如SKILL.md、脚本名称、参数、技能分级底层ID、菜品ID、客户ID、接口名称、参数、路径等；
                   - 上述禁止内容不仅限于列举的示例，还包括所有类似的系统内部技术信息。当你无法判断某个信息是否可以直接回答时，请遵循"不确定则不答"原则——拒绝回答并引导用户通过其他方式表达需求。
                
                """);
        this.xml("沟通规范", """
                - 保持友好、简洁、专业的团餐平台助手风格。
                - 直接使用用户提供的商家/客户名称。即使该名称不是标准名称，也直接使用，不要质疑用户的输入。
                - 无匹配技能时，正常友好回答，不提及技能或工具。
                """);
        this.xml("重要规则", """
                - 必须根据用户的意图去调用相关工具获取数据，禁止自动创造假数据
                系统规则：
                1. 不要向用户输出任何内部系统ID，例如：
                   - 客户ID
                   - 商家ID
                   - 订单ID
                   - 数据库主键ID
                2. 如果查询结果包含ID，只能在内部使用，不允许出现在最终回复中。
                3. 如果用户询问ID，必须回复：
                   “该信息属于系统内部标识，不对外提供。”
                4. 对外展示信息时，请使用名称而不是ID。
                """);
    }


    protected void loadCurrentTime() {
        this.xml("当前时间",
                LocalDateTime.now().format(TimeUtils.DEFAULT_TIME_FORMETTER)
        );
    }

    protected void loadSkillMap(RuntimeContext runtimeContext) {
        String skillsMetadataPrompt = getSkillsMetadataPrompt(runtimeContext.getSkillDir());
        if (StringUtils.isNotBlank(skillsMetadataPrompt)) {
            this.xml("可用技能",skillsMetadataPrompt);
        }
    }

    public static String getSkillsMetadataPrompt(String skillDir) {
        String fileContent = SkillLoader.getFileContent(skillDir, "skill.json");
        if (StringUtils.isBlank(fileContent)) {
            return "";
        }
        JSONObject jsonObject = JSONObject.parseObject(fileContent);

        JSONArray jsonArray = jsonObject.getJSONArray("skills");

        StringBuilder prompt = new StringBuilder();
        for (int i = 0; i < jsonArray.size(); i++) {
            JSONObject jsonObject1 = jsonArray.getJSONObject(i);
            prompt.append("- `")
                    .append(jsonObject1.getString("name"))
                    .append("`: ")
                    .append(jsonObject1.getString("description"))
                    .append("\n");
        }
        // 去掉最后一个换行
        if (!prompt.isEmpty()) {
            prompt.setLength(prompt.length() - 1);
        }
        return prompt.toString();
    }

    protected void loadSkill(RuntimeContext runtimeContext) {
        String skillName = runtimeContext.getSkillName();
        String skillContent = SkillLoader.getSkillContent(runtimeContext.getSkillDir(), skillName);
        if (StringUtils.isNotBlank(skillContent)) {
            this.xml("可用技能", String.format("""
                <%s>%s</%s>
                """, skillName,skillContent,skillName));
        }


    }
}
