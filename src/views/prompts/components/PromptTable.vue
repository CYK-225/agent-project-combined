<template>
  <el-card class="table-card">
    <template #header>
      <div class="card-header">
        <span>大提示词列表</span>
      </div>
    </template>

    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="promptTitle" label="提示词标题" min-width="150" show-overflow-tooltip />
      <el-table-column label="包含步骤" min-width="120" align="center">
        <template #default="{ row }">
          <el-tag type="primary" size="small">{{ row.miniPrompts?.length || 0 }} 个步骤</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">
            {{ row.status === 1 ? '已保存' : '草稿' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="isPublic" label="可见性" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.isPublic === 1 ? 'primary' : 'warning'">
            {{ row.isPublic === 1 ? '公开' : '私有' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="useCount" label="使用次数" width="100" align="center" />
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="150" align="center" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="handleEdit(row)">
            <el-icon><Edit /></el-icon>编辑
          </el-button>
          <el-button type="danger" link @click="handleDelete(row)">
            <el-icon><Delete /></el-icon>删除
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
import { Edit, Delete } from '@element-plus/icons-vue'

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

const emit = defineEmits(['edit', 'delete', 'page-change', 'size-change'])

const handleEdit = (row) => emit('edit', row)
const handleDelete = (row) => emit('delete', row)
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

.content-cell {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 280px;
}

.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
