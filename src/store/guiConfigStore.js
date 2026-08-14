import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useGuiConfigStore = defineStore('guiConfig', () => {
  // ========== State ==========
  const isVncActive = ref(false) // 控制 VNC 是否激活，控制右侧新建按钮是否置灰
  const containerId = ref('') // 当前运行的养号容器 ID
  const vncPort = ref(null) // VNC 端口
  const currentProfile = ref('') // 当前正在操作的配置名
  const taskId = ref('') // 任务ID（farm/create 返回的 taskId）
  const clientId = ref('') // SSE客户端ID
  const baiduOpened = ref(true) // 创建时浏览器是否已自动打开百度
  const sessionStartAt = ref(0) // 养号会话创建时间戳（1 小时强制回收倒计时用）

  // ========== Actions ==========

  /**
   * 设置环境状态，将 isVncActive 置为 true
   * @param {Object} opts - { profile, cid, port, tid, cid2, opened, startAt }
   */
  const setVncEnv = ({ profile, cid, port, tid = '', cid2 = '', opened = true, startAt = Date.now() }) => {
    currentProfile.value = profile
    containerId.value = cid
    vncPort.value = port
    taskId.value = tid
    clientId.value = cid2
    baiduOpened.value = opened
    sessionStartAt.value = startAt
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
    taskId.value = ''
    clientId.value = ''
    baiduOpened.value = true
    sessionStartAt.value = 0
  }

  return {
    isVncActive,
    containerId,
    vncPort,
    currentProfile,
    taskId,
    clientId,
    baiduOpened,
    sessionStartAt,
    setVncEnv,
    clearVncEnv
  }
})
