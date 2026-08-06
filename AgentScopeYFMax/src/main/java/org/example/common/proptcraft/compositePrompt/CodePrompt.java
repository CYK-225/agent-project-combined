package org.example.common.proptcraft.compositePrompt;/**
 * @Auter zzh
 * @Date 2025/10/23
 */

/**
 * @projectName: AItest
 * @package: org.example.aitest.infrastructure.proptcraft.compositePrompt
 * @className: CodePrompt
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
 * 代码提示 (Code Prompting) 组件。
 * 专门用于代码相关的任务，利用LLM理解和生成编程语言的能力 [[117]]。
 * 适用于编写新代码、解释现有代码、调试问题以及在不同编程语言之间进行转换。
 * @author zzh
 */
@Data
@Builder
@AllArgsConstructor
public class CodePrompt extends PromptComponent {


    /**
     * 要求模型用指定语言编写代码。
     *
     * @param language    目标编程语言。
     * @param requirement 代码需求描述。
     * @return 当前实例，用于方法链。
     */
    public CodePrompt writeCode(String language, String requirement) {
        append(of("Write a code snippet in " + language + ": " + requirement));
        return this;
    }

    /**
     * 要求模型解释一段给定的代码。
     *
     * @param code 需要解释的代码。
     * @return 当前实例，用于方法链。
     */
    public CodePrompt explainCode(String code) {
        append(of("Explain the following code:\n```\n" + code + "\n```"));
        return this;
    }

    /**
     * 要求模型将代码从一种语言翻译成另一种语言。
     *
     * @param sourceLang 源语言。
     * @param targetLang 目标语言。
     * @param code       需要翻译的源代码。
     * @return 当前实例，用于方法链。
     */
    public CodePrompt translateCode(String sourceLang, String targetLang, String code) {
        append(of("Translate the following " + sourceLang + " code to " + targetLang + ":\n" + code));
        return this;
    }




}
