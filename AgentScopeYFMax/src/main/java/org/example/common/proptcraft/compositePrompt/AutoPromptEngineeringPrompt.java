package org.example.common.proptcraft.compositePrompt;/**
 * @Auter zzh
 * @Date 2025/10/23
 */

/**
 * @projectName: AItest
 * @package: org.example.aitest.infrastructure.proptcraft.compositePrompt
 * @className: AutoPromptEngineeringPrompt
 * @author: Eric
 * @description: TODO
 * @date: 2025/10/23 22:42
 * @version: 1.0
 */

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.example.common.proptcraft.PromptComponent;


/**
 * 自动提示工程 (Automatic Prompt Engineering) 组件。
 * 这是一种元技术，利用AI模型本身来生成和评估不同的提示词变体，
 * 以找到针对特定任务的最佳提示 [[105]]。
 * @author zzh
 */
@Data
@Builder
@AllArgsConstructor
public class AutoPromptEngineeringPrompt extends PromptComponent {


    /**
     * 第一步：要求模型为一个基础指令生成多个语义相同但表述不同的变体。
     *
     * @param baseInstruction 基础指令。
     * @return 当前实例，用于方法链。
     */
    public AutoPromptEngineeringPrompt generateVariants(String baseInstruction) {
        append(of("Generate variants for: \"" + baseInstruction + "\""));
        return this;
    }

    /**
     * 第二步：提供生成的变体，并要求模型（或另一个评估器）选出最佳的一个。
     *
     * @param variants 由第一步生成的变体列表。
     * @return 当前实例，用于方法链。
     */
    public AutoPromptEngineeringPrompt evaluateVariants(String variants) {
        append(of("Evaluate these variants end select the best one:\n----\n" + variants + "\n----"));
        return this;
    }



    // =============== 使用示例 ===============
    /*
    public static void main(String[] args) {
        // 假设第一步已经执行并得到了变体
        String variants = """
                1. Please order one Metallica t-shirt, size S.
                2. I'd like to buy a small Metallica t-shirt.
                3. Can I get a Metallica tee in size small?
                """;

        String promptText = new AutoPromptEngineeringPrompt()
                .generateVariants("One Metallica t-shirt size S")
                .evaluateVariants(variants)
                .render();

        System.out.println(promptText);
    }
    */
}
