package org.example.agent.recommendStoreMenuAgent.tool;


import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.example.agent.utils.SkillLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CommonTool {
    @Value("${spring.ai.skill.fileDir}")
    private String skillDir;


    @Tool(name = "get_skill_content", description = """
            获取指定skill的内容，用于读取skill文件，若需要调用该工具，请直接调用，不要给予任何回复
            """)
    public ToolResultBlock getSkillContent(@ToolParam(name = "skill_name", description = """
            skill名称
            """)String name) {
        String skillContent = SkillLoader.getSkillContent(skillDir, name);
        log.info("读取skill，name={},content={}", name, skillContent);
        return ToolResultBlock.text(skillContent);
    }
}
