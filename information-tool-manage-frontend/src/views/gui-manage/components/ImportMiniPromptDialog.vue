<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="(val) => emit('update:modelValue', val)"
    title="导入小提示词"
    width="800px"
    destroy-on-close
  >
    <!-- 查询与过滤 -->
    <div class="import-search-bar">
      <el-select v-model="searchType" placeholder="按类型筛选" clearable style="width: 200px">
        <el-option
          v-for="item in filteredTypeOptions"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </el-select>
      <el-input
        v-model="searchKeyword"
        placeholder="搜索标题或内容"
        clearable
        style="width: 220px; margin-left: 10px"
        @keyup.enter="handleSearch"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button type="primary" @click="handleSearch" style="margin-left: 10px">
        <el-icon><Search /></el-icon>查询
      </el-button>
    </div>

    <!-- 表格展示 -->
    <el-table
      ref="tableRef"
      :data="tableData"
      v-loading="loading"
      border
      stripe
      @selection-change="handleSelectionChange"
      style="margin-top: 16px"
    >
      <el-table-column type="selection" width="50" align="center" />
      <el-table-column prop="title" label="标题" min-width="150" show-overflow-tooltip />
      <el-table-column prop="content" label="内容" min-width="250">
        <template #default="{ row }">
          <el-tooltip :content="row.content" placement="top" :show-after="500">
            <div class="content-cell">{{ row.content }}</div>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="type" label="类型" width="140" align="center">
        <template #default="{ row }">
          <el-tag :type="getTypeTagType(row.type)">
            {{ getMiniPromptTypeLabel(row.type) }}
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
    </el-table>

    <!-- 分页器 -->
    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :page-sizes="[10, 20, 50]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" @click="handleConfirmImport" :disabled="selectedRows.length === 0">
        确认导入 ({{ selectedRows.length }})
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch, computed } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getMiniPromptsList, getMiniPromptsByType } from '@/api/prompts'
import { MINI_PROMPT_TYPE_OPTIONS, getMiniPromptTypeLabel } from '@/constants/prompt'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  allowedTypes: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['update:modelValue', 'import'])

const loading = ref(false)
const tableRef = ref(null)
const searchType = ref(undefined)
const searchKeyword = ref('')

// 根据 allowedTypes 过滤类型选项
const filteredTypeOptions = computed(() => {
  if (!props.allowedTypes || props.allowedTypes.length === 0) {
    return MINI_PROMPT_TYPE_OPTIONS
  }
  return MINI_PROMPT_TYPE_OPTIONS.filter(item => props.allowedTypes.includes(item.value))
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const tableData = ref([])
const selectedRows = ref([])

/**
 * 根据类型值返回对应的 Tag 样式
 */
const getTypeTagType = (type) => {
  const map = { 1: 'danger', 2: 'warning', 3: 'success', 4: 'info', 5: '' }
  return map[type] || 'info'
}

/**
 * 从 API 响应中提取列表数据
 * 兼容多种后端返回格式
 */
const extractListFromResponse = (res) => {
  if (!res) return []
  
  // 情况1: { code: 200, data: [...] } - data 直接是数组
  if (res && (res.code === 200 || res.code === 0) && Array.isArray(res.data)) {
    return res.data
  }
  
  // 情况2: { code: 200, data: { records: [...] } } - data 是分页对象
  if (res && (res.code === 200 || res.code === 0) && res.data && res.data.records) {
    return res.data.records
  }
  
  // 情况3: { records: [...] } - 直接是分页对象
  if (res && res.records) {
    return res.records
  }
  
  // 情况4: [...] - 直接是数组
  if (Array.isArray(res)) {
    return res
  }
  
  return []
}

const extractTotalFromResponse = (res) => {
  if (!res) return 0
  
  // 情况1: { code: 200, data: [...] } - data 直接是数组
  if (res && (res.code === 200 || res.code === 0) && Array.isArray(res.data)) {
    return res.data.length
  }
  
  // 情况2: { code: 200, data: { totalRow: 46 } } - data 是分页对象
  if (res && (res.code === 200 || res.code === 0) && res.data) {
    return res.data.totalRow || res.data.total || 0
  }
  
  // 情况3: { totalRow: 46 } - 直接是分页对象
  if (res && (res.totalRow !== undefined || res.total !== undefined)) {
    return res.totalRow || res.total || 0
  }
  
  return 0
}

const fetchData = async () => {
  loading.value = true
  try {
    let list = []
    let total = 0
    const keyword = searchKeyword.value?.trim() || undefined

    // 情况1：用户选了具体类型 → 用 getMiniPromptsByType 后端分页
    if (searchType.value !== undefined && searchType.value !== null && searchType.value !== '') {
      const res = await getMiniPromptsByType(searchType.value, {
        pageNumber: pagination.page,
        pageSize: pagination.size,
        keyword
      })
      list = extractListFromResponse(res)
      total = extractTotalFromResponse(res)
    }
    // 情况2：有 allowedTypes 限制但用户未选类型 → 遍历每个类型查询后合并
    else if (props.allowedTypes && props.allowedTypes.length > 0) {
      const allResults = []
      for (const t of props.allowedTypes) {
        try {
          const res = await getMiniPromptsByType(t, { pageNumber: 1, pageSize: 9999, keyword })
          const items = extractListFromResponse(res)
          allResults.push(...items)
        } catch { /* 单类型查询失败忽略 */ }
      }
      list = allResults
      total = list.length
      // 前端分页切片
      const start = (pagination.page - 1) * pagination.size
      list = list.slice(start, start + pagination.size)
    }
    // 情况3：无 allowedTypes 且未选类型 → 用 getMiniPromptsList 后端分页
    else {
      const res = await getMiniPromptsList({
        page: pagination.page,
        size: pagination.size,
        keyword
      })
      list = extractListFromResponse(res)
      total = extractTotalFromResponse(res)
    }

    tableData.value = list
    pagination.total = total
  } catch (error) {
    console.error('获取小提示词列表失败:', error)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.page = 1
  fetchData()
}

const handleSelectionChange = (rows) => {
  selectedRows.value = rows
}

const handleSizeChange = (val) => {
  pagination.size = val
  fetchData()
}

const handlePageChange = (val) => {
  pagination.page = val
  fetchData()
}

const handleConfirmImport = () => {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请至少选择一条小提示词')
    return
  }
  emit('import', [...selectedRows.value])
  emit('update:modelValue', false)
  ElMessage.success(`成功导入 ${selectedRows.value.length} 条小提示词`)
}

// 弹窗打开时自动加载数据
watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      searchType.value = undefined
      searchKeyword.value = ''
      pagination.page = 1
      selectedRows.value = []
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

.content-cell {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 230px;
}

.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
