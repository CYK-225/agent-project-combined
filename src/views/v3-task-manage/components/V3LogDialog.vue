<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="(val) => emit('update:modelValue', val)"
    :title="`任务 #${task?.taskId || ''} 执行日志`"
    width="800px"
    destroy-on-close
    top="5vh"
  >
    <div class="log-dialog-content" v-if="task">
      <!-- 日志搜索 -->
      <div class="log-search-bar">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索日志..."
          clearable
          style="width: 300px"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <div class="log-stats">
          <el-tag type="success" size="small">{{ stepCount }} 步骤</el-tag>
          <el-tag type="primary" size="small">{{ toolCount }} 工具调用</el-tag>
        </div>
        <el-button type="danger" size="small" @click="handleClear">
          <el-icon><Delete /></el-icon>清空
        </el-button>
      </div>

      <!-- 合并日志时间线 -->
      <el-scrollbar max-height="calc(70vh - 150px)">
        <el-timeline v-if="mergedLogs.length > 0">
          <el-timeline-item
            v-for="(log, index) in mergedLogs"
            :key="index"
            :type="getLogType(log)"
            :timestamp="formatTimestamp(log.timestamp)"
            placement="top"
          >
            <el-card shadow="never" class="log-card">
              <!-- 步骤日志 -->
              <div v-if="log._logType === 'step'" class="log-item">
                <div v-if="log.type === 'step_completed'" class="log-item">
                  <el-tag type="success" size="small" class="log-type-tag">步骤完成</el-tag>
                  <span class="log-text">步骤 {{ log.step }} 已完成</span>
                </div>
                <div v-else-if="log.type === 'task_completed'" class="log-item">
                  <el-tag type="success" size="small" class="log-type-tag">任务完成</el-tag>
                  <span class="log-text">所有步骤执行成功</span>
                </div>
                <div v-else-if="log.type === 'task_failed'" class="log-item">
                  <el-tag type="danger" size="small" class="log-type-tag">任务失败</el-tag>
                  <span class="log-text text-danger">{{ log.errorMessage || '未知错误' }}</span>
                </div>
              </div>

              <!-- 工具调用日志 -->
              <div v-else-if="log._logType === 'tool'" class="log-item">
                <el-tag type="primary" size="small" class="log-type-tag">工具调用</el-tag>
                <span class="log-label">{{ log.toolName }}</span>
                <span class="log-step-info">步骤 {{ log.step }} - 工具 {{ log.toolStep }}</span>
              </div>
              <div v-if="log._logType === 'tool' && log.toolInput" class="log-item tool-detail">
                <span class="log-label">输入：</span>
                <span class="log-text">{{ formatJson(log.toolInput) }}</span>
              </div>
              <div v-if="log._logType === 'tool' && log.outputResult" class="log-item tool-detail">
                <span class="log-label">结果：</span>
                <span class="log-text">{{ log.outputResult }}</span>
              </div>
            </el-card>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无执行日志" :image-size="80" />
      </el-scrollbar>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { Search, Delete } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  task: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['update:modelValue', 'clear'])

const searchKeyword = ref('')

// 合并并排序所有日志
const mergedLogs = computed(() => {
  if (!props.task || !props.task.toolLogs) return []

  let logs = props.task.toolLogs.map(log => {
    if (log.type) {
      return { ...log, _logType: 'step' }
    } else if (log.toolName) {
      return { ...log, _logType: 'tool' }
    }
    return log
  })

  // 搜索过滤
  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase()
    logs = logs.filter(log => {
      if (log._logType === 'step') {
        return (
          (log.errorMessage && log.errorMessage.toLowerCase().includes(keyword)) ||
          (log.type && log.type.toLowerCase().includes(keyword))
        )
      } else if (log._logType === 'tool') {
        return (
          (log.toolName && log.toolName.toLowerCase().includes(keyword)) ||
          (log.toolInput && log.toolInput.toLowerCase().includes(keyword)) ||
          (log.outputResult && log.outputResult.toLowerCase().includes(keyword))
        )
      }
      return false
    })
  }

  // 按时间排序
  return logs.sort((a, b) => {
    const timeA = a.timestamp ? new Date(a.timestamp).getTime() : 0
    const timeB = b.timestamp ? new Date(b.timestamp).getTime() : 0
    return timeA - timeB
  })
})

// 统计数量
const stepCount = computed(() => {
  if (!props.task || !props.task.toolLogs) return 0
  return props.task.toolLogs.filter(log => log.type).length
})

const toolCount = computed(() => {
  if (!props.task || !props.task.toolLogs) return 0
  return props.task.toolLogs.filter(log => log.toolName).length
})

const getLogType = (log) => {
  if (log._logType === 'step') {
    if (log.type === 'task_failed') return 'danger'
    if (log.type === 'task_completed' || log.type === 'step_completed') return 'success'
    return 'primary'
  }
  return 'primary'
}

const formatTimestamp = (timestamp) => {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  return date.toLocaleTimeString('zh-CN', { hour12: false })
}

const formatJson = (jsonStr) => {
  try {
    const obj = typeof jsonStr === 'string' ? JSON.parse(jsonStr) : jsonStr
    return JSON.stringify(obj)
  } catch {
    return jsonStr
  }
}

const handleClear = () => {
  emit('clear')
}
</script>

<style scoped>
.log-dialog-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.log-search-bar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.log-stats {
  display: flex;
  gap: 8px;
  margin-left: auto;
}

.log-card {
  --el-card-padding: 12px;
  background-color: #fff;
}

.log-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 13px;
  line-height: 1.5;
}

.log-item:last-child {
  margin-bottom: 0;
}

.log-type-tag {
  flex-shrink: 0;
}

.log-label {
  font-weight: 600;
  color: #606266;
  white-space: nowrap;
}

.log-text {
  color: #303133;
  word-break: break-all;
}

.log-step-info {
  margin-left: auto;
  font-size: 12px;
  color: #909399;
}

.tool-detail {
  padding-left: 10px;
  border-left: 2px solid #e4e7ed;
  margin-left: 4px;
}

.text-danger {
  color: #f56c6c;
}
</style>
