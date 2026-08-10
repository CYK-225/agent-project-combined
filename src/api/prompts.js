import request from '@/utils/request'

// ========== 大提示词（Prompts）接口 ==========

export function getPrompts(params) {
  return request({
    url: '/api/prompts/page',
    method: 'get',
    params: {
      pageNumber: params.page || 1,
      pageSize: params.size || 10,
      title: params.title || undefined
    }
  })
}

export function getPromptById(id) {
  return request({
    url: `/api/prompts/${id}`,
    method: 'get'
  })
}

export function createPrompt(data) {
  return request({
    url: '/api/prompts/add',
    method: 'post',
    data
  })
}

export function updatePrompt(data) {
  return request({
    url: '/api/prompts/updatePrompts',
    method: 'post',
    data
  })
}

export function deletePrompt(id) {
  return request({
    url: `/api/prompts/${id}`,
    method: 'delete'
  })
}

// ========== 小提示词（Mini Prompts）接口 ==========
// 注意：小提示词所有接口均为 POST，参数通过 query 传递

/**
 * 新增小提示词
 * @param {object} params - { title, content, type, isPublic }
 */
export function addMiniPrompt(data) {
  return request({ url: '/addMiniPrompts', method: 'post', data })
}

/**
 * 修改小提示词
 * @param {object} params - { id, title, content, type, isPublic }
 */
export function updateMiniPrompt(data) {
  return request({ url: '/updateMiniPrompts', method: 'post', data })
}

/**
 * 删除小提示词
 * @param {number|string} id
 */
export function deleteMiniPrompt(id) {
  return request({ url: `/deleteMiniPrompts/${id}`, method: 'post' })
}

/**
 * 分页查询小提示词（支持 type 筛选和关键字搜索）
 * @param {object} params - { page, size, type, keyword }
 */
export function getMiniPromptsList(params) {
  return request({
    url: '/selectMiniPromptsList',
    method: 'post',
    params: {
      pageNumber: params.page || 1,
      pageSize: params.size || 10,
      type: params.type || undefined,
      keyword: params.keyword || undefined
    }
  })
}

/**
 * 根据类型分页查询小提示词
 * @param {string|number} type - 小提示词类型
 * @param {object} [params] - 分页参数
 * @param {number} [params.pageNumber=1] - 页码
 * @param {number} [params.pageSize=10] - 每页条数
 * @param {string} [params.keyword] - 关键字搜索（匹配标题+内容）
 * @returns {Promise} 返回分页结果
 */
export function getMiniPromptsByType(type, params = {}) {
  return request({
    url: `/selectMiniPromptsByType/${type}`,
    method: 'post',
    params: {
      pageNumber: params.pageNumber || 1,
      pageSize: params.pageSize || 10,
      keyword: params.keyword || undefined
    }
  })
}
