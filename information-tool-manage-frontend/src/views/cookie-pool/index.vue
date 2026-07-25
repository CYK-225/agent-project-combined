<template>
  <div class="cookie-pool-container">
    <CookieSearch @search="handleSearch" @reset="handleReset" />

    <CookieTable
      :table-data="tableData"
      :loading="loading"
      :pagination="pagination"
      :refresh-loading="refreshLoading"
      @add="handleAdd"
      @edit="handleEdit"
      @delete="handleDelete"
      @verify="handleVerify"
      @refresh="handleRefresh"
      @page-change="handlePageChange"
      @size-change="handleSizeChange"
    />

    <CookieDialog
      v-model="dialogVisible"
      :is-edit="isEdit"
      :form-data="formData"
      :submit-loading="submitLoading"
      @submit="handleSubmit"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCookies, createCookie, updateCookie, deleteCookie, verifyCookie } from '@/api/cookiePool'
import CookieSearch from './components/CookieSearch.vue'
import CookieTable from './components/CookieTable.vue'
import CookieDialog from './components/CookieDialog.vue'

const loading = ref(false)
const submitLoading = ref(false)
const refreshLoading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const currentId = ref(null)

const searchForm = reactive({
  site: '',
  status: null
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const tableData = ref([])

const formData = ref({
  site: '',
  account: '',
  cookieValue: '',
  status: 1,
  failReason: ''
})

const fetchData = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      size: pagination.size
    }
    if (searchForm.site) params.site = searchForm.site
    if (searchForm.status !== null) params.status = searchForm.status

    const res = await getCookies(params)
    tableData.value = (res.records || []).map(item => ({ ...item, verifying: false }))
    pagination.total = res.total || 0
  } catch (error) {
    console.error('获取数据失败:', error)
  } finally {
    loading.value = false
  }
}

const handleSearch = (form) => {
  searchForm.site = form.site || ''
  searchForm.status = form.status !== undefined ? form.status : null
  pagination.page = 1
  fetchData()
}

const handleReset = () => {
  searchForm.site = ''
  searchForm.status = null
  pagination.page = 1
  fetchData()
}

const handleRefresh = async () => {
  refreshLoading.value = true
  await fetchData()
  refreshLoading.value = false
  ElMessage.success('刷新成功')
}

const handleAdd = () => {
  isEdit.value = false
  currentId.value = null
  formData.value = {
    site: '',
    account: '',
    cookieValue: '',
    status: 1,
    failReason: ''
  }
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  currentId.value = row.id
  formData.value = {
    site: row.site,
    account: row.account,
    cookieValue: row.cookieValue,
    status: row.status,
    failReason: row.failReason || ''
  }
  dialogVisible.value = true
}

const handleDelete = (row) => {
  ElMessageBox.confirm(
    `确定要删除站点 "${row.site}" 的 Cookie 吗？`,
    '确认删除',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    try {
      await deleteCookie(row.id)
      ElMessage.success('删除成功')
      fetchData()
    } catch (error) {
      console.error('删除失败:', error)
    }
  }).catch(() => {})
}

const handleVerify = async (row) => {
  row.verifying = true
  try {
    await verifyCookie(row.id)
    ElMessage.success('验证成功')
    fetchData()
  } catch (error) {
    console.error('验证失败:', error)
  } finally {
    row.verifying = false
  }
}

const handleSubmit = async (form) => {
  submitLoading.value = true
  try {
    if (isEdit.value) {
      await updateCookie(currentId.value, form)
      ElMessage.success('更新成功')
    } else {
      await createCookie(form)
      ElMessage.success('创建成功')
    }
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
.cookie-pool-container {
  padding: 20px;
}
</style>
