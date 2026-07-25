import request from '@/utils/request'

/**
 * 获取配置列表
 * @returns {Promise} 返回配置列表数据
 */
export function getFarmProfiles() {
  return request({
    url: '/api/v2/agent/farm/profiles',
    method: 'get'
  })
}

/**
 * 拉起养号环境 (V3版本)
 * @param {Object} data - { profileName, clientId }
 * @returns {Promise} 返回 { taskId, containerId, vncPort, profile, message }
 */
export function setupFarmEnvironment(data) {
  return request({
    url: '/api/hr/v3/connect',
    method: 'post',
    data,
    timeout: 60000 // VNC连接超时60秒
  })
}

/**
 * 确认人工登录完成
 * @param {Object} data - { profileName, targetUrl, containerId }
 * @returns {Promise}
 */
export function confirmFarmProfile(data) {
  return request({
    url: '/api/v2/agent/farm/setProfile',
    method: 'post',
    data
  })
}

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
 * 执行工作流任务 (V3版本)
 * @param {Object} data - { taskId, sysPrompts, stepPrompts }
 * @returns {Promise} 返回 { taskId, message }
 */
export function executeV3Task(data) {
  return request({
    url: '/api/hr/v3/execute',
    method: 'post',
    data
  })
}

/**
 * 获取V3 SSE连接地址
 * @param {string} clientId - 客户端ID
 * @returns {string} SSE连接URL
 */
export function getV3SseUrl(clientId) {
  return `/api/hr/v3/sse/${clientId}`
}

/**
 * 查询V3任务状态
 * @param {string} taskId - 任务ID
 * @returns {Promise} 返回任务状态信息
 */
export function getV3TaskStatus(taskId) {
  return request({
    url: `/api/hr/v3/task/${taskId}`,
    method: 'get'
  })
}

/**
 * 查询V3任务排队位置
 * @param {string} taskId - 任务ID
 * @returns {Promise} 返回排队位置信息
 */
export function getV3TaskQueue(taskId) {
  return request({
    url: `/api/hr/v3/task/${taskId}/queue`,
    method: 'get'
  })
}
