<template>
  <div class="prompts-container">
    <el-tabs v-model="activeTab" type="border-card">
      <!-- Tab 1: 提示词管理 -->
      <el-tab-pane label="提示词管理" name="mini">
        <MiniPromptManage />
      </el-tab-pane>

      <!-- Tab 2: 工作流管理 -->
      <el-tab-pane label="工作流管理" name="prompts">
        <PromptSearch @search="handleSearch" @reset="handleReset" />

        <PromptTable
          :table-data="tableData"
          :loading="loading"
          :pagination="pagination"
          @edit="handleEdit"
          @delete="handleDelete"
          @page-change="handlePageChange"
          @size-change="handleSizeChange"
        />

        <PromptDialog
          v-model="dialogVisible"
          :is-edit="isEdit"
          :form-data="formData"
          :submit-loading="submitLoading"
          @submit="handleSubmit"
        />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPrompts, updatePrompt, deletePrompt } from '@/api/prompts'
import PromptSearch from './components/PromptSearch.vue'
import PromptTable from './components/PromptTable.vue'
import PromptDialog from './components/PromptDialog.vue'
import MiniPromptManage from './components/MiniPromptManage.vue'

// ========== Tab 切换 ==========
const activeTab = ref('mini')

// ========== 大提示词逻辑（保持原有逻辑不变） ==========
const loading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const currentId = ref(null)

const searchForm = reactive({
  title: ''
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const tableData = ref([])

const formData = ref({
  promptTitle: '',
  status: 0,
  isPublic: 0
})

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getPrompts({
      page: pagination.page,
      size: pagination.size,
      ...searchForm
    })
    const data = res.data || res
    tableData.value = data.records || []
    pagination.total = data.totalRow || 0
  } catch (error) {
    console.error('获取数据失败:', error)
  } finally {
    loading.value = false
  }
}

const handleSearch = (form) => {
  searchForm.title = form.title || ''
  pagination.page = 1
  fetchData()
}

const handleReset = () => {
  searchForm.title = ''
  pagination.page = 1
  fetchData()
}

const handleEdit = (row) => {
  isEdit.value = true
  currentId.value = row.promptId
  formData.value = {
    promptTitle: row.promptTitle,
    status: row.status,
    isPublic: row.isPublic
  }
  dialogVisible.value = true
}

const handleDelete = (row) => {
  ElMessageBox.confirm(
    `确定要删除提示词 "${row.promptTitle}" 吗？`,
    '确认删除',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    try {
      await deletePrompt(row.promptId)
      ElMessage.success('删除成功')
      fetchData()
    } catch (error) {
      console.error('删除失败:', error)
    }
  }).catch(() => {})
}

const handleSubmit = async (form) => {
  submitLoading.value = true
  try {
    await updatePrompt({ ...form, id: currentId.value })
    ElMessage.success('更新成功')
    dialogVisible.value = false
    fetchData()
  } catch (error) {
    console.error('提交失败:', error)
  } finally {
    submitLoading.value = false
  }
}

const handleSizeChange = (val) => {
  pagination.size = val
  fetchData()
}

const handlePageChange = (val) => {
  pagination.page = val
  fetchData()
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.prompts-container {
  padding: 20px;
}
</style>
