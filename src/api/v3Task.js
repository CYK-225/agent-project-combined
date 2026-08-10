import request from '@/utils/request'

/**
 * 创建单个 V3 自动化任务
 * @param {Object} data - { promptId: number, configName: string, clientId: string, companyName?: string, userId?: string }
 * @returns {Promise} 返回 { taskId: number, queuePosition: number, message: string }
 */
export function createV3Task(data) {
  return request({
    url: '/api/hr/v3/task',
    method: 'post',
    data
  })
}

/**
 * 批量创建 V3 自动化任务
 * @param {Object} data - { promptId: number, configName: string, clientId: string, count: number, companyName?: string, userId?: string }
 * @returns {Promise} 返回 { taskIds: number[], successCount: number, totalCount: number, errors?: string[] }
 */
export function createV3TaskBatch(data) {
  return request({
    url: '/api/hr/v3/task/batch',
    method: 'post',
    data
  })
}

/**
 * 查询 V3 任务状态
 * @param {number} taskId - 任务 ID
 * @returns {Promise} 返回任务状态信息
 */
export function getV3TaskStatus(taskId) {
  return request({
    url: `/api/hr/v3/task/${taskId}`,
    method: 'get'
  })
}

/**
 * 查询 V3 任务排队位置
 * @param {number} taskId - 任务 ID
 * @returns {Promise} 返回 { taskId: number, position: number, displayText: string }
 */
export function getV3TaskQueue(taskId) {
  return request({
    url: `/api/hr/v3/task/${taskId}/queue`,
    method: 'get'
  })
}

/**
 * 查询 V3 任务聚合结果
 * @param {number} taskId - 任务 ID
 * @returns {Promise} 返回 AI 中台所有步骤的 modelOutput 聚合
 */
export function getV3TaskResults(taskId) {
  return request({
    url: `/api/v3/agent/results/${taskId}`,
    method: 'get'
  })
}

/**
 * 取消 V3 任务
 * @param {number} taskId - 任务 ID
 * @returns {Promise}
 */
export function cancelV3Task(taskId) {
  return request({
    url: `/api/v3/agent/cancel/${taskId}`,
    method: 'post'
  })
}

/**
 * AI 中台健康检查
 * @returns {Promise}
 */
export function getV3AgentHealth() {
  return request({
    url: '/api/v3/agent/health',
    method: 'get'
  })
}

/**
 * 获取 SSE 连接地址
 * @param {string} clientId - 前端生成的唯一标识（UUID）
 * @returns {string} SSE 连接 URL
 */
export function getSseUrl(clientId) {
  const baseUrl = import.meta.env.VITE_BASE_URL || 'http://8.129.128.167:8981'
  return `${baseUrl}/api/hr/v3/sse/${clientId}`
}
