<template>
  <div class="gui-manage-container">
    <div class="gui-layout">
      <!-- 区域一：VNC 监控视图 -->
      <div class="gui-left">
        <VncViewer ref="vncViewerRef" @view-log="activeRightPanel = 'log'" />
      </div>

      <!-- 右侧区域 -->
      <div class="gui-right">
        <div class="gui-right-prompt-wrapper" v-show="activeRightPanel === 'prompt'">
          <div class="gui-right-top">
            <!-- 步骤提示词构建区 -->
            <PromptBuilder v-show="activeBuilderType === 'step'" v-model:list="builderList" @run-single="handleRunSingle" @switch-to-system="activeBuilderType = 'system'" />
            <!-- 系统提示词构建区 -->
            <SystemPromptBuilder v-show="activeBuilderType === 'system'" v-model:list="systemBuilderList" @run-single="handleRunSingle" @switch-to-step="activeBuilderType = 'step'" />
          </div>
          <div class="gui-right-bottom">
            <!-- 步骤提示词编排区 -->
            <PromptSequencer v-show="activeSequencerType === 'step'" v-model:sequence-list="sequenceList" :system-prompt-list="systemSequenceList" @update:system-prompt-list="systemSequenceList = $event" @run="handleRun" @switch-to-system="activeSequencerType = 'system'" />
            <!-- 系统提示词编排区 -->
            <SystemPromptSequencer v-show="activeSequencerType === 'system'" v-model:system-prompt-list="systemSequenceList" @switch-to-step="activeSequencerType = 'step'" />
          </div>
        </div>

        <div class="gui-right-log-wrapper" v-if="activeRightPanel === 'log'">
          <el-card class="log-panel-card" shadow="never">
            <template #header>
              <div class="log-header">
                <span class="log-title">任务执行日志</span>
                <div class="log-actions">
                  <el-input
                    v-model="logSearchKeyword"
                    placeholder="搜索日志..."
                    size="small"
                    clearable
                    style="width: 180px; margin-right: 8px;"
                  >
                    <template #prefix><el-icon><Search /></el-icon></template>
                  </el-input>
                  <el-button type="danger" size="small" plain @click="handleClearLogs">
                    <el-icon><Delete /></el-icon>清空
                  </el-button>
                  <el-button type="info" size="small" @click="activeRightPanel = 'prompt'">
                    <el-icon><Close /></el-icon>关闭日志
                  </el-button>
                </div>
              </div>
            </template>
            
            <el-scrollbar ref="logScrollbarRef" class="log-scrollbar">
              <el-empty v-if="filteredLogs.length === 0" description="暂无执行日志，等待后端推送..." />
              <el-timeline v-else>
                <el-timeline-item
                  v-for="(log, index) in filteredLogs"
                  :key="index"
                  :type="getTimelineItemType(log.status)"
                  :timestamp="getTimelineItemTimestamp(log)"
                  placement="top"
                >
                  <el-card shadow="never" class="log-card">
                    <div v-if="log.instruction" class="log-item">
                      <span class="log-label">指令:</span> <span class="log-value">{{ log.instruction }}</span>
                    </div>
                    <div v-if="log.action" class="log-item">
                      <span class="log-label">动作:</span> <span class="log-value">{{ log.action }}</span>
                    </div>
                    <div v-if="log.result || log.message" class="log-item" :class="{'text-danger': ['timeout', 'failure', 'processing_error'].includes(log.status)}">
                      <span class="log-label">结果:</span> <span class="log-value">{{ log.result || log.message }}</span>
                    </div>
                  </el-card>
                </el-timeline-item>
              </el-timeline>
            </el-scrollbar>
          </el-card>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, computed, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Delete, Close } from '@element-plus/icons-vue'
import { useGuiStore } from '@/store/guiStore'
import { stopContainer } from '@/api/guiManage'
import { executeV3Task } from '@/api/guiConfig'
import VncViewer from './components/VncViewer.vue'
import PromptBuilder from './components/PromptBuilder.vue'
import SystemPromptBuilder from './components/SystemPromptBuilder.vue'
import PromptSequencer from './components/PromptSequencer.vue'
import SystemPromptSequencer from './components/SystemPromptSequencer.vue'

const guiStore = useGuiStore()
const vncViewerRef = ref(null)

