<template>
  <el-card class="table-card">
    <template #header>
      <div class="card-header">
        <span>任务执行监控</span>
        <div>
          <el-button type="warning" @click="handleExport" :disabled="tableData.length === 0">
            <el-icon><Download /></el-icon>导出当前页
          </el-button>
          <el-button type="primary" @click="handleRefresh">
            <el-icon><Refresh /></el-icon>刷新
          </el-button>
        </div>
      </div>
    </template>

    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="id" label="任务ID" min-width="150" show-overflow-tooltip />
      <el-table-column prop="companyName" label="公司名称" min-width="150" show-overflow-tooltip />
      <el-table-column prop="taskType" label="任务类型" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="taskTypeTagType(row.taskType)">
            {{ taskTypeLabel(row.taskType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="任务状态" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="retryCount" label="重试次数" width="100" align="center" />
      <el-table-column prop="failureReason" label="失败原因" min-width="200">
        <template #default="{ row }">
          <el-tooltip
            v-if="row.failureReason"
            :content="row.failureReason"
            placement="top"
            :show-after="500"
          >
            <div class="failure-cell">{{ row.failureReason }}</div>
          </el-tooltip>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="180" align="center">
        <template #default="{ row }">
          {{ formatTime(row.createTimeMs) }}
        </template>
      </el-table-column>
      <el-table-column prop="startTime" label="开始时间" width="180" align="center">
        <template #default="{ row }">
          {{ row.startTime || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" align="center" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="handleViewDetail(row)">
            <el-icon><View /></el-icon>详情
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="currentSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>
  </el-card>
</template>

<script setup>
import { computed } from 'vue'
import { Refresh, View, Download } from '@element-plus/icons-vue'

const props = defineProps({
  tableData: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  },
  pagination: {
    type: Object,
    default: () => ({ page: 1, size: 10, total: 0 })
  }
})

const emit = defineEmits(['refresh', 'page-change', 'size-change', 'view-detail', 'export'])

const currentPage = computed({
  get: () => props.pagination.page,
  set: () => {}
})

const currentSize = computed({
  get: () => props.pagination.size,
  set: () => {}
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

const handleRefresh = () => emit('refresh')
const handleSizeChange = (val) => emit('size-change', val)
const handleCurrentChange = (val) => emit('page-change', val)
const handleViewDetail = (row) => emit('view-detail', row)
const handleExport = () => emit('export')
</script>

<style scoped>
.table-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.failure-cell {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 180px;
}

.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
