package org.example.skillOpt.edit;

/**
 * Skill 编辑类型枚举。
 *
 * @author zhilin
 */
public enum EditType {
    /** 在指定 section 末尾追加内容 */
    APPEND,
    /** 在匹配的行后面插入内容 */
    INSERT_AFTER,
    /** 替换指定 section 的内容 */
    REPLACE,
    /** 删除指定 section */
    DELETE
}
