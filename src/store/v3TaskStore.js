import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { createV3Task, createV3TaskBatch, getV3TaskStatus, getV3TaskQueue, cancelV3Task, getV3TaskResults, getSseUrl } from '@/api/v3Task'

// 任务状态初始值
const createTaskState = (taskId) => ({
  taskId,
  status: 'PENDING',
  currentStep: 0,
  queuePosition: -1,
  queueDisplayText: '',
  toolLogs: [],
  taskResults: null,
  errorMessage: '',
  isCancelling: false
})

export const useV3TaskStore = defineStore('v3Task', () => {
  // ========== State ==========
  const clientId = ref('')
  const evtSource = ref(null)
  const isConnected = ref(false)
  const isConnecting = ref(false)

  // 多任务管理
  const tasksMap = ref(new Map()) // taskId -> taskState
  const selectedTaskId = ref(null) // 当前选中的任务

  const isCreating = ref(false)

  // ========== Computed ==========
  const tasksList = computed(() => Array.from(tasksMap.value.values()))

  const selectedTask = computed(() => {
    if (!selectedTaskId.value) return null
    return tasksMap.value.get(selectedTaskId.value) || null
  })

  const hasActiveTasks = computed(() => tasksMap.value.size > 0)

  const runningTasksCount = computed(() => {
    let count = 0
    tasksMap.value.forEach(task => {
      if (task.status === 'RUNNING' || task.status === 'PENDING') {
        count++
      }
    })
    return count
  })

  const completedTasksCount = computed(() => {
    let count = 0
    tasksMap.value.forEach(task => {
      if (task.status === 'SUCCESS' || task.status === 'FAILED') {
        count++
      }
    })
    return count
  })

  // ========== Actions ==========

  /**
   * 生成 clientId（UUID）
   */
  const generateClientId = () => {
    clientId.value = crypto.randomUUID()
    return clientId.value
  }

  /**
   * 建立 SSE 连接
   */
  const connectSSE = () => {
    if (evtSource.value) {
      evtSource.value.close()
    }

    if (!clientId.value) {
      generateClientId()
    }

    isConnecting.value = true
    const url = getSseUrl(clientId.value)
    const source = new EventSource(url)

    source.addEventListener('task_update', (e) => {
      try {
        const data = JSON.parse(e.data)
        handleTaskUpdate(data)
      } catch (err) {
        console.error('解析 SSE 事件失败:', err)
      }
    })

    source.onopen = () => {
      isConnected.value = true
      isConnecting.value = false
      console.log('SSE 连接已建立')
    }

    source.onerror = (err) => {
      console.error('SSE 连接错误:', err)
      isConnected.value = false
      isConnecting.value = false
      source.close()

      // 断线重连（延迟 3 秒）
      setTimeout(() => {
        if (clientId.value) {
          connectSSE()
        }
      }, 3000)
    }

    evtSource.value = source
  }

  /**
   * 断开 SSE 连接
   */
  const disconnectSSE = () => {
    if (evtSource.value) {
      evtSource.value.close()
      evtSource.value = null
      isConnected.value = false
      isConnecting.value = false
    }
  }

  /**
   * 处理 SSE 事件（根据 taskId 分发到对应任务）
   * @param {Object} data - 任务更新事件
   */
  const handleTaskUpdate = (data) => {
    const { taskId, status, step, toolStep, toolName, toolInput, outputResult, results, errorMessage: errMsg } = data

    // 根据 taskId 找到对应的任务
    const taskIdStr = String(taskId)
    let task = tasksMap.value.get(taskIdStr)

    // 如果任务不存在，创建一个新的任务状态
    if (!task) {
      task = createTaskState(taskIdStr)
      tasksMap.value.set(taskIdStr, task)
    }

    // 更新任务状态
    switch (status) {
      case 'RUNNING':
        task.status = 'RUNNING'
        // 工具调用级别更新
        task.toolLogs.push({
          step,
          toolStep,
          toolName,
          toolInput,
          outputResult,
          timestamp: data.timestamp || Date.now()
        })
        break

      case 'COMPLETED':
        task.status = 'SUCCESS'
        task.toolLogs.push({
          type: 'completed',
          totalSteps: data.totalSteps,
          timestamp: Date.now()
        })
        break

      case 'STEP_COMPLETED':
        task.currentStep = step || 0
        task.toolLogs.push({
          type: 'step_completed',
          step,
          timestamp: Date.now()
        })
        break

      case 'TASK_COMPLETED':
        task.status = 'SUCCESS'
        task.taskResults = results
        task.toolLogs.push({
          type: 'task_completed',
          timestamp: Date.now()
        })
        break

      case 'TASK_FAILED':
        task.status = 'FAILED'
        task.errorMessage = errMsg || '任务执行失败'
        task.toolLogs.push({
          type: 'task_failed',
          errorMessage: errMsg,
          timestamp: Date.now()
        })
        break

      case 'RESUMED':
        // Agent 恢复，可选处理
        break
    }

    // 触发响应式更新
    tasksMap.value = new Map(tasksMap.value)
  }

  /**
   * 创建任务（支持单个和批量）
   * @param {Object} formData - { promptId: number, configName: string, count?: number, userId?: string }
   */
  const createTask = async (formData) => {
    // 确保 clientId 存在
    if (!clientId.value) {
      generateClientId()
    }

    isCreating.value = true
    try {
      const { count, ...rest } = formData
      const requestData = {
        ...rest,
        clientId: clientId.value
      }

      let res
      if (count && count > 1) {
        // 批量创建
        res = await createV3TaskBatch({
          ...requestData,
          count
        })

        if (res.code === 200 && res.data) {
          const { taskIds, successCount, totalCount, errors } = res.data

          // 将新任务添加到 tasksMap
          taskIds.forEach(taskId => {
            const taskIdStr = String(taskId)
            if (!tasksMap.value.has(taskIdStr)) {
              tasksMap.value.set(taskIdStr, createTaskState(taskIdStr))
            }
          })

          // 选中第一个任务
          if (taskIds.length > 0) {
            selectedTaskId.value = String(taskIds[0])
          }

          // 触发响应式更新
          tasksMap.value = new Map(tasksMap.value)

          // 任务创建成功后建立 SSE 连接
          if (!isConnected.value) {
            connectSSE()
          }

          return {
            ...res.data,
            message: `批量创建完成，成功${successCount}个${errors ? `，失败${errors.length}个` : ''}`
          }
        }
      } else {
        // 单个创建
        res = await createV3Task(requestData)

        if (res.code === 200 && res.data) {
          const taskIdStr = String(res.data.taskId)

          // 将新任务添加到 tasksMap
          tasksMap.value.set(taskIdStr, createTaskState(taskIdStr))
          selectedTaskId.value = taskIdStr

          // 触发响应式更新
          tasksMap.value = new Map(tasksMap.value)

          // 任务创建成功后建立 SSE 连接
          if (!isConnected.value) {
            connectSSE()
          }

          return res.data
        }
      }

      throw new Error(res.message || '任务创建失败')
    } catch (error) {
      throw error
    } finally {
      isCreating.value = false
    }
  }

  /**
   * 选择任务
   * @param {string} taskId
   */
  const selectTask = (taskId) => {
    selectedTaskId.value = String(taskId)
  }

  /**
   * 查询任务状态
   * @param {number|string} taskId
   */
  const queryTaskStatus = async (taskId) => {
    try {
      const res = await getV3TaskStatus(taskId)
      if (res.code === 200 && res.data) {
        const taskIdStr = String(taskId)
        const task = tasksMap.value.get(taskIdStr)
        if (task) {
          task.status = res.data.status || 'PENDING'
          tasksMap.value = new Map(tasksMap.value)
        }
        return res.data
      }
      return null
    } catch (error) {
      console.error('查询任务状态失败:', error)
      return null
    }
  }

  /**
   * 查询排队位置
   * @param {number|string} taskId
   */
  const queryQueuePosition = async (taskId) => {
    try {
      const res = await getV3TaskQueue(taskId)
      if (res.code === 200 && res.data) {
        const taskIdStr = String(taskId)
        const task = tasksMap.value.get(taskIdStr)
        if (task) {
          task.queuePosition = res.data.position
          task.queueDisplayText = res.data.displayText
          tasksMap.value = new Map(tasksMap.value)
        }
        return res.data
      }
      return null
    } catch (error) {
      console.error('查询排队位置失败:', error)
      return null
    }
  }

  /**
   * 取消任务
   * @param {number|string} taskId
   */
  const cancelTask = async (taskId) => {
    const taskIdStr = String(taskId)
    const task = tasksMap.value.get(taskIdStr)
    if (!task) return false

    task.isCancelling = true
    try {
      const res = await cancelV3Task(taskId)
      if (res.code === 200) {
        task.status = 'FAILED'
        task.errorMessage = '任务已取消'
        tasksMap.value = new Map(tasksMap.value)
        return true
      }
      return false
    } catch (error) {
      console.error('取消任务失败:', error)
      return false
    } finally {
      task.isCancelling = false
      tasksMap.value = new Map(tasksMap.value)
    }
  }

  /**
   * 查询任务结果
   * @param {number|string} taskId
   */
  const queryTaskResults = async (taskId) => {
    try {
      const res = await getV3TaskResults(taskId)
      if (res.code === 200) {
        const taskIdStr = String(taskId)
        const task = tasksMap.value.get(taskIdStr)
        if (task) {
          task.taskResults = res.data
          tasksMap.value = new Map(tasksMap.value)
        }
        return res.data
      }
      return null
    } catch (error) {
      console.error('查询任务结果失败:', error)
      return null
    }
  }

  /**
   * 清空指定任务的日志
   * @param {number|string} taskId
   */
  const clearTaskLogs = (taskId) => {
    const taskIdStr = String(taskId)
    const task = tasksMap.value.get(taskIdStr)
    if (task) {
      task.toolLogs = []
      tasksMap.value = new Map(tasksMap.value)
    }
  }

  /**
   * 清空所有任务
   */
  const clearAllTasks = () => {
    tasksMap.value = new Map()
    selectedTaskId.value = null
  }

  /**
   * 重置状态
   */
  const resetState = () => {
    tasksMap.value = new Map()
    selectedTaskId.value = null
  }

  return {
    // State
    clientId,
    isConnected,
    isConnecting,
    tasksMap,
    selectedTaskId,
    isCreating,

    // Computed
    tasksList,
    selectedTask,
    hasActiveTasks,
    runningTasksCount,
    completedTasksCount,

    // Actions
    generateClientId,
    connectSSE,
    disconnectSSE,
    createTask,
    selectTask,
    queryTaskStatus,
    queryQueuePosition,
    cancelTask,
    queryTaskResults,
    clearTaskLogs,
    clearAllTasks,
    resetState
  }
})
