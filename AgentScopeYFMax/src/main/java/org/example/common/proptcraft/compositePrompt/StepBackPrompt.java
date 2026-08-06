package org.example.common.proptcraft.compositePrompt;/**
 * @Auter zzh
 * @Date 2025/10/23
 */

/**
 * @projectName: AItest
 * @package: org.example.aitest.infrastructure.proptcraft.compositePrompt
 * @className: StepBackPrompt
 * @author: Eric
 * @description: TODO
 * @date: 2025/10/23 22:41
 * @version: 1.0
 */


import org.example.common.proptcraft.PromptComponent;
import org.example.common.proptcraft.PromptComponentEasyFactory;

/**
 * 后退提示 (Step-Back Prompting) 组件。
 * 通过将复杂请求分解为更简单的步骤来提高准确性。
 * 第一步：获取与问题相关的高级概念或背景知识。
 * 第二步：将这些知识应用到具体问题上 [[72]]。
 */
public class StepBackPrompt extends PromptComponent implements PromptComponentEasyFactory {

    /**
     * 定义第一步：获取高级背景知识的问题。
     *
     * @param question 获取背景知识的问题。
     * @return 当前实例，用于方法链。
     */
    public StepBackPrompt highLevelQuestion(String question) {
        append(of("Step 1: " + question));
        return this;
    }

    /**
     * 定义第二步：将第一步的答案作为上下文，应用到具体任务中。
     * 在 `specificTask` 字符串中使用 `{step-back}` 作为占位符。
     *
     * @param specificTask 具体任务描述，包含 `{step-back}` 占位符。
     * @param stepBackAnswer 第一步得到的答案。
     * @return 当前实例，用于方法链。
     */
    public StepBackPrompt applyToSpecific(String specificTask, String stepBackAnswer) {
        append(of("Step 2: " + specificTask.replace("{step-back}", stepBackAnswer)));
        return this;
    }



    // =============== 使用示例 ===============
    /*
    public static void main(String[] args) {
        // 假设第一步已经执行并得到了答案
        String stepBackAnswer = "Key settings in FPS games include abandoned cities, military bases, alien planets, etc.";

        String promptText = new StepBackPrompt()
                .highLevelQuestion("What are common fictional settings in first-person shooter games?")
                .applyToSpecific(
                        "Write a short story for a new FPS level set in {step-back}. Make it engaging.",
                        stepBackAnswer
                )
                .render();

        System.out.println(promptText);
    }
    */
}
