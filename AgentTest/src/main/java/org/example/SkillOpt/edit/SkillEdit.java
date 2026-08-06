package org.example.skillOpt.edit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个 Skill 编辑操作。
 *
 * @author zhilin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillEdit {

    /** 编辑类型 */
    private EditType type;

    /** 目标 section 标题或行 pattern */
    private String targetSection;

    /** 新内容（DELETE 时为 null） */
    private String content;

    /** 为什么需要这个编辑 */
    private String rationale;

    /** 优先级（0.0~1.0，用于排序和裁剪） */
    @Builder.Default
    private double priority = 0.5;
}
