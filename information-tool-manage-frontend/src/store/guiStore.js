import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useGuiStore = defineStore('gui', () => {
  // ========== State ==========
  const containerId = ref('')
  const taskId = ref('')
  const vncPort = ref(null)
  const isTaskRunning = ref(false)
  const username = 13145739225
  const taskLogs = ref([])

  // ========== Actions ==========

  /**
   * 设置 GUI 连接信息（提交任务成功后调用）
   * @param {string} cid - 容器 ID
   * @param {string} tid - 任务 ID
   * @param {number} port - VNC 端口
   */
  const setGuiInfo = (cid, tid, port) => {
    containerId.value = cid
    taskId.value = tid
    vncPort.value = port
  }

  /**
   * 清除所有 GUI 连接信息（容器销毁后调用）
   */
  const clearGuiInfo = () => {
    containerId.value = ''
    taskId.value = ''
    vncPort.value = null
    isTaskRunning.value = false
  }

  /**
   * 设置任务运行状态（锁定/解锁界面按钮）
   * @param {boolean} status
   */
  const setRunningState = (status) => {
    isTaskRunning.value = status
  }

  const appendLog = (log) => {
    // 根据SSE事件状态生成适合渲染的字段
    const logEntry = {
      ...log,
      // 生成统一的时间戳
      timestamp: log.timestamp || Date.now(),
      // 根据状态类型生成instruction、action、result、message字段
      instruction: '',
      action: '',
      result: '',
      message: ''
    }

    switch (log.status) {
      case 'RUNNING':
        if (log.toolName) {
          // 工具调用日志
          logEntry.instruction = `步骤 ${log.step}-${log.toolStep}`
          logEntry.action = log.toolName
          logEntry.result = log.outputResult || ''
          logEntry.message = `工具输入: ${log.toolInput || ''}`
        } else {
          // 任务开始
          logEntry.instruction = '任务开始'
          logEntry.action = '执行'
          logEntry.result = ''
          logEntry.message = `共 ${log.totalSteps} 个步骤`
        }
        break
      case 'STEP_RUNNING':
        logEntry.instruction = `步骤 ${log.step}`
        logEntry.action = '开始执行'
        logEntry.result = ''
        logEntry.message = log.instruction || ''
        break
      case 'STEP_COMPLETED':
        logEntry.instruction = `步骤 ${log.step}`
        logEntry.action = '执行完成'
        logEntry.result = ''
        logEntry.message = log.message || `步骤 ${log.step} 执行完成`
        break
      case 'COMPLETED':
        logEntry.instruction = '所有步骤'
        logEntry.action = '执行完成'
        logEntry.result = ''
        logEntry.message = log.message || '所有步骤执行完成'
        break
      case 'FAILED':
        logEntry.instruction = `步骤 ${log.step || ''}`
        logEntry.action = '执行失败'
        logEntry.result = ''
        logEntry.message = log.errorMessage || '任务执行失败'
        break
      case 'TIMEOUT':
        logEntry.instruction = '任务'
        logEntry.action = '执行超时'
        logEntry.result = ''
        logEntry.message = log.errorMessage || '任务执行超时'
        break
      case 'ABORTED':
        logEntry.instruction = `步骤 ${log.step || ''}`
        logEntry.action = '被中止'
        logEntry.result = ''
        logEntry.message = log.message || '任务已被用户中止'
        break
      case 'SUSPENDED':
        logEntry.instruction = `步骤 ${log.step || ''}`
        logEntry.action = 'Agent挂起'
        logEntry.result = ''
        logEntry.message = `等待执行工具: ${log.toolName || ''}`
        break
      case 'RESUMED':
        logEntry.instruction = `步骤 ${log.step || ''}-${log.toolStep || ''}`
        logEntry.action = 'Agent恢复'
        logEntry.result = log.success ? '成功' : '失败'
        logEntry.message = `工具执行${log.success ? '成功' : '失败'}`
        break
      case 'TASK_COMPLETED':
        logEntry.instruction = '工作流'
        logEntry.action = '执行完成'
        logEntry.result = ''
        logEntry.message = '任务完成'
        break
      case 'TASK_FAILED':
        logEntry.instruction = '工作流'
        logEntry.action = '执行失败'
        logEntry.result = ''
        logEntry.message = log.errorMessage || '任务失败'
        break
      default:
        logEntry.instruction = log.status || ''
        logEntry.action = ''
        logEntry.result = ''
        logEntry.message = log.message || ''
    }

    taskLogs.value.push(logEntry)
  }

  const clearLogs = () => {
    taskLogs.value = []
  }

  return {
    containerId,
    taskId,
    vncPort,
    isTaskRunning,
    username,
    taskLogs,
    setGuiInfo,
    clearGuiInfo,
    setRunningState,
    appendLog,
    clearLogs
  }
})
