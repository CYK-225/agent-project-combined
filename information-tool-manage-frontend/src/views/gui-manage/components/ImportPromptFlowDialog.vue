<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="(val) => emit('update:modelValue', val)"
    title="导入流程大提示词"
    width="800px"
    destroy-on-close
  >
    <!-- 查询与过滤 -->
    <div class="import-search-bar">
      <el-input
        v-model="searchTitle"
        placeholder="按标题搜索"
        clearable
        style="width: 300px"
        @keyup.enter="handleSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-button type="primary" @click="handleSearch" style="margin-left: 10px">
        <el-icon><Search /></el-icon>查询
      </el-button>
    </div>

    <!-- 表格展示 -->
    <el-table
      :data="tableData"
      v-loading="loading"
      border
      stripe
      style="margin-top: 16px"
    >
      <el-table-column prop="promptTitle" label="流程标题" min-width="200" show-overflow-tooltip />
      <el-table-column label="步骤数" width="100" align="center">
        <template #default="{ row }">
          <el-tag type="primary" size="small">{{ row.miniPrompts?.length || 0 }} 步</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">
            {{ row.status === 1 ? '已保存' : '草稿' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" align="center" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="handleSelect(row)">
            选择
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页器 -->
    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :page-sizes="[10, 20, 50]"
        :total="pagination.totalRow"
        layout="total, sizes, prev, pager, next"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { getPrompts } from '@/api/prompts'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'import'])

const loading = ref(false)
const searchTitle = ref('')

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const tableData = ref([])

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getPrompts({
      page: pagination.page,
      size: pagination.size,
      title: searchTitle.value || undefined
    })
    // 兼容统一响应结构，提取嵌套在 data 中的 records 和 total
    if (res && (res.code === 200 || res.code === 0) && res.data) {
      tableData.value = res.data.records || []
      pagination.totalRow = res.data.totalRow || 0
    } else {
      tableData.value = res.records || []
      pagination.totalRow = res.totalRow || 0
    }
  } catch (error) {
    console.error('获取大提示词列表失败:', error)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.page = 1
  fetchData()
}

const handleSizeChange = (val) => {
  pagination.size = val
  fetchData()
}

const handlePageChange = (val) => {
  pagination.page = val
  fetchData()
}

const handleSelect = (row) => {
  emit('import', row)
  emit('update:modelValue', false)
}

// 弹窗打开时自动加载数据
watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      searchTitle.value = ''
      pagination.page = 1
      fetchData()
    }
  }
)
</script>

<style scoped>
.import-search-bar {
  display: flex;
  align-items: center;
}

.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
