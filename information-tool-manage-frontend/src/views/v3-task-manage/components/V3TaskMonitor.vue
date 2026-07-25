<template>
  <el-card class="task-monitor-card" shadow="never">
    <template #header>
      <div class="card-header">
        <span class="card-title">任务状态监控</span>
        <el-button
          type="primary"
          size="small"
          :loading="loading"
          @click="handleQueryResults"
        >
          查询结果
        </el-button>
      </div>
    </template>

    <div class="monitor-content">
      <!-- 任务基本信息 -->
      <div class="task-info">
        <div class="info-item">
          <span class="info-label">任务 ID：</span>
          <el-tag type="info" size="small">{{ task.taskId }}</el-tag>
        </div>
        <div class="info-item">
          <span class="info-label">任务状态：</span>
          <el-tag :type="statusType" size="small">{{ statusText }}</el-tag>
        </div>
        <div class="info-item">
          <span class="info-label">当前步骤：</span>
          <span class="info-value">{{ task.currentStep }}</span>
        </div>
      </div>

      <!-- 排队信息 -->
      <div v-if="task.queuePosition >= 0" class="queue-info">
        <el-alert
          :title="task.queueDisplayText"
          type="warning"
          :closable="false"
          show-icon
        />
      </div>

      <!-- 错误信息 -->
      <div v-if="task.errorMessage" class="error-info">
        <el-alert
          :title="task.errorMessage"
          type="error"
          :closable="false"
          show-icon
        />
      </div>

      <!-- 步骤进度 -->
      <div v-if="task.status === 'RUNNING'" class="step-progress">
        <div class="progress-title">执行进度</div>
        <el-progress
          :percentage="progressPercentage"
          :status="progressStatus"
          :stroke-width="10"
        />
      </div>

      <!-- 操作按钮 -->
      <div v-if="task.status === 'RUNNING' || task.status === 'PENDING'" class="actions">
        <el-button
          type="danger"
          :loading="task.isCancelling"
          :disabled="task.isCancelling"
          @click="handleCancel"
        >
          取消任务
        </el-button>
      </div>

      <!-- 任务结果 -->
      <div v-if="task.taskResults" class="task-results">
        <div class="results-title">任务结果</div>
        <el-scrollbar max-height="200px">
          <pre class="results-content">{{ JSON.stringify(task.taskResults, null, 2) }}</pre>
        </el-scrollbar>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  task: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['cancel', 'query-results'])

const loading = ref(false)

const statusText = computed(() => {
  const map = {
    'PENDING': '排队中',
    'RUNNING': '执行中',
    'SUCCESS': '成功',
    'FAILED': '失败'
  }
  return map[props.task.status] || props.task.status
})

const statusType = computed(() => {
  const map = {
    'PENDING': 'warning',
    'RUNNING': 'primary',
    'SUCCESS': 'success',
    'FAILED': 'danger'
  }
  return map[props.task.status] || 'info'
})

const progressPercentage = computed(() => {
  return props.task.currentStep > 0 ? Math.min(100, props.task.currentStep * 20) : 0
})

const progressStatus = computed(() => {
  if (props.task.status === 'SUCCESS') return 'success'
  if (props.task.status === 'FAILED') return 'exception'
  return undefined
})

const handleCancel = () => {
  emit('cancel')
}

const handleQueryResults = async () => {
  loading.value = true
  try {
    emit('query-results')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.task-monitor-card {
  background-color: #fff;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-size: 16px;
  font-weight: bold;
  color: #303133;
}

.monitor-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.task-info {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.info-label {
  font-size: 14px;
  color: #606266;
  white-space: nowrap;
}

.info-value {
  font-size: 14px;
  color: #303133;
}

.queue-info,
.error-info {
  margin-top: 8px;
}

.step-progress {
  margin-top: 8px;
}

.progress-title {
  font-size: 14px;
  font-weight: bold;
  color: #303133;
  margin-bottom: 8px;
}

.actions {
  margin-top: 8px;
}

.task-results {
  margin-top: 8px;
}

.results-title {
  font-size: 14px;
  font-weight: bold;
  color: #303133;
  margin-bottom: 8px;
}

.results-content {
  font-size: 12px;
  color: #606266;
  background-color: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
