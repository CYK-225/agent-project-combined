import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useGuiConfigStore = defineStore('guiConfig', () => {
  // ========== State ==========
  const isVncActive = ref(false) // 控制 VNC 是否激活，控制右侧拉起按钮是否置灰
  const containerId = ref('') // 当前运行的容器 ID
  const vncPort = ref(null) // VNC 端口
  const currentProfile = ref('') // 当前正在操作的配置名
  const currentUrl = ref('') // 当前正在操作的网址
  const taskId = ref('') // V3任务ID
  const clientId = ref('') // SSE客户端ID

  // ========== Actions ==========

  /**
   * 设置环境状态，将 isVncActive 置为 true
   * @param {string} profile - 配置名
   * @param {string} url - 目标网址
   * @param {string} cid - 容器 ID
   * @param {number} port - VNC 端口
   * @param {string} tid - V3任务ID
   * @param {string} cid2 - SSE客户端ID
   */
  const setVncEnv = (profile, url, cid, port, tid = '', cid2 = '') => {
    currentProfile.value = profile
    currentUrl.value = url
    containerId.value = cid
    vncPort.value = port
    taskId.value = tid
    clientId.value = cid2
    isVncActive.value = true
  }

  /**
   * 清空环境状态，将 isVncActive 置为 false
   */
  const clearVncEnv = () => {
    isVncActive.value = false
    containerId.value = ''
    vncPort.value = null
    currentProfile.value = ''
    currentUrl.value = ''
    taskId.value = ''
    clientId.value = ''
  }

  return {
    isVncActive,
    containerId,
    vncPort,
    currentProfile,
    currentUrl,
    taskId,
    clientId,
    setVncEnv,
    clearVncEnv
  }
})
