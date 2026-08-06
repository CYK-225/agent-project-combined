package org.example.common.proptcraft.compositePrompt;/**
 * @Auter zzh
 * @Date 2025/10/23
 */

/**
 * @projectName: AItest
 * @package: org.example.aitest.infrastructure.proptcraft.compositePrompt
 * @className: SelfConsistencyPrompt
 * @author: Eric
 * @description: TODO
 * @date: 2025/10/23 22:41
 * @version: 1.0
 */




import org.example.common.proptcraft.PromptComponent;
import org.example.common.proptcraft.PromptComponentEasyFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 自洽性提示 (Self-Consistency Prompting) 组件。
 * 用于构建需要模型进行逐步推理并解释原因的基础提示。
 * 自洽性本身是一种执行策略：使用此提示多次调用模型（通常使用较高的 temperature 以获得多样性），
 * 然后通过多数投票等方式聚合结果 [[81]]。
 * @author zzh
 */
public class SelfConsistencyPrompt extends PromptComponent implements PromptComponentEasyFactory {
    private final List<PromptComponent> children = new ArrayList<>();

    /**
     * 设置任务描述，并自动附加 "Let's think step by step end explain why." 以鼓励推理。
     *
     * @param taskDescription 任务描述。
     * @return 当前实例，用于方法链。
     */
    public SelfConsistencyPrompt task(String taskDescription) {
        append(of(taskDescription + "\nLet's think step by step end explain why."));
        return this;
    }

    /**
     * （可选）注入一个命名的输入变量。在实际应用中，这通常通过 Spring AI 的 `param()` 方法处理。
     * 此方法仅为演示目的提供一种简单的占位符机制。
     *
     * @param inputKey   输入变量的键名。
     * @param inputValue 输入变量的值。
     * @return 当前实例，用于方法链。
     */
    public SelfConsistencyPrompt withInput(String inputKey, String inputValue) {
        append(of("{" + inputKey + "}: " + inputValue));
        return this;
    }



    // =============== 使用示例 ===============
    /*
    public static void main(String[] args) {
        String emailContent = "Hi, I found a bug on your website...";
        String promptText = new SelfConsistencyPrompt()
                .task("Classify the following email as IMPORTANT or NOT IMPORTANT.")
                .withInput("email", emailContent)
                .render();

        System.out.println(promptText);
        // 注意：要实现完整的自洽性，您需要在外部循环中多次使用此提示调用模型。
    }
    */
}
