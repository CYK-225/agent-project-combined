<template>
  <el-card class="task-overview-card" shadow="never">
    <template #header>
      <div class="card-header">
        <div class="header-left">
          <span class="card-title">任务概览</span>
          <el-tag type="info" size="small">{{ tasks.length }} 个任务</el-tag>
        </div>
        <div class="header-right">
          <el-button type="danger" size="small" @click="handleClearAll" :disabled="tasks.length === 0">
            清空所有
          </el-button>
        </div>
      </div>
    </template>

    <el-scrollbar :height="scrollHeight">
      <div v-if="tasks.length === 0" class="empty-state">
        <el-empty description="暂无任务，请先创建任务" :image-size="80" />
      </div>

      <div v-else class="task-list">
        <div
          v-for="task in tasks"
          :key="task.taskId"
          class="task-item"
          :class="{ 'task-item-running': task.status === 'RUNNING' || task.status === 'PENDING' }"
        >
          <div class="task-header">
            <div class="task-id-row">
              <span class="task-id">任务 #{{ task.taskId }}</span>
              <el-tag :type="getStatusType(task.status)" size="small">
                {{ getStatusText(task.status) }}
              </el-tag>
            </div>
            <div class="task-actions">
              <el-button
                v-if="task.status === 'RUNNING' || task.status === 'PENDING'"
                type="danger"
                size="small"
                link
                @click="handleCancel(task.taskId)"
              >
                取消
              </el-button>
              <el-button
                type="primary"
                size="small"
                link
                @click="handleViewLog(task)"
              >
                查看日志
              </el-button>
            </div>
          </div>

          <div class="task-info">
            <span class="info-item">
              <el-icon><VideoPlay /></el-icon>
              步骤: {{ task.currentStep }}
            </span>
            <span class="info-item">
              <el-icon><Document /></el-icon>
              日志: {{ task.toolLogs.length }} 条
            </span>
          </div>

          <!-- 最近日志预览 -->
          <div v-if="task.toolLogs.length > 0" class="recent-logs">
            <div class="recent-log-item" v-for="(log, idx) in getRecentLogs(task)" :key="idx">
              <el-tag :type="getLogTagType(log)" size="small" class="log-tag">
                {{ getLogTagText(log) }}
              </el-tag>
              <span class="log-text">{{ getLogDisplayText(log) }}</span>
            </div>
          </div>

          <!-- 错误信息 -->
          <div v-if="task.errorMessage" class="error-info">
            <el-alert :title="task.errorMessage" type="error" :closable="false" show-icon size="small" />
          </div>
        </div>
      </div>
    </el-scrollbar>

    <!-- 日志详情弹窗 -->
    <V3LogDialog
      v-model="logDialogVisible"
      :task="currentLogTask"
      @clear="handleClearTaskLogs"
    />
  </el-card>
</template>

<script setup>
import { ref } from 'vue'
import { VideoPlay, Document } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import V3LogDialog from './V3LogDialog.vue'

const props = defineProps({
  tasks: {
    type: Array,
    default: () => []
  },
  scrollHeight: {
    type: String,
    default: '500px'
  }
})

const emit = defineEmits(['cancel', 'clear-all', 'clear-task-logs'])

const logDialogVisible = ref(false)
const currentLogTask = ref(null)

const getStatusText = (status) => {
  const map = {
    'PENDING': '排队中',
    'RUNNING': '执行中',
    'SUCCESS': '成功',
    'FAILED': '失败'
  }
  return map[status] || status
}

const getStatusType = (status) => {
  const map = {
    'PENDING': 'warning',
    'RUNNING': 'primary',
    'SUCCESS': 'success',
    'FAILED': 'danger'
  }
  return map[status] || 'info'
}

const getRecentLogs = (task) => {
  // 获取最近 3 条日志
  return task.toolLogs.slice(-3)
}

const getLogTagType = (log) => {
  if (log.type === 'task_failed') return 'danger'
  if (log.type === 'task_completed' || log.type === 'step_completed') return 'success'
  if (log.toolName) return 'primary'
  return 'info'
}

const getLogTagText = (log) => {
  if (log.type === 'task_failed') return '失败'
  if (log.type === 'task_completed') return '完成'
  if (log.type === 'step_completed') return '步骤'
  if (log.toolName) return log.toolName
  return '日志'
}

const getLogDisplayText = (log) => {
  if (log.type === 'task_failed') return log.errorMessage || '任务失败'
  if (log.type === 'task_completed') return '所有步骤执行成功'
  if (log.type === 'step_completed') return `步骤 ${log.step} 已完成`
  if (log.toolName) return log.outputResult || log.toolInput || ''
  return ''
}

const handleViewLog = (task) => {
  currentLogTask.value = task
  logDialogVisible.value = true
}

const handleCancel = (taskId) => {
  emit('cancel', taskId)
}

const handleClearAll = async () => {
  try {
    await ElMessageBox.confirm('确定要清空所有任务吗？', '确认清空', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    emit('clear-all')
  } catch {
    // 用户取消
  }
}

const handleClearTaskLogs = () => {
  if (currentLogTask.value) {
    emit('clear-task-logs', currentLogTask.value.taskId)
    ElMessage.success('日志已清空')
  }
}
</script>

<style scoped>
.task-overview-card {
  background-color: #fff;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.task-overview-card :deep(.el-card__body) {
  flex: 1;
  padding: 16px;
  overflow: hidden;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.card-title {
  font-size: 16px;
  font-weight: bold;
  color: #303133;
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.task-item {
  padding: 16px;
  background-color: #f5f7fa;
  border-radius: 8px;
  border: 2px solid transparent;
  transition: all 0.2s;
}

.task-item:hover {
  background-color: #ecf5ff;
}

.task-item-running {
  border-color: #409eff;
  background-color: #ecf5ff;
}

.task-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.task-id-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.task-id {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.task-actions {
  display: flex;
  gap: 8px;
}

.task-info {
  display: flex;
  gap: 20px;
  margin-bottom: 12px;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #606266;
}

.recent-logs {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-top: 12px;
  border-top: 1px solid #e4e7ed;
}

.recent-log-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.log-tag {
  flex-shrink: 0;
}

.log-text {
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.error-info {
  margin-top: 12px;
}
</style>
