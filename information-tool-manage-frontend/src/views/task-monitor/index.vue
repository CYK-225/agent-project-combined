<template>
  <div class="task-monitor-container">
    <TaskSearch @search="handleSearch" @reset="handleReset" />

    <TaskTable
      :table-data="tableData"
      :loading="loading"
      :pagination="pagination"
      @refresh="handleRefresh"
      @page-change="handlePageChange"
      @size-change="handleSizeChange"
      @view-detail="handleViewDetail"
      @export="handleExportData"
    />

    <TaskDetail
      v-model="detailVisible"
      :task-data="currentTask"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { getTasksPage, getTasksByCompanyName, getTasksByDateRange } from '@/api/taskMonitor'
import { exportToCsv } from '@/utils/export'
import { ElMessage } from 'element-plus'
import TaskSearch from './components/TaskSearch.vue'
import TaskTable from './components/TaskTable.vue'
import TaskDetail from './components/TaskDetail.vue'

const loading = ref(false)
const tableData = ref([])
const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const detailVisible = ref(false)
const currentTask = ref(null)
const searchKeyword = ref('')
const searchDateRange = ref([])

let refreshTimer = null

// 获取分页数据（无搜索关键词时）
const fetchPageData = async (silent = false) => {
  if (!silent) {
    loading.value = true
  }
  try {
    const res = await getTasksPage({
      page: pagination.page,
      size: pagination.size
    })
    tableData.value = res.records || []
    pagination.total = res.totalRow || 0
  } catch (error) {
    console.error('获取任务数据失败:', error)
  } finally {
    if (!silent) {
      loading.value = false
    }
  }
}

// 按公司名称搜索（模糊搜索，返回全量列表）
const fetchSearchData = async (silent = false) => {
  if (!silent) {
    loading.value = true
  }
  try {
    const res = await getTasksByCompanyName(searchKeyword.value)
    // 接口返回的是数组（List<TaskInfoEntity>）
    const list = Array.isArray(res) ? res : (res.records || [])
    tableData.value = list
    pagination.total = list.length
  } catch (error) {
    console.error('搜索任务数据失败:', error)
  } finally {
    if (!silent) {
      loading.value = false
    }
  }
}

// 按日期范围分页查询
const fetchDateRangeData = async (silent = false) => {
  if (!silent) loading.value = true
  try {
    const res = await getTasksByDateRange({
      page: pagination.page,
      size: pagination.size,
      startDate: searchDateRange.value[0],
      endDate: searchDateRange.value[1]
    })
    tableData.value = res.records || []
    pagination.total = res.totalRow || res.total || 0
  } catch (error) {
    console.error('按日期查询任务数据失败:', error)
  } finally {
    if (!silent) loading.value = false
  }
}

// 统一数据获取入口
const fetchData = (silent = false) => {
  if (searchKeyword.value) {
    // 优先级1：如果输入了公司名称，按公司名称搜索（全量）
    fetchSearchData(silent)
  } else if (searchDateRange.value && searchDateRange.value.length === 2) {
    // 优先级2：如果选择了日期范围，按日期范围分页查询
    fetchDateRangeData(silent)
  } else {
    // 优先级3：默认的全局分页查询
    fetchPageData(silent)
  }
}

const handleSearch = (form) => {
  searchKeyword.value = form.keyword || ''
  searchDateRange.value = form.dateRange || []
  pagination.page = 1
  fetchData()
}

const handleReset = () => {
  searchKeyword.value = ''
  searchDateRange.value = []
  pagination.page = 1
  fetchData()
}

const handleRefresh = () => {
  // 手动刷新时清除搜索状态，保留当前页码，重新执行分页查询
  searchKeyword.value = ''
  searchDateRange.value = []
  fetchPageData()
}

const handlePageChange = (val) => {
  pagination.page = val
  fetchData()
}

const handleSizeChange = (val) => {
  pagination.size = val
  pagination.page = 1
  fetchData()
}

const handleViewDetail = (row) => {
  currentTask.value = row
  detailVisible.value = true
}

// 处理数据导出
const handleExportData = () => {
  if (tableData.value.length === 0) {
    ElMessage.warning('当前没有可导出的数据')
    return
  }

  // 定义导出列的映射关系
  const columns = [
    { title: '任务ID', key: 'id' },
    { title: '公司名称', key: 'companyName' },
    { title: '任务类型', key: 'taskType' },
    { title: '状态', key: 'status', formatter: (val) => {
        const map = { PENDING: '排队中', RUNNING: '执行中', SUCCESS: '成功', FAILED: '失败' }
        return map[val] || val
    }},
    { title: '开始时间', key: 'startTime' },
    { title: '失败原因', key: 'failureReason' },
    { title: '网址', key: 'webAddress' },
    // 提取采集字段 (collectedFields)
    { title: '采集-企业名称', key: 'entName', formatter: (val, row) => row.collectedFields?.entName || '' },
    { title: '采集-法定代表人', key: 'legalPerson', formatter: (val, row) => row.collectedFields?.legalPerson || '' },
    { title: '采集-注册资本', key: 'recConcat', formatter: (val, row) => row.collectedFields?.recConcat || '' },
    { title: '采集-联系电话', key: 'telList', formatter: (val, row) => row.collectedFields?.telList || '' },
    { title: '采集-经营范围', key: 'opScope', formatter: (val, row) => row.collectedFields?.opScope || '' }
  ]

  const fileName = `任务监控导出_${new Date().getTime()}.csv`
  const success = exportToCsv(tableData.value, columns, fileName)
  
  if (success) {
    ElMessage.success('数据导出成功')
  }
}

onMounted(() => {
  fetchData()
  // 每15秒静默刷新一次数据，实现实时监控效果
  refreshTimer = setInterval(() => {
    fetchData(true)
  }, 15000)
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
})
</script>

<style scoped>
.task-monitor-container {
  padding: 20px;
}
</style>
