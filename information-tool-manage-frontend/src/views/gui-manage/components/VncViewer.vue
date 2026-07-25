<template>
  <el-card class="vnc-card" shadow="never">
    <template #header>
      <div class="card-header">
        <span>GUI 监控视图</span>
        <div class="header-right">
          <el-select
            v-model="selectedProfile"
            placeholder="选择环境配置(必选)"
            size="small"
            style="width: 160px; margin-right: 8px;"
          >
            <el-option
              v-for="item in profileList"
              :key="item.profileName || item"
              :label="item.profileName || item"
              :value="item.profileName || item"
            />
          </el-select>

          <el-tag :type="statusType" size="small" class="status-tag">{{ statusText }}</el-tag>
          <el-button
            type="info"
            size="small"
            plain
            @click="emit('view-log')"
          >
            <el-icon><Document /></el-icon>
            查看日志
          </el-button>
          <el-button
            v-if="guiStore.taskId"
            type="danger"
            size="small"
            plain
            :disabled="!guiStore.isTaskRunning"
            @click="handleAbortTask"
          >
            <el-icon><CircleClose /></el-icon>
            中断任务
          </el-button>
          <el-button
            type="primary"
            size="small"
            :loading="connecting"
            :disabled="guiStore.isTaskRunning"
            @click="handleConnectVnc"
          >
            <el-icon v-if="!connecting"><Link /></el-icon>
            {{ connected ? '重新连接' : '连接 VNC' }}
          </el-button>
        </div>
      </div>
    </template>
    <div v-loading="connecting" element-loading-text="正在连接 VNC 服务..." class="vnc-container">
      <div ref="vncContainer" class="vnc-screen"></div>
      <div v-if="!connected && !connecting" class="vnc-placeholder">
        <el-icon :size="64" color="#c0c4cc"><Monitor /></el-icon>
        <p>请点击右上角按钮连接 VNC</p>
      </div>
    </div>
  </el-card>

</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { Monitor, Link, CircleClose, Document, Search, Delete } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { fetchEventSource } from '@microsoft/fetch-event-source'
import { useGuiStore } from '@/store/guiStore'
import { stopContainer, getSseUrl, abortTask } from '@/api/guiManage'
import { getFarmProfiles, setupFarmEnvironment } from '@/api/guiConfig'
import { v4 as uuidv4 } from 'uuid'

const guiStore = useGuiStore()
const emit = defineEmits(['view-log'])

// 新增：配置选择相关状态
const selectedProfile = ref('')
const profileList = ref([])

// VNC 相关
const VNC_HOST = '8.129.128.167'

/**
 * 生成随机奇数端口
 * 在 [9100, 9200] 整数区间内筛选所有奇数，随机抽取一个
 * @returns {number} 随机奇数端口（9101, 9103, ..., 9199）
 */
const generateRandomOddPort = () => {
  const oddPorts = []
  for (let port = 9101; port <= 9199; port += 2) {
    oddPorts.push(port)
  }
  const randomIndex = Math.floor(Math.random() * oddPorts.length)
  return oddPorts[randomIndex]
}

const vncContainer = ref(null)
const connecting = ref(false)
const connected = ref(false)
let rfbInstance = null

// SSE 相关
let sseAbortController = null

const statusType = computed(() => {
  if (connected.value) return 'success'
  if (connecting.value) return 'warning'
  return 'danger'
})

const statusText = computed(() => {
  if (connected.value) return '已连接'
  if (connecting.value) return '连接中...'
  return '未连接'
})

// ========== SSE 连接 ==========

/**
 * 建立 SSE 连接（POST 方式）
 * 监听 task_update 事件，当 status === 'complete' 时解锁界面
 */
