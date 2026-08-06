package org.example.dbagent.prompt;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 代码生成提示词工厂
 * 根据框架名称创建对应的提示词组件
 */
public class CodeGenPromptFactory {

    private static final Map<String, CodeGenPrompt> PROMPT_MAP = new HashMap<>();

    static {
        // 注册内置的提示词组件
        registerPrompt("mybatis-flex", new MyBatisFlexPrompt());
        registerPrompt("mybatis-plus", new MyBatisPlusPrompt());
    }

    /**
     * 注册提示词组件
     */
    public static void registerPrompt(String framework, CodeGenPrompt prompt) {
        PROMPT_MAP.put(framework.toLowerCase(), prompt);
    }

    /**
     * 获取提示词组件
     * @param framework 框架名称（如 mybatis-flex, mybatis-plus）
     * @return 提示词组件，如果未找到返回默认的 mybatis-flex
     */
    public static CodeGenPrompt getPrompt(String framework) {
        if (framework == null || framework.isBlank()) {
            return PROMPT_MAP.get("mybatis-flex");
        }
        CodeGenPrompt prompt = PROMPT_MAP.get(framework.toLowerCase());
        return prompt != null ? prompt : PROMPT_MAP.get("mybatis-flex");
    }

    /**
     * 获取所有支持的框架名称
     */
    public static Set<String> getSupportedFrameworks() {
        return PROMPT_MAP.keySet();
    }

    /**
     * 检查是否支持指定框架
     */
    public static boolean isSupported(String framework) {
        if (framework == null) return false;
        return PROMPT_MAP.containsKey(framework.toLowerCase());
    }
}
