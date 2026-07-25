<template>
  <el-card class="task-create-card" shadow="never">
    <template #header>
      <div class="card-header">
        <span class="card-title">创建 V3 任务</span>
      </div>
    </template>

    <!-- Client ID 和连接状态 -->
    <div class="connection-info">
      <div class="info-row">
        <span class="info-label">Client ID：</span>
        <el-tag type="info" size="small" class="client-id-tag">{{ v3Store.clientId || '未生成' }}</el-tag>
      </div>
      <div class="info-row" v-if="v3Store.hasActiveTasks">
        <span class="info-label">SSE 连接：</span>
        <el-tag :type="v3Store.isConnected ? 'success' : 'warning'" size="small">
          {{ v3Store.isConnected ? '已连接' : '等待连接' }}
        </el-tag>
        <el-button
          v-if="!v3Store.isConnected"
          type="primary"
          size="small"
          link
          :loading="v3Store.isConnecting"
          @click="handleConnect"
        >
          手动连接
        </el-button>
      </div>
    </div>

    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="100px"
      label-position="right"
      size="default"
    >
      <el-form-item label="工作流" prop="promptId">
        <!-- 未选择工作流时显示选择按钮 -->
        <el-button
          v-if="!selectedWorkflow"
          type="primary"
          :disabled="disabled"
          @click="importDialogVisible = true"
        >
          <el-icon><FolderOpened /></el-icon>
          选择工作流
        </el-button>

        <!-- 已选择工作流时显示卡片 -->
        <div v-else class="workflow-card">
          <div class="workflow-info">
            <div class="workflow-title">{{ selectedWorkflow.promptTitle }}</div>
            <div class="workflow-meta">
              <el-tag type="primary" size="small">
                {{ selectedWorkflow.miniPrompts?.length || 0 }} 步
              </el-tag>
              <el-tag :type="selectedWorkflow.status === 1 ? 'success' : 'info'" size="small">
                {{ selectedWorkflow.status === 1 ? '已保存' : '草稿' }}
              </el-tag>
            </div>
          </div>
          <el-button
            type="warning"
            size="small"
            :disabled="disabled"
            @click="importDialogVisible = true"
          >
            更换工作流
          </el-button>
        </div>
      </el-form-item>

      <el-form-item label="配置名称" prop="configName">
        <el-select
          v-model="formData.configName"
          placeholder="请选择配置名称"
          style="width: 100%"
          :disabled="disabled"
          filterable
          :loading="profilesLoading"
        >
          <el-option
            v-for="item in profilesList"
            :key="item.profileName"
            :label="item.profileName"
            :value="item.profileName"
          >
            <span>{{ item.profileName }}</span>
            <span v-if="item.summary" style="float: right; color: #8492a6; font-size: 12px">{{ item.summary }}</span>
          </el-option>
        </el-select>
      </el-form-item>

      <el-form-item label="数量" prop="count">
        <el-input-number
          v-model="formData.count"
          :min="1"
          :max="10"
          placeholder="批量数量，留空为单个创建"
          style="width: 100%"
          :disabled="disabled"
        />
        <div class="form-tip">填写数量将批量创建任务，不填则创建单个任务</div>
      </el-form-item>

      <el-form-item>
        <el-button
          type="primary"
          :loading="v3Store.isCreating"
          :disabled="disabled || v3Store.isCreating"
          @click="handleSubmit"
        >
          创建任务
        </el-button>
        <el-button @click="handleReset" :disabled="disabled">
          重置
        </el-button>
      </el-form-item>
    </el-form>

    <!-- 导入工作流弹窗 -->
    <ImportPromptFlowDialog
      v-model="importDialogVisible"
      @import="handleSelectWorkflow"
    />
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { FolderOpened } from '@element-plus/icons-vue'
import { useV3TaskStore } from '@/store/v3TaskStore'
import { getFarmProfiles } from '@/api/guiConfig'
import ImportPromptFlowDialog from '@/views/gui-manage/components/ImportPromptFlowDialog.vue'

const props = defineProps({
  disabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['create', 'connect'])

const v3Store = useV3TaskStore()
const formRef = ref(null)

// 配置列表数据
const profilesList = ref([])
const profilesLoading = ref(false)

// 工作流选择状态
const selectedWorkflow = ref(null)
const importDialogVisible = ref(false)

const formData = reactive({
  promptId: null,
  configName: '',
  count: undefined
})

const formRules = {
  promptId: [
    { required: true, message: '请选择工作流', trigger: ['change', 'blur'] }
  ],
  configName: [
    { required: true, message: '请选择配置名称', trigger: 'change' }
  ]
}

/**
 * 获取配置列表
 */
const fetchProfiles = async () => {
  profilesLoading.value = true
  try {
    const data = await getFarmProfiles()
    profilesList.value = Array.isArray(data) ? data : []
  } catch (error) {
    console.error('获取配置列表失败:', error)
    ElMessage.error('获取配置列表失败')
    profilesList.value = []
  } finally {
    profilesLoading.value = false
  }
}

/**
 * 选择工作流
 */
const handleSelectWorkflow = (row) => {
  selectedWorkflow.value = row
  // 兼容 id 或 promptId 字段
  formData.promptId = row.id || row.promptId
  console.log('选择工作流:', row, 'promptId:', formData.promptId)
  importDialogVisible.value = false
  // 手动清除该字段的验证状态
  formRef.value?.clearValidate('promptId')
}

const handleSubmit = async () => {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
    emit('create', { ...formData, userId: '1' })
  } catch (error) {
    // 表单验证失败
  }
}

const handleReset = () => {
  if (formRef.value) {
    formRef.value.resetFields()
  }
  selectedWorkflow.value = null
}

const handleConnect = () => {
  emit('connect')
}

/**
 * 组件挂载时获取配置列表
 */
onMounted(() => {
  fetchProfiles()
})
</script>

<style scoped>
.task-create-card {
  background-color: #fff;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-size: 16px;
  font-weight: bold;
  color: #303133;
}

.workflow-card {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background-color: #f5f7fa;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
}

.workflow-info {
  flex: 1;
  min-width: 0;
}

.workflow-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workflow-meta {
  display: flex;
  gap: 8px;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.4;
}

.connection-info {
  padding: 12px 16px;
  background-color: #f5f7fa;
  border-radius: 8px;
  margin-bottom: 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.info-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.info-label {
  font-size: 13px;
  color: #606266;
  white-space: nowrap;
}

.client-id-tag {
  word-break: break-all;
  max-height: 60px;
  overflow-y: auto;
}
</style>
