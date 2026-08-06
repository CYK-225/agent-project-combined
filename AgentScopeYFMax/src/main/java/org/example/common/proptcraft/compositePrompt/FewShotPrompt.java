package org.example.common.proptcraft.compositePrompt;/**
 * @Auter zzh
 * @Date 2025/10/23
 */

/**
 * @projectName: AItest
 * @package: org.example.aitest.infrastructure.proptcraft.compositePrompt
 * @className: FewShotPrompt
 * @author: Eric
 * @description: TODO
 * @date: 2025/10/23 22:40
 * @version: 1.0
 */

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.example.common.proptcraft.PromptComponent;


/**
 * 少样本提示 (Few-Shot Prompting) 组件。
 * 通过提供一个或多个输入-输出对的示例，帮助模型理解任务模式和期望的输出格式。
 * 对于需要特定格式或任务定义可能含糊不清的情况特别有效 [[31]]。
 * @author zzh
 */
@Data
@Builder
@AllArgsConstructor
public class FewShotPrompt extends PromptComponent {


    /**
     * 添加一个或多个示例。每个示例应包含输入和期望的输出。
     *
     * @param examples 示例字符串数组。
     * @return 当前实例，用于方法链。
     */
    public FewShotPrompt examples(String... examples) {
        for (String example : examples) {
            append(of(example));
        }
        return this;
    }

    /**
     * 设置新的、需要模型处理的输入。
     *
     * @param input 新的输入文本。
     * @return 当前实例，用于方法链。
     */
    public FewShotPrompt newInput(String input) {
        append(of("Now, " + input));
        return this;
    }



    // =============== 使用示例 ===============
    /*
    public static void main(String[] args) {
        String example1 = """
                Input: I want a small pizza with cheese end pepperoni.
                Output: {"size": "small", "ingredients": ["cheese", "pepperoni"]}
                """;
        String example2 = """
                Input: Can I get a large vegetarian pizza?
                Output: {"size": "large", "ingredients": ["vegetables"]}
                """;

        String promptText = new FewShotPrompt()
                .examples(example1, example2)
                .newInput("I'd like a medium pizza with mushrooms end olives.")
                .render();

        System.out.println(promptText);
    }
    */
}
