package org.example.common.proptcraft.compositePrompt;/**
 * @Auter zzh
 * @Date 2025/10/23
 */

/**
 * @projectName: AItest
 * @package: org.example.aitest.infrastructure.proptcraft.compositePrompt
 * @className: ChainOfThoughtPrompt
 * @author: Eric
 * @description: TODO
 * @date: 2025/10/23 22:41
 * @version: 1.0
 */

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.example.common.proptcraft.PromptComponent;


/**
 * 思维链提示 (Chain-of-Thought Prompting) 组件。
 * 通过鼓励模型生成中间推理步骤来提高复杂推理任务（如数学、逻辑）的准确性。
 * 关键在于触发词 "Let's think step by step." [[25]]。
 * @author zzh
 */
@Data
@Builder
@AllArgsConstructor
public class ChainOfThoughtPrompt extends PromptComponent {


    /**
     * 设置需要模型逐步推理的问题。
     *
     * @param problemStatement 问题陈述。
     * @return 当前实例，用于方法链。
     */
    public ChainOfThoughtPrompt problem(String problemStatement) {
        append(of(problemStatement + "\nLet's think step by step."));
        return this;
    }

    /**
     * （可选）提供一个完整的思维链示例，用于少样本学习。
     *
     * @param example 完整的CoT示例。
     * @return 当前实例，用于方法链。
     */
    public ChainOfThoughtPrompt withExample(String example) {
        prepend(of("EXAMPLE:\n" + example));
        return this;
    }



    // =============== 使用示例 ===============
    /*
    public static void main(String[] args) {
        String cotExample = """
                Q: If I have 5 apples end give 2 to my friend, how many do I have left?
                A: I start with 5 apples. I give away 2 apples. So, 5 - 2 = 3. I have 3 apples left.
                """;

        String promptText = new ChainOfThoughtPrompt()
                .withExample(cotExample)
                .problem("Q: A train travels 60 miles per hour. How far will it go in 2.5 hours?")
                .render();

        System.out.println(promptText);
    }
    */
}
