package org.example.common.proptcraft.compositePrompt;/**
 * @Auter zzh
 * @Date 2025/10/23
 */

/**
 * @projectName: AItest
 * @package: org.example.aitest.infrastructure.proptcraft.compositePrompt
 * @className: TreeOfThoughtPrompt
 * @author: Eric
 * @description: TODO
 * @date: 2025/10/23 22:41
 * @version: 1.0
 */


import org.example.common.proptcraft.PromptComponent;
import org.example.common.proptcraft.PromptComponentEasyFactory;

/**
 * 思维树提示 (Tree-of-Thought Prompting) 组件。
 * 一种高级推理框架，将问题解决视为一个搜索过程。
 * 通常涉及三个步骤：
 * 1. 生成多个初始的推理路径或解决方案。
 * 2. 评估这些路径并选择最有希望的一个。
 * 3. 从最佳路径出发，进一步探索未来的可能性 [[94]]。
 * @author zzh
 */
public class TreeOfThoughtPrompt extends PromptComponent implements PromptComponentEasyFactory {


    /**
     * 第一步：生成多个初始路径或方案。
     *
     * @param generationTask 生成任务的描述。
     * @return 当前实例，用于方法链。
     */
    public TreeOfThoughtPrompt generatePaths(String generationTask) {
        append(of("Step 1: " + generationTask));
        return this;
    }

    /**
     * 第二步：评估已生成的路径。在 `evaluationTask` 中使用 `{paths}` 作为占位符。
     *
     * @param evaluationTask 评估任务的描述，包含 `{paths}` 占位符。
     * @param paths          第一步生成的路径内容。
     * @return 当前实例，用于方法链。
     */
    public TreeOfThoughtPrompt evaluatePaths(String evaluationTask, String paths) {
        append(of("Step 2: " + evaluationTask.replace("{paths}", paths)));
        return this;
    }

    /**
     * 第三步：探索最佳路径的未来状态。在 `explorationTask` 中使用 `{best_path}` 作为占位符。
     *
     * @param explorationTask 探索任务的描述，包含 `{best_path}` 占位符。
     * @param bestPath        第二步选出的最佳路径。
     * @return 当前实例，用于方法链。
     */
    public TreeOfThoughtPrompt exploreBest(String explorationTask, String bestPath) {
        append(of("Step 3: " + explorationTask.replace("{best_path}", bestPath)));
        return this;
    }



    // =============== 使用示例 ===============
    /*
    public static void main(String[] args) {
        // 假设第一步和第二步已经完成
        String generatedPaths = "Option A: ...\nOption B: ...\nOption C: ...";
        String bestPath = "Option B: ...";

        String promptText = new TreeOfThoughtPrompt()
                .generatePaths("Generate 3 different marketing strategies for a new product.")
                .evaluatePaths("Analyze these strategies end select the best one:\n{paths}", generatedPaths)
                .exploreBest("Outline the first 3 months of execution for this strategy: {best_path}", bestPath)
                .render();

        System.out.println(promptText);
    }
    */
}
