<template>
  <div class="mini-prompt-manage">
    <!-- 搜索区 -->
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="提示词类型">
          <el-select v-model="searchForm.type" placeholder="请选择类型" clearable style="width: 200px">
            <el-option
              v-for="item in MINI_PROMPT_TYPE_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="searchForm.keyword" placeholder="搜索标题或内容" clearable style="width: 240px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>查询
          </el-button>
          <el-button @click="handleReset">
            <el-icon><RefreshRight /></el-icon>重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格区 -->
    <el-card class="table-card">
      <template #header>
        <div class="card-header">
          <span>小提示词列表</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>新增小提示词
          </el-button>
        </div>
      </template>

      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="title" label="标题" min-width="150" show-overflow-tooltip />
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
        <el-table-column prop="createTime" label="创建时间" width="300" />
        <el-table-column label="操作" width="300" align="center" fixed="right">
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
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑小提示词' : '新增小提示词'"
      width="600px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="formData.title" placeholder="请输入提示词标题" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input
            v-model="formData.content"
            type="textarea"
            placeholder="请输入具体的提示词内容"
            :rows="6"
            maxlength="2000"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="formData.type" placeholder="请选择提示词类型" style="width: 100%">
            <el-option
              v-for="item in MINI_PROMPT_TYPE_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="可见性" prop="isPublic">
          <el-radio-group v-model="formData.isPublic">
            <el-radio :label="0">私有</el-radio>
            <el-radio :label="1">公开</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitLoading">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, RefreshRight, Plus, Edit, Delete } from '@element-plus/icons-vue'
import { getMiniPromptsList, getMiniPromptsByType, addMiniPrompt, updateMiniPrompt, deleteMiniPrompt } from '@/api/prompts'
import { MINI_PROMPT_TYPE_OPTIONS, getMiniPromptTypeLabel } from '@/constants/prompt'

const loading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const currentId = ref(null)
const formRef = ref(null)

const searchForm = reactive({
  type: undefined,
  keyword: ''
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const tableData = ref([])

const formData = ref({
  title: '',
  content: '',
  type: undefined,
  isPublic: 0
})

const formRules = {
  title: [
    { required: true, message: '请输入提示词标题', trigger: 'blur' },
    { min: 1, max: 100, message: '标题长度在 1 到 100 个字符', trigger: 'blur' }
  ],
  content: [
    { required: true, message: '请输入提示词内容', trigger: 'blur' }
  ],
  type: [
    { required: true, message: '请选择提示词类型', trigger: 'change' }
  ],
  isPublic: [
    { required: true, message: '请选择可见性', trigger: 'change' }
  ]
}

/**
 * 根据类型值返回对应的 Tag 样式
 */
const getTypeTagType = (type) => {
  const map = { 1: 'danger', 2: 'warning', 3: 'success', 4: 'info' }
  return map[type] || 'info'
}

const fetchData = async () => {
  loading.value = true
  try {
    const keyword = searchForm.keyword?.trim() || undefined
    if (searchForm.type !== undefined && searchForm.type !== null && searchForm.type !== '') {
      // 按类型查询：使用专用接口
      const res = await getMiniPromptsByType(searchForm.type, {
        pageNumber: pagination.page,
        pageSize: pagination.size,
        keyword
      })
      const data = res && (res.code === 200 || res.code === 0) && res.data ? res.data : res
      if (Array.isArray(data)) {
        tableData.value = data
        pagination.total = data.length
      } else {
        tableData.value = data.records || []
        pagination.total = data.totalRow || data.total || 0
      }
    } else {
      // 无类型筛选：使用分页接口
      const res = await getMiniPromptsList({
        page: pagination.page,
        size: pagination.size,
        type: searchForm.type,
        keyword
      })
      // 兼容统一响应结构，提取嵌套在 data 中的 records 和 totalRow
      if (res && (res.code === 200 || res.code === 0) && res.data) {
        tableData.value = res.data.records || []
        pagination.total = res.data.totalRow || 0
      } else {
        // 容错处理：如果直接返回的是分页对象本身
        tableData.value = res.records || []
        pagination.total = res.totalRow || res.total || 0
      }
    }
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

const handleReset = () => {
  searchForm.type = undefined
  searchForm.keyword = ''
  pagination.page = 1
  fetchData()
}

const handleAdd = () => {
  isEdit.value = false
  currentId.value = null
  formData.value = {
    title: '',
    content: '',
    type: undefined,
    isPublic: 0
  }
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  currentId.value = row.id
  formData.value = {
    title: row.title,
    content: row.content,
    type: row.type,
    isPublic: row.isPublic
  }
  dialogVisible.value = true
}

const handleDelete = (row) => {
  ElMessageBox.confirm(
    `确定要删除小提示词「${row.title}」吗？`,
    '确认删除',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    try {
      await deleteMiniPrompt(row.id)
      ElMessage.success('删除成功')
      fetchData()
    } catch (error) {
      console.error('删除失败:', error)
    }
  }).catch(() => {})
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (isEdit.value) {
        await updateMiniPrompt({ id: currentId.value, ...formData.value })
        ElMessage.success('更新成功')
      } else {
        await addMiniPrompt(formData.value)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      fetchData()
    } catch (error) {
      console.error('提交失败:', error)
    } finally {
      submitLoading.value = false
    }
  })
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
.mini-prompt-manage {
  /* 继承父容器样式 */
}

.search-card {
  margin-bottom: 20px;
}

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
