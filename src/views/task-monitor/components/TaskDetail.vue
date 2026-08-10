<template>
  <el-dialog
    v-model="visible"
    title="任务详情"
    width="700px"
    :close-on-click-modal="false"
  >
    <div v-if="taskData" class="task-detail">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="任务ID" :span="2">
          {{ taskData.id }}
        </el-descriptions-item>
        <el-descriptions-item label="公司名称">
          {{ taskData.companyName }}
        </el-descriptions-item>
        <el-descriptions-item label="公司ID">
          {{ taskData.companyId }}
        </el-descriptions-item>
        <el-descriptions-item label="配置名称">
          {{ taskData.configName }}
        </el-descriptions-item>
        <el-descriptions-item label="任务类型">
          <el-tag :type="taskTypeTagType(taskData.taskType)">
            {{ taskTypeLabel(taskData.taskType) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="任务状态">
          <el-tag :type="statusTagType(taskData.status)">
            {{ statusLabel(taskData.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="重试次数">
          {{ taskData.retryCount }}
        </el-descriptions-item>
        <el-descriptions-item label="是否独占">
          {{ taskData.isExclusive ? '是' : '否' }}
        </el-descriptions-item>
        <el-descriptions-item label="任务标识">
          {{ taskData.mission || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="用户ID">
          {{ taskData.userId }}
        </el-descriptions-item>
        <el-descriptions-item label="创建时间" :span="2">
          {{ formatTime(taskData.createTimeMs) }}
        </el-descriptions-item>
        <el-descriptions-item label="开始时间">
          {{ taskData.startTime || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="最后失败时间">
          {{ taskData.lastFailureTime || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="失败原因" :span="2">
          <span :class="{ 'error-text': taskData.failureReason }">
            {{ taskData.failureReason || '-' }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="网站地址" :span="2">
          <el-link v-if="taskData.webAddress" :href="taskData.webAddress" target="_blank" type="primary">
            {{ taskData.webAddress }}
          </el-link>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">
          {{ taskData.remark || '-' }}
        </el-descriptions-item>
      </el-descriptions>

      <!-- 采集字段详情 -->
      <div v-if="taskData.collectedFields" class="collected-fields">
        <h4 class="section-title">采集字段详情</h4>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="企业名称">
            {{ taskData.collectedFields.entName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="法定代表人">
            {{ taskData.collectedFields.legalPerson || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="注册资本">
            {{ taskData.collectedFields.recConcat || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="成立日期">
            {{ taskData.collectedFields.esDate || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="注册地址">
            {{ taskData.collectedFields.yrAddress || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="联系电话">
            {{ taskData.collectedFields.telList || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="经营范围">
            <div class="scope-text">{{ taskData.collectedFields.opScope || '-' }}</div>
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  taskData: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const statusTagType = (status) => {
  const map = {
    SUCCESS: 'success',
    FAILED: 'danger',
    RUNNING: 'primary',
    PENDING: 'info'
  }
  return map[status] || 'info'
}

const statusLabel = (status) => {
  const map = {
    PENDING: '排队中',
    RUNNING: '执行中',
    SUCCESS: '成功',
    FAILED: '失败'
  }
  return map[status] || status
}

const taskTypeTagType = (type) => {
  const map = {
    ENS: 'primary',
    AI: 'warning',
    OTHER: 'info'
  }
  return map[type] || 'info'
}

const taskTypeLabel = (type) => {
  const map = {
    ENS: 'ENS',
    AI: 'AI',
    OTHER: '其他'
  }
  return map[type] || type
}

const formatTime = (timestamp) => {
  if (!timestamp) return '-'
  const date = new Date(timestamp)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}
</script>

<style scoped>
.task-detail {
  max-height: 60vh;
  overflow-y: auto;
}

.collected-fields {
  margin-top: 20px;
}

.section-title {
  margin-bottom: 12px;
  color: #303133;
  font-size: 15px;
  font-weight: 600;
}

.error-text {
  color: #f56c6c;
}

.scope-text {
  white-space: pre-wrap;
  word-break: break-all;
  line-height: 1.6;
}
</style>
