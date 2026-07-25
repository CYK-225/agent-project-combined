package org.example.common.proptcraft.compositePrompt;/**
 * @Auter zzh
 * @Date 2025/10/23
 */

/**
 * @projectName: AItest
 * @package: org.example.aitest.infrastructure.proptcraft.compositePrompt
 * @className: SystemRoleContextPrompt
 * @author: Eric
 * @description: TODO
 * @date: 2025/10/23 22:40
 * @version: 1.0
 */


import org.example.common.proptcraft.PromptComponent;
import org.example.common.proptcraft.PromptComponentEasyFactory;

/**
 * 系统/角色/情境提示 (System/Role/Contextual Prompting) 组件。
 * 这是一个组合组件，用于：
 * - 系统提示 (System): 设置模型的总体行为、目标和约束 [[45]]。
 * - 角色提示 (Role): 指示模型扮演特定角色，如专家、导游等 [[51]]。
 * - 情境提示 (Context): 提供与任务相关的额外背景信息 [[64]]。
 * @author zzh
 */
public class SystemRoleContextPrompt extends PromptComponent implements PromptComponentEasyFactory {


    /**
     * 设置系统级别的指令。这通常放在提示的最前面。
     *
     * @param systemMessage 系统消息。
     * @return 当前实例，用于方法链。
     */
    public SystemRoleContextPrompt system(String systemMessage) {
        prepend(system(systemMessage));
        return this;
    }

    /**
     * 为模型分配一个特定的角色。
     *
     * @param roleDescription 角色描述，例如 "You are an experienced travel guide"。
     * @return 当前实例，用于方法链。
     */
    public SystemRoleContextPrompt role(String roleDescription) {
        prepend(of("ROLE: " + roleDescription));
        return this;
    }

    /**
     * 提供任务相关的上下文信息。
     *
     * @param contextInfo 上下文信息。
     * @return 当前实例，用于方法链。
     */
    public SystemRoleContextPrompt context(String contextInfo) {
        append(of("CONTEXT: " + contextInfo));
        return this;
    }

    /**
     * 设置用户的实际查询。
     *
     * @param query 用户的查询。
     * @return 当前实例，用于方法链。
     */
    public SystemRoleContextPrompt userQuery(String query) {
        append(user(query));
        return this;
    }



    // =============== 使用示例 ===============
    /*
    public static void main(String[] args) {
        String promptText = new SystemRoleContextPrompt()
                .system("You are a helpful assistant.")
                .role("Act as a professional data scientist.")
                .context("The user is working with a dataset about customer churn.")
                .userQuery("How can I improve my model's precision?")
                .render();

        System.out.println(promptText);
    }
    */
}
