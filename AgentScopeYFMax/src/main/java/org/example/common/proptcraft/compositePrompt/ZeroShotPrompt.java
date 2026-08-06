package org.example.common.proptcraft.compositePrompt;/**
 * @Auter zzh
 * @Date 2025/10/23
 */

/**
 * @projectName: AItest
 * @package: org.example.aitest.infrastructure.proptcraft.compositePrompt
 * @className: ZeroShotPrompt
 * @author: Eric
 * @description: TODO
 * @date: 2025/10/23 22:39
 * @version: 1.0
 */
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.example.common.proptcraft.PromptComponent;
import org.example.common.proptcraft.PromptComponentEasyFactory;


/**
 * 零样本提示 (Zero-Shot Prompting) 组件。
 * 用于在不提供任何示例的情况下，直接向模型发出指令以执行任务。
 * 适用于模型在训练中很可能已经见过的简单任务。
 * @author zzh
 */
@Data
@Builder
@AllArgsConstructor
public class ZeroShotPrompt extends PromptComponent implements PromptComponentEasyFactory {


    /**
     * 设置任务描述，例如 "将以下英文翻译成中文"。
     *
     * @param taskDescription 任务的指令描述。
     * @return 当前实例，用于方法链。
     */
    public ZeroShotPrompt task(String taskDescription) {
        append(of(taskDescription));
        return this;
    }

    /**
     * 设置具体的输入内容。
     *
     * @param inputText 具体的输入文本。
     * @return 当前实例，用于方法链。
     */
    public ZeroShotPrompt input(String inputText) {
        append(of(inputText));
        return this;
    }




    // =============== 使用示例 ===============
    /*
    public static void main(String[] args) {
        String promptText = new ZeroShotPrompt()
                .task("Classify the following movie review as POSITIVE, NEUTRAL, or NEGATIVE.")
                .input("Review: \"Her\" is a masterpiece of modern cinema.")
                .render();

        System.out.println(promptText);
        // 输出:
        // Classify the following movie review as POSITIVE, NEUTRAL, or NEGATIVE.
        // Review: "Her" is a masterpiece of modern cinema.
    }
    */
}