import request from '@/utils/request'

/**
 * 停止并移除容器 (V3版本)
 * @param {string} containerId - 容器 ID
 * @returns {Promise}
 */
export function stopContainer(containerId) {
  return request({
    url: `/api/v3/agent/container/${containerId}`,
    method: 'delete'
  })
}

/**
 * SSE 连接地址（V3版本，GET 方式）
 * URL: /api/hr/v3/sse/${clientId}
 */
export function getSseUrl(clientId) {
  return `/api/hr/v3/sse/${clientId}`
}

/**
 * 中断当前任务执行 (V3版本)
 * @param {string} taskId - 任务 ID
 * @returns {Promise}
 */
export function abortTask(taskId) {
  return request({
    url: `/api/v3/agent/task/${taskId}/abort`,
    method: 'post'
  })
}
