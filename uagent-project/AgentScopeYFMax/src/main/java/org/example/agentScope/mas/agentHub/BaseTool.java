package org.example.agentScope.mas.agentHub;


import java.util.Map;

/**
 * 1. 抽象父类
 * 定义所有工具必须遵守的规范
 */
public abstract class BaseTool {

    /**
     * 获取工具的唯一标识符（注册名）
     * 子类必须实现这个方法，告诉中心它是谁
     */
    public abstract String getToolName();

    /**
     * 统一的执行入口
     * @param  input JSON 格式或字符串格式的参数
     * @return 执行结果
     */
    public abstract String execute(Map<String, Object> input);
}
