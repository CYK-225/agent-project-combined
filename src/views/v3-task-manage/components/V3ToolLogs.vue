<template>
  <el-card class="tool-logs-card" shadow="never">
    <template #header>
      <div class="card-header">
        <span class="card-title">工具调用日志</span>
        <div class="header-actions">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索日志..."
            size="small"
            clearable
            style="width: 180px; margin-right: 8px;"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button type="danger" size="small" plain @click="handleClear">
            <el-icon><Delete /></el-icon>清空
          </el-button>
        </div>
      </div>
    </template>

    <el-scrollbar ref="scrollbarRef" class="logs-scrollbar">
      <el-empty v-if="filteredLogs.length === 0" description="暂无执行日志，等待后端推送..." />
      <el-timeline v-else>
        <el-timeline-item
          v-for="(log, index) in filteredLogs"
          :key="index"
          :type="getLogType(log)"
          :timestamp="getLogTimestamp(log)"
          placement="top"
        >
          <el-card shadow="never" class="log-card">
            <!-- 工具调用日志 -->
            <template v-if="log.toolName">
              <div class="log-item">
                <span class="log-label">工具：</span>
                <el-tag size="small" type="primary">{{ log.toolName }}</el-tag>
              </div>
              <div v-if="log.toolInput" class="log-item">
                <span class="log-label">输入：</span>
                <span class="log-value">{{ formatJson(log.toolInput) }}</span>
              </div>
              <div v-if="log.outputResult" class="log-item">
                <span class="log-label">结果：</span>
                <span class="log-value">{{ log.outputResult }}</span>
              </div>
              <div class="log-item">
                <span class="log-label">步骤：</span>
                <span class="log-value">步骤 {{ log.step }} - 工具 {{ log.toolStep }}</span>
              </div>
            </template>

            <!-- 步骤完成日志 -->
            <template v-else-if="log.type === 'step_completed'">
              <div class="log-item">
                <span class="log-label">步骤完成：</span>
                <span class="log-value">步骤 {{ log.step }} 已完成</span>
              </div>
            </template>

            <!-- 任务完成日志 -->
            <template v-else-if="log.type === 'task_completed'">
              <div class="log-item">
                <span class="log-label">任务完成：</span>
                <span class="log-value text-success">所有步骤执行成功</span>
              </div>
            </template>

            <!-- 任务失败日志 -->
            <template v-else-if="log.type === 'task_failed'">
              <div class="log-item">
                <span class="log-label">任务失败：</span>
                <span class="log-value text-danger">{{ log.errorMessage || '未知错误' }}</span>
              </div>
            </template>
          </el-card>
        </el-timeline-item>
      </el-timeline>
    </el-scrollbar>
  </el-card>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { Search, Delete } from '@element-plus/icons-vue'

const props = defineProps({
  logs: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['clear'])

const searchKeyword = ref('')
const scrollbarRef = ref(null)

const filteredLogs = computed(() => {
  if (!searchKeyword.value) {
    return props.logs
  }
  const keyword = searchKeyword.value.toLowerCase()
  return props.logs.filter(log => {
    return (
      (log.toolName && log.toolName.toLowerCase().includes(keyword)) ||
      (log.toolInput && log.toolInput.toLowerCase().includes(keyword)) ||
      (log.outputResult && log.outputResult.toLowerCase().includes(keyword)) ||
      (log.errorMessage && log.errorMessage.toLowerCase().includes(keyword))
    )
  })
})

const getLogType = (log) => {
  if (log.type === 'task_failed') return 'danger'
  if (log.type === 'task_completed') return 'success'
  if (log.type === 'step_completed') return 'success'
  return 'primary'
}

const getLogTimestamp = (log) => {
  if (log.type === 'step_completed') return `步骤 ${log.step} 完成`
  if (log.type === 'task_completed') return '任务完成'
  if (log.type === 'task_failed') return '任务失败'
  if (log.step && log.toolStep) return `步骤 ${log.step} - 工具 ${log.toolStep}`
  return ''
}

const formatJson = (jsonStr) => {
  try {
    const obj = typeof jsonStr === 'string' ? JSON.parse(jsonStr) : jsonStr
    return JSON.stringify(obj, null, 2)
  } catch {
    return jsonStr
  }
}

const handleClear = () => {
  emit('clear')
}

// 自动滚动到最新日志
watch(() => props.logs.length, () => {
  nextTick(() => {
    if (scrollbarRef.value) {
      const scrollWrap = scrollbarRef.value.$el.querySelector('.el-scrollbar__wrap')
      if (scrollWrap) {
        scrollWrap.scrollTop = scrollWrap.scrollHeight
      }
    }
  })
})
</script>

<style scoped>
.tool-logs-card {
  height: 100%;
  display: flex;
  flex-direction: column;
  background-color: #fff;
}

.tool-logs-card :deep(.el-card__body) {
  flex: 1;
  padding: 16px;
  overflow: hidden;
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

.header-actions {
  display: flex;
  align-items: center;
}

.logs-scrollbar {
  height: 100%;
  padding-right: 16px;
}

.log-card {
  --el-card-padding: 12px;
  background-color: #f8f9fa;
  border: 1px solid #ebeef5;
}

.log-item {
  margin-bottom: 8px;
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
  white-space: pre-wrap;
}

.text-success {
  color: #67c23a;
}

.text-danger {
  color: #f56c6c;
}
</style>