// 根据SSE事件状态返回时间线项目类型
const getTimelineItemType = (status) => {
  switch (status) {
    case 'FAILED':
    case 'TIMEOUT':
    case 'TASK_FAILED':
      return 'danger'
    case 'COMPLETED':
    case 'TASK_COMPLETED':
      return 'success'
    case 'ABORTED':
      return 'warning'
    case 'RUNNING':
    case 'STEP_RUNNING':
    case 'STEP_COMPLETED':
    case 'SUSPENDED':
    case 'RESUMED':
      return 'primary'
    default:
      return 'info'
  }
}

// 根据SSE事件状态返回时间线项目时间戳
const getTimelineItemTimestamp = (log) => {
  switch (log.status) {
    case 'RUNNING':
      if (log.toolName) {
        return `步骤 ${log.step}-${log.toolStep}`
      }
      return '任务开始'
    case 'STEP_RUNNING':
      return `步骤 ${log.step} 开始`
    case 'STEP_COMPLETED':
      return `步骤 ${log.step} 完成`
    case 'COMPLETED':
      return '所有步骤完成'
    case 'FAILED':
      return `步骤 ${log.step || ''} 失败`
    case 'TIMEOUT':
      return '任务超时'
    case 'ABORTED':
      return `步骤 ${log.step || ''} 中止`
    case 'SUSPENDED':
      return `步骤 ${log.step || ''} 挂起`
    case 'RESUMED':
      return `步骤 ${log.step || ''}-${log.toolStep || ''} 恢复`
    case 'TASK_COMPLETED':
      return '工作流完成'
    case 'TASK_FAILED':
      return '工作流失败'
    default:
      return log.status || '任务通知'
  }
}

// 构建区列表数据（初始为空，通过新增或导入填充）
const builderList = ref([])

// 系统提示词构建区列表数据（导入区）
const systemBuilderList = ref([])

// 系统提示词编排区列表数据（通过拖拽从导入区添加）
const systemSequenceList = ref([])

// 执行流程列表数据
const sequenceList = ref([])

// ========== 提示词构建区切换状态 ==========
const activeBuilderType = ref('step') // 'step' | 'system'

// ========== 编排区切换状态 ==========
const activeSequencerType = ref('step') // 'step' | 'system'

// ========== 动态面板与日志状态 ==========
const activeRightPanel = ref('prompt') // 'prompt' | 'log'
const logSearchKeyword = ref('')
const logScrollbarRef = ref(null)

const filteredLogs = computed(() => {
  if (!logSearchKeyword.value) {
    return guiStore.taskLogs
  }
  const keyword = logSearchKeyword.value.toLowerCase()
  return guiStore.taskLogs.filter(log => {
    return (
      (log.instruction && log.instruction.toLowerCase().includes(keyword)) ||
      (log.action && log.action.toLowerCase().includes(keyword)) ||
      (log.result && log.result.toLowerCase().includes(keyword)) ||
      (log.message && log.message.toLowerCase().includes(keyword)) ||
      (log.status && log.status.toLowerCase().includes(keyword)) ||
      (log.step && String(log.step).includes(keyword))
    )
  })
})

const handleClearLogs = () => {
  guiStore.clearLogs()
  ElMessage.success('日志已清空')
}

// 自动滚动日志到最新
watch(() => guiStore.taskLogs.length, () => {
  if (activeRightPanel.value === 'log') {
    nextTick(() => {
      if (logScrollbarRef.value) {
        const scrollWrap = logScrollbarRef.value.$el.querySelector('.el-scrollbar__wrap')
        if (scrollWrap) scrollWrap.scrollTop = scrollWrap.scrollHeight
      }
    })
  }
})

// ========== 单步运行（步骤六） ==========
const handleRunSingle = async (element) => {
  if (!element) return ElMessage.warning('无效的提示词卡片')
  if (!guiStore.taskId) return ElMessage.warning('请先连接 VNC 后再运行')

  guiStore.setRunningState(true)
  try {
    // 构建步骤提示词数组
    const stepPrompts = [{
      content: element.content,
      step: 1
    }]

    // 获取系统提示词列表（从编排区获取）
    const sysPrompts = systemSequenceList.value.map(item => ({
      id: item.id,
      content: item.content,
      title: item.title
    }))

    // 调用V3执行接口
    await executeV3Task({
      taskId: guiStore.taskId,
      sysPrompts,
      stepPrompts
    })
    ElMessage.success(`指令已下发: ${element.title}`)
  } catch (error) {
    guiStore.setRunningState(false)
    ElMessage.error(`指令下发失败: ${error.message || '网络错误'}`)
  }
}

