<template>
  <div class="v3-task-manage-container">
    <!-- 左侧：任务创建区 -->
    <div class="left-panel">
      <V3TaskCreate
        :disabled="false"
        @create="handleCreateTask"
        @connect="handleConnect"
      />
    </div>

    <!-- 右侧：任务概览区 -->
    <div class="right-panel">
      <V3TaskOverview
        :tasks="v3Store.tasksList"
        scroll-height="calc(100vh - 200px)"
        @cancel="handleCancelTask"
        @clear-all="handleClearAll"
        @clear-task-logs="handleClearTaskLogs"
      />
    </div>
  </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { useV3TaskStore } from '@/store/v3TaskStore'
import V3TaskCreate from './components/V3TaskCreate.vue'
import V3TaskOverview from './components/V3TaskOverview.vue'

const v3Store = useV3TaskStore()

/**
 * 建立 SSE 连接
 */
const handleConnect = () => {
  v3Store.connectSSE()
  ElMessage.success('正在建立 SSE 连接...')
}

/**
 * 创建任务
 */
const handleCreateTask = async (formData) => {
  try {
    const result = await v3Store.createTask(formData)
    if (result.taskIds) {
      // 批量创建
      ElMessage.success(result.message || `批量创建完成，成功${result.successCount}个`)
    } else {
      // 单个创建
      ElMessage.success(`任务创建成功，任务ID: ${result.taskId}`)
    }
  } catch (error) {
    ElMessage.error(error.message || '任务创建失败')
  }
}

/**
 * 取消任务
 */
const handleCancelTask = async (taskId) => {
  try {
    const success = await v3Store.cancelTask(taskId)
    if (success) {
      ElMessage.success('任务已取消')
    } else {
      ElMessage.error('取消任务失败')
    }
  } catch (error) {
    ElMessage.error(error.message || '取消任务失败')
  }
}

/**
 * 清空所有任务
 */
const handleClearAll = () => {
  v3Store.clearAllTasks()
  ElMessage.success('已清空所有任务')
}

/**
 * 清空指定任务日志
 */
const handleClearTaskLogs = (taskId) => {
  v3Store.clearTaskLogs(taskId)
}

/**
 * 组件挂载时生成 clientId
 */
onMounted(() => {
  v3Store.generateClientId()
})

/**
 * 组件卸载时断开连接
 */
onBeforeUnmount(() => {
  v3Store.disconnectSSE()
})
</script>

<style scoped>
.v3-task-manage-container {
  padding: 20px;
  height: calc(100vh - 120px);
  display: flex;
  gap: 20px;
}

.left-panel {
  width: 420px;
  flex-shrink: 0;
}

.right-panel {
  flex: 1;
  min-width: 0;
}
</style>
