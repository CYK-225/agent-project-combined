package org.example.agentScope.mas.agentHub;


import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 4. 调度中心（注册表管理器）
 * 所有的脏活累活都在这里，业务代码非常干净
 *
 */
@Service
public class ToolDispatcher {

    // 【核心魔法】
    // Spring 会自动查找所有 BaseTool 的子类，并注入到这个 List 中
    // 你不需要手动 new，也不需要手动 register
    private final List<BaseTool> allTools;

    // 本地缓存 Map，用于快速查找： "name" -> ToolInstance
    private final Map<String, BaseTool> toolRegistry = new HashMap<>();

    public ToolDispatcher(List<BaseTool> allTools) {
        this.allTools = allTools;
    }

    /**
     * 初始化：在构造完成后自动执行
     * 将 List 转为 Map，方便后续 O(1) 快速查找
     */
    @PostConstruct
    public void init() {
        for (BaseTool tool : allTools) {
            if (toolRegistry.containsKey(tool.getToolName())) {
                throw new RuntimeException("重复的工具名称: " + tool.getToolName());
            }
            toolRegistry.put(tool.getToolName(), tool);
            System.out.println(">>> 工具已加载: " + tool.getToolName());
        }
    }

    /**
     * 对外暴露的统一调用方法
     */
    public String dispatch(String toolName, Map<String, Object> input) {
        // 1. 第一尝试：直接根据传入的 toolName 查找
        BaseTool tool = toolRegistry.get(toolName);

        // 2. 降级尝试：如果没找到，且 input 不为空，尝试从 input 中提取 'name'
        if (tool == null && input != null) {
            Object fallbackObj = input.get("name");

            // 【防御性编程】必须判断它是否真的是 String，防止 ClassCastException
            if (fallbackObj instanceof String) {
                String fallbackName = (String) fallbackObj;
                tool = toolRegistry.get(fallbackName);

                // (可选) 建议在这里加一条 debug 日志，方便排查是谁在用这个隐式规则
                // log.debug("Primary tool [{}] not found. Used fallback name [{}] from input.", toolName, fallbackName);
            }
        }

        // 3. 最终校验：如果还是 null，说明两个名字都不对
        if (tool == null) {
            // 优化异常信息，告诉调用者我们尝试了哪些路径
            throw new IllegalArgumentException(
                    String.format("未找到工具: 已尝试主名称 [%s] 及参数中的 name 字段", toolName)
            );
        }

        // 4. 委托执行
        return tool.execute(input);
    }

}