// ========== 流程组装运行（步骤七） ==========
// 流程组装运行（接收已展平的 instructions 数组和系统提示词）
const handleRun = async ({ instructions, sysPrompts }) => {
  if (!instructions || instructions.length === 0) {
    ElMessage.warning('请至少勾选一个步骤后再运行')
    return
  }
  if (!guiStore.taskId) return ElMessage.warning('请先连接 VNC 后再运行')

  guiStore.setRunningState(true)
  try {
    // 构建步骤提示词数组
    const stepPrompts = instructions.map((content, index) => ({
      content,
      step: index + 1
    }))

    // 调用V3执行接口
    await executeV3Task({
      taskId: guiStore.taskId,
      sysPrompts: sysPrompts || [],
      stepPrompts
    })
    ElMessage.success(`已提交 ${instructions.length} 条组合执行指令`)
  } catch (error) {
    guiStore.setRunningState(false)
    ElMessage.error(`指令下发失败: ${error.message || '网络错误'}`)
  }
}

// ========== 生命周期管理（步骤五） ==========

/**
 * 停止并清理容器资源
 */
const cleanupContainer = async () => {
  if (guiStore.containerId) {
    try {
      await stopContainer(guiStore.containerId)
      console.log('容器已停止:', guiStore.containerId)
    } catch (e) {
      console.warn('停止容器失败:', e)
    }
    guiStore.clearGuiInfo()
  }
}

/**
 * 浏览器关闭/刷新时的清理处理
 * 使用 navigator.sendBeacon 或同步 XMLHttpRequest 确保请求发出
 */
const handleUnload = () => {
  if (!guiStore.containerId) return

  const containerId = guiStore.containerId
  const token = localStorage.getItem('token') || ''
  const baseUrl = import.meta.env.VITE_API_BASE || 'http://8.129.128.167:8081'
  const url = `${baseUrl}/api/v3/agent/container/${containerId}`

  // 优先使用 sendBeacon（异步但不会被浏览器拦截）
  try {
    const blob = new Blob([JSON.stringify({})], { type: 'application/json' })
    const sent = navigator.sendBeacon(url + '?_method=DELETE', blob)
    if (sent) {
      console.log('sendBeacon 发送成功')
      return
    }
  } catch (e) {
    console.warn('sendBeacon 失败，回退到 XMLHttpRequest:', e)
  }

  // 回退：使用同步 XMLHttpRequest（会阻塞页面卸载，但保证请求发出）
  try {
    const xhr = new XMLHttpRequest()
    xhr.open('DELETE', url, false) // 同步
    xhr.setRequestHeader('Authorization', `Bearer ${token}`)
    xhr.setRequestHeader('Content-Type', 'application/json')
    xhr.send()
  } catch (e) {
    console.warn('同步 XHR 发送失败:', e)
  }
}

onMounted(() => {
  window.addEventListener('beforeunload', handleUnload)
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleUnload)
  // 组件销毁时清理容器
  cleanupContainer()
})
</script>

<style scoped>
.gui-manage-container {
  padding: 16px;
  height: calc(100vh - 120px);
  box-sizing: border-box;
}

.gui-layout {
  display: flex;
  gap: 16px;
  height: 100%;
}

/* 左侧区域一：VNC 监控 */
.gui-left {
  flex: 1;
  min-width: 0;
}

/* 右侧区域 */
.gui-right {
  width: 50%;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

/* 右上：提示词构建区 */
.gui-right-top {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

/* 右下：提示词排序区 */
.gui-right-bottom {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

/* ========== 动态右侧面板与日志样式 ========== */
.gui-right-prompt-wrapper {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
}

.gui-right-log-wrapper {
  height: 100%;
}

.log-panel-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.log-panel-card :deep(.el-card__body) {
  flex: 1;
  padding: 16px;
  overflow: hidden;
}

.log-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.log-title {
  font-size: 16px;
  font-weight: bold;
}

.log-actions {
  display: flex;
  align-items: center;
}

.log-scrollbar {
  height: 100%;
  padding-right: 16px;
}

.log-card {
  --el-card-padding: 12px;
  background-color: #f8f9fa;
  border: 1px solid #ebeef5;
}

.log-item {
  margin-bottom: 6px;
  font-size: 13px;
  line-height: 1.5;
}

.log-item:last-child {
  margin-bottom: 0;
}

.log-label {
  font-weight: bold;
  color: #606266;
  margin-right: 4px;
}

.log-value {
  color: #303133;
  word-break: break-all;
}

.text-danger .log-value {
  color: #f56c6c;
}
</style>