const connectSse = (clientId) => {
  // 先断开旧连接
  disconnectSse()

  sseAbortController = new AbortController()
  const token = localStorage.getItem('token') || ''
  // 使用相对路径，走Vite代理
  const sseUrl = getSseUrl(clientId)

  fetchEventSource(sseUrl, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`
    },
    signal: sseAbortController.signal,

    onopen(response) {
      if (response.ok) {
        console.log('[SSE] 连接已建立')
      } else {
        console.error('[SSE] 连接失败:', response.status)
        ElMessage.error(`SSE 连接失败 (${response.status})`)
      }
    },

    onmessage(event) {
      if (event.event === 'task_update') {
        try {
          const data = JSON.parse(event.data)
          console.log('[SSE] 收到 task_update:', data)

          // 将日志推入全局 Store 数组
          guiStore.appendLog(data)

          // 根据文档处理不同的状态类型
          switch (data.status) {
            case 'RUNNING':
              // 任务开始执行或工具调用日志
              if (data.toolName) {
                console.log(`[SSE] 工具调用: 步骤${data.step}-${data.toolStep} ${data.toolName}`)
              } else {
                console.log(`[SSE] 任务开始，共${data.totalSteps}个步骤`)
              }
              break
            case 'STEP_RUNNING':
              console.log(`[SSE] 开始执行步骤${data.step}: ${data.instruction}`)
              break
            case 'STEP_COMPLETED':
              console.log(`[SSE] 步骤${data.step}执行完成`)
              break
            case 'COMPLETED':
              console.log('[SSE] 所有步骤执行完成')
              guiStore.setRunningState(false)
              ElMessage.success('所有步骤执行完成！')
              break
            case 'FAILED':
              console.error(`[SSE] 任务失败: ${data.errorMessage}`)
              guiStore.setRunningState(false)
              ElMessage.error(`任务失败: ${data.errorMessage || '未知错误'}`)
              break
            case 'TIMEOUT':
              console.error('[SSE] 任务执行超时')
              guiStore.setRunningState(false)
              ElMessage.error('任务执行超时')
              break
            case 'ABORTED':
              console.warn(`[SSE] 任务被中止: ${data.message}`)
              guiStore.setRunningState(false)
              ElMessage.warning(`任务被中止: ${data.message || '用户中止'}`)
              break
            case 'SUSPENDED':
              console.log(`[SSE] Agent挂起，等待执行工具: ${data.toolName}`)
              break
            case 'RESUMED':
              console.log(`[SSE] Agent恢复执行，工具执行${data.success ? '成功' : '失败'}`)
              break
            case 'TASK_COMPLETED':
              console.log('[SSE] 任务完成，结果:', data.results)
              guiStore.setRunningState(false)
              ElMessage.success('任务执行完成！')
              break
            case 'TASK_FAILED':
              console.error(`[SSE] 任务失败: ${data.errorMessage}`)
              guiStore.setRunningState(false)
              ElMessage.error(`任务失败: ${data.errorMessage || '未知错误'}`)
              break
            default:
              console.log('[SSE] 未知状态:', data.status)
          }
        } catch (err) {
          console.error('[SSE] 解析数据失败:', err)
        }
      }
    },

    onerror(err) {
      console.error('[SSE] 连接错误:', err)
      // 自动重试由 fetchEventSource 内部处理
      // 如果是主动 abort 则不重试
      if (sseAbortController?.signal.aborted) {
        throw err // 抛出错误停止重试
      }
    },

    onclose() {
      console.log('[SSE] 连接已关闭')
    }
  }).catch((err) => {
    if (err.name !== 'AbortError') {
      console.error('[SSE] 连接异常:', err)
    }
  })
}

/**
 * 断开 SSE 连接
 */
const disconnectSse = () => {
  if (sseAbortController) {
    sseAbortController.abort()
    sseAbortController = null
  }
}

// ========== VNC 连接 ==========

/**
 * 连接 VNC（主入口）
 * 流程：清理旧资源 → 提交任务 → 存储状态 → 渲染 VNC → 建立 SSE
 */
const handleConnectVnc = async () => {
  if (connecting.value) return

  // 检查是否选择了环境配置
  if (!selectedProfile.value) {
    ElMessage.warning('请先选择环境配置再连接 VNC')
    return
  }

  connecting.value = true

  try {
    // 1. 清理旧资源
    await cleanupOldResources()

    // 2. 提取选中的配置名
    const targetProfile = selectedProfile.value ? String(selectedProfile.value.profileName || selectedProfile.value) : ''

    // 3. 生成clientId
    const clientId = uuidv4()

    // 4. 组装V3接口参数（端口由后端自动分配）
    const payload = {
      profileName: targetProfile,
      clientId
    }

    // 5. 调用V3连接接口
    const result = await setupFarmEnvironment(payload)

    // 6. 解析V3返回结果（数据在data字段下）
    const containerId = result.data.containerId
    const taskId = result.data.taskId
    const vncPort = result.data.vncPort

    if (!containerId || !taskId || !vncPort) {
      throw new Error('返回数据缺少必要字段 (containerId/taskId/vncPort)')
    }

    // 7. 存入全局状态
    guiStore.setGuiInfo(containerId, taskId, vncPort)

    // 8. 关闭 loading 状态并等待 DOM 更新（修复 VNC 挂载目标丢失问题）
    connecting.value = false
    await nextTick()

    // 9. 动态渲染 VNC
    await renderVnc(vncPort)

    // 10. 建立 SSE 连接（使用clientId而非containerId）
    connectSse(clientId)

    ElMessage.success('VNC 连接成功，SSE 已建立')
  } catch (error) {
    console.error('连接 VNC 失败:', error)
    ElMessage.error(`连接失败: ${error.message || '请检查网络或服务状态'}`)
    connecting.value = false // 确保报错时恢复按钮状态
  }
}

/**
 * 清理旧资源（旧 VNC 实例 + 旧容器 + 旧 SSE）
 */
const cleanupOldResources = async () => {
  guiStore.clearLogs()
  // 断开旧 VNC
  if (rfbInstance) {
    try {
      rfbInstance.disconnect()
    } catch (e) {
      console.warn('断开旧 VNC 实例失败:', e)
    }
    rfbInstance = null
    connected.value = false
  }

  // 断开旧 SSE
  disconnectSse()

  // 停止旧容器
  if (guiStore.containerId) {
    try {
      await stopContainer(guiStore.containerId)
      console.log('旧容器已停止:', guiStore.containerId)
    } catch (e) {
      console.warn('停止旧容器失败（可能已销毁）:', e)
    }
    guiStore.clearGuiInfo()
  }
}

/**
 * 渲染 VNC 画面
 * @param {number} port - VNC 端口号
 */
const renderVnc = async (port) => {
  if (!vncContainer.value) {
    throw new Error('VNC 容器 DOM 未就绪')
  }

  const { default: RFB } = await import('@novnc/novnc')

  const vncUrl = `ws://${VNC_HOST}:${port}/websockify`

  rfbInstance = new RFB(vncContainer.value, vncUrl, {
    credentials: { password: '' }
  })

  rfbInstance.scaleViewport = true
  rfbInstance.resizeSession = true

  return new Promise((resolve, reject) => {
    const onConnect = () => {
      connected.value = true
      rfbInstance.removeEventListener('connect', onConnect)
      resolve()
    }

    const onDisconnect = (e) => {
      connected.value = false
      rfbInstance.removeEventListener('disconnect', onDisconnect)
      if (!e.detail.clean) {
        ElMessage.warning('VNC 连接异常断开')
      }
    }

    rfbInstance.addEventListener('connect', onConnect)
    rfbInstance.addEventListener('disconnect', onDisconnect)

    rfbInstance.addEventListener('credentialsrequired', () => {
      rfbInstance.sendCredentials({ password: '' })
    })

    // 超时处理
    setTimeout(() => {
      if (!connected.value) {
        reject(new Error('VNC 连接超时'))
      }
    }, 15000)
  })
}

// ========== 中断任务 ==========

/**
 * 中断当前正在执行的任务
 * 仅打断指令进程，不销毁容器和 VNC 连接
 */
const handleAbortTask = async () => {
  if (!guiStore.taskId) return

  try {
    await abortTask(guiStore.taskId)
    ElMessage.success('任务已成功中断')
    guiStore.setRunningState(false)
  } catch (error) {
    console.error('中断任务失败:', error)
    ElMessage.error(`中断任务失败: ${error.message || '请稍后重试'}`)
  }
}

/**
 * 获取环境配置列表
 */
const fetchProfiles = async () => {
  try {
    const data = await getFarmProfiles()
    profileList.value = Array.isArray(data) ? data : []
  } catch (error) {
    console.error('获取配置列表失败:', error)
  }
}

// ========== 生命周期 ==========

onMounted(() => {
  // 不自动连接，等待用户点击按钮
  fetchProfiles() // 新增：组件挂载时拉取配置列表
})

onBeforeUnmount(() => {
  // 断开 VNC
  if (rfbInstance) {
    try {
      rfbInstance.disconnect()
    } catch (e) {
      console.warn('销毁时断开 VNC 失败:', e)
    }
    rfbInstance = null
  }
  // 断开 SSE
  disconnectSse()
})

// 暴露方法供父组件调用
defineExpose({
  disconnectSse,
  cleanupOldResources
})
</script>

<style scoped>
.vnc-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.vnc-card :deep(.el-card__body) {
  flex: 1;
  padding: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 16px;
  font-weight: bold;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.status-tag {
  flex-shrink: 0;
}

.vnc-container {
  flex: 1;
  position: relative;
  min-height: 400px;
  background: #1a1a2e;
  border-radius: 0 0 4px 4px;
  overflow: hidden; /* 新增：严防溢出 */
}

.vnc-screen {
  width: 100%;
  height: 100%;
  /* 新增：Flex 居中，确保内部画布完美居中 */
  display: flex;
  align-items: center;
  justify-content: center;
}

.vnc-screen :deep(canvas) {
  display: block;
  /* 移除原有的 margin: 0 auto */
  max-width: 100%;
  max-height: 100%;
  object-fit: contain; /* 核心修复：保持宽高比缩放，确保画面不被截断 */
}

.vnc-placeholder {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  color: #c0c4cc;
}

.vnc-placeholder p {
  font-size: 14px;
  margin: 0;
}

</style>
