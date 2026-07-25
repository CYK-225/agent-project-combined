/**
 * 小提示词类型枚举字典
 * 1: 角色类型
 * 2: 物理规则类型
 * 3: 步骤类型
 * 4: 背景知识类型
 * 5: 步骤输出类型
 */
export const MINI_PROMPT_TYPE_MAP = {
  1: '角色类型',
  2: '物理规则类型',
  3: '步骤类型',
  4: '背景知识类型',
  5: '步骤输出类型'
}

/**
 * 小提示词类型选项列表（适用于下拉选择框）
 */
export const MINI_PROMPT_TYPE_OPTIONS = Object.entries(MINI_PROMPT_TYPE_MAP).map(([value, label]) => ({
  value: Number(value),
  label
}))

/**
 * 根据类型值获取类型标签文本
 * @param {number} type
 * @returns {string}
 */
export function getMiniPromptTypeLabel(type) {
  return MINI_PROMPT_TYPE_MAP[type] || '未知类型'
}
