package org.example.common.proptcraft.compositePrompt;

import org.example.common.proptcraft.PromptComponent;


public class Skill extends PromptComponent {
    // 私有构造，强制使用 create
    private Skill() {
    }

    /**
     * 【核心入口】
     * 创建 Skill 并强制写入 name 和 description
     * * @param name 技能名称 (将作为 YAML name 和 Markdown H1 标题)
     * @param description 技能描述 (将作为 YAML description)
     * @return 返回 Skill 对象本身，支持后续链式调用
     */
    public static Skill create(String name, String description) {
        Skill skill = new Skill();
        // 调用接口中定义的内部方法，先生成头部
        skill._internalSkillHeader(name, description);
        return skill;
    }



}
