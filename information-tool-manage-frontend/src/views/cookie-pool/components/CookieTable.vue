<template>
  <el-card class="table-card">
    <template #header>
      <div class="card-header">
        <span>Cookie 池列表</span>
        <div>
          <el-button type="success" @click="handleRefresh" :loading="refreshLoading">
            <el-icon><Refresh /></el-icon>刷新列表
          </el-button>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>新增 Cookie
          </el-button>
        </div>
      </div>
    </template>

    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="site" label="站点标识" min-width="120" />
      <el-table-column prop="account" label="登录账号/手机号" min-width="150" />
      <el-table-column prop="status" label="状态" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">
            {{ row.status === 1 ? '有效可用' : '失效或获取失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="failReason" label="失败原因" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.failReason">{{ row.failReason }}</span>
          <el-tag v-else type="info" size="small">-</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastVerifyTime" label="最后验证时间" width="180" />
      <el-table-column label="操作" width="200" align="center" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="handleEdit(row)">
            <el-icon><Edit /></el-icon>编辑
          </el-button>
          <el-button type="danger" link @click="handleDelete(row)">
            <el-icon><Delete /></el-icon>删除
          </el-button>
          <el-button type="info" link @click="handleVerify(row)" :loading="row.verifying">
            <el-icon><Check /></el-icon>验证
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
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
import { Plus, Edit, Delete, Refresh, Check } from '@element-plus/icons-vue'

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
  },
  refreshLoading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['add', 'edit', 'delete', 'verify', 'refresh', 'page-change', 'size-change'])

const handleAdd = () => emit('add')
const handleEdit = (row) => emit('edit', row)
const handleDelete = (row) => emit('delete', row)
const handleVerify = (row) => emit('verify', row)
const handleRefresh = () => emit('refresh')
const handleSizeChange = (val) => emit('size-change', val)
const handleCurrentChange = (val) => emit('page-change', val)
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

.card-header > div {
  display: flex;
  gap: 10px;
}

.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
