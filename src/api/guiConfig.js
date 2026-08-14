import request from '@/utils/request'

/**
 * 获取养号配置列表
 * 返回统一结构 { code, message, data: [{ profileName, summary, websites: [{ id, url, status }] }] }
 * @returns {Promise}
 */
export function getFarmProfiles() {
  return request({
    url: '/api/hr/v3/farm/profiles',
    method: 'get'
  })
}

/**
 * 创建养号会话（自动打开百度，可写挂载登录态）
 * @param {Object} data - { profileName, clientId }（VNC 端口由后端自动分配，前端不传）
 * @returns {Promise} { code, message, data: { taskId, containerId, vncPort, profile, baiduOpened, message } }
 * code: 200=成功；503=容器池已满；409=任务被拒
 */
export function createFarmSession(data) {
  return request({
    url: '/api/hr/v3/farm/create',
    method: 'post',
    data,
    timeout: 60000 // 容器创建 + 自动进百度，等待 60 秒
  })
}

/**
 * 保存配置（先落库 auth_info，再销毁容器断开 VNC）
 * @param {Object} data - { profileName, containerId }
 * @returns {Promise} { code, message, data: { profileName, websiteName, message } }
 */
export function saveFarmSession(data) {
  return request({
    url: '/api/hr/v3/farm/save',
    method: 'post',
    data
  })
}

/**
 * 取消养号会话（仅销毁容器，不落库）
 * @param {Object} data - { containerId }
 * @returns {Promise} { code, message, data: { message } }
 */
export function cancelFarmSession(data) {
  return request({
    url: '/api/hr/v3/farm/cancel',
    method: 'post',
    data
  })
}

/**
 * 配置标记（已有配置 + 网址 → 落库，标记为已登录，无容器操作）
 * @param {Object} data - { profileName, websiteUrl }
 * @returns {Promise} { code, message, data: { profileName, websiteUrl, message } }
 */
export function markFarmProfile(data) {
  return request({
    url: '/api/hr/v3/farm/mark',
    method: 'post',
    data
  })
}

/**
 * 重新拉起浏览器（养号会话内手动重开浏览器，自动进入百度，不依赖 AI 中台）
 * @param {Object} data - { containerId }
 * @returns {Promise} { code, message, data: { containerId, vncPort, baiduOpened, message } }
 * code: 200=成功；404=会话已保存/取消/超时回收；500=容器不可达等异常
 */
export function openFarmBrowser(data) {
  return request({
    url: '/api/hr/v3/farm/open-browser',
    method: 'post',
    data
  })
}

/**
 * 更新配置（V3 可写挂载，登录态可持久化）
 * ⚠️ 该接口返回裸 JSON（非 ResultData 结构），字段为 vnc_port（下划线）
 * @param {Object} data - { profileName, targetUrl, port }
 * @returns {Promise} { status: 'env_ready', containerId, vnc_port, message }
 */
export function updateProfile(data) {
  return request({
    url: '/api/v3/agent/task/updateProfile',
    method: 'post',
    data,
    timeout: 60000
  })
}

/**
 * 拉起养号环境 (旧版 /api/hr/v3/connect，GUI 管理页任务执行仍在使用)
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
