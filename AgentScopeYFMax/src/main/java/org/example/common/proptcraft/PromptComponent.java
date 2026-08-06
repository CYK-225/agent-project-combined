package org.example.common.proptcraft;/**
 * @Auter zzh
 * @Date 2025/10/10
 */

/**
 * @projectName: AItest
 * @package: org.example.aitest.infrastructure.proptcraft.component
 * @className: PromptCompoment
 * @author: Eric
 * @description: TODO
 * @date: 2025/10/10 23:40
 * @version: 1.0
 */
public abstract class PromptComponent implements PromptComponentEasyFactory {

    private final StringBuilder buffer = new StringBuilder();

    @Override
    public PromptComponent append(PromptComponent component) {
        // 【核心修改】防自吞机制
        // 如果 component 就是 this，说明是 test2.append(test2.box()) 这种情况
        // 因为 box() 内部已经 append 过了，所以这里直接忽略，防止无限循环
        if (component == null || component == this) {
            return this;
        }

        String content = component.render();
        if (content != null && !content.isEmpty()) {
            if (!buffer.isEmpty()) {
                buffer.append("\n\n");
            }
            buffer.append(content);
        }
        return this;
    }

    /**
     * 头部插入同理
     */
    public PromptComponent prepend(PromptComponent component) {
        // 防止自己插到自己前面
        if (component == null || component == this) {
            return this;
        }
        String content = component.render();
        if (content != null && !content.isEmpty()) {
            if (!buffer.isEmpty()) {
                buffer.insert(0, "\n\n");
            }
            buffer.insert(0, content);
        }
        return this;
    }
    @Override
    public String render() {
        return buffer.toString();
    }

}