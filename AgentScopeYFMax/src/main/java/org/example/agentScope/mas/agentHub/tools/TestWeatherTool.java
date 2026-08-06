package org.example.agentScope.mas.agentHub.tools;


import org.example.agentScope.mas.agentHub.BaseTool;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 2. 具体工具 A：天气查询
 * 加上 @Component，Spring 会自动把它加入容器
 */
@Component
public class TestWeatherTool extends BaseTool {

    @Override
    public String getToolName() {
        return "weather_query"; // 定义我的名字
    }

    @Override
    public String execute(Map<String, Object> input) {
        // 这里可以注入其他 @Service，这是手动 new 做不到的
        return STR."正在查询天气，地点：\{input}";
    }
}
