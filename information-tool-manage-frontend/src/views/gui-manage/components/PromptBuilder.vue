<template>
  <el-card class="builder-card" shadow="never">
    <template #header>
      <div class="card-header">
        <span>提示词构建区</span>
        <div class="header-actions">
          <el-button type="warning" size="small" plain @click="emit('switch-to-system')">
            <el-icon><Switch /></el-icon>
            切换至系统提示词
          </el-button>
          <el-button type="success" size="small" plain @click="importDialogVisible = true">
            <el-icon><Download /></el-icon>
            导入提示词
          </el-button>
          <el-button type="primary" size="small" @click="handleAdd">
            <el-icon><Plus /></el-icon>
            新增提示词
          </el-button>
        </div>
      </div>
    </template>

    <VueDraggable
      v-model="localList"
      :group="{ name: 'prompts', pull: 'clone', put: false }"
      :animation="200"
      item-key="id"
      class="builder-list"
      :sort="false"
    >
      <template #item="{ element }">
        <el-card class="prompt-card" shadow="never" :body-style="{ padding: '12px 16px' }">
          <div class="prompt-card-content">
            <el-icon class="drag-handle"><Rank /></el-icon>
            <span class="prompt-title">{{ element.title }}</span>
            <el-tag size="small" type="info" class="type-tag" v-if="element.type">
              {{ getMiniPromptTypeLabel(element.type) }}
            </el-tag>
            <div class="prompt-actions">
              <el-button
                type="success"
                link
                size="small"
                :disabled="guiStore.isTaskRunning || !guiStore.taskId"
                @click.stop="handleRunSingle(element)"
              >
                <el-icon><VideoPlay /></el-icon>
                运行
              </el-button>
              <el-button type="primary" link size="small" @click.stop="handleEdit(element)">
                <el-icon><Edit /></el-icon>
                编辑
              </el-button>
              <el-button type="danger" link size="small" @click.stop="handleDelete(element)">
                <el-icon><Delete /></el-icon>
                删除
              </el-button>
            </div>
          </div>
        </el-card>
      </template>
    </VueDraggable>

    <el-empty v-if="localList.length === 0" description="暂无提示词，点击右上角新增或导入" :image-size="80" />

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑提示词' : '新增提示词'"
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

    <!-- 导入小提示词弹窗 -->
    <ImportMiniPromptDialog
      v-model="importDialogVisible"
      :allowed-types="[3, 5]"
      @import="handleImport"
    />
  </el-card>
</template>

<script setup>
import { ref, computed } from 'vue'
import { Plus, Edit, Delete, Rank, VideoPlay, Download, Switch } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMiniPromptTypeLabel, MINI_PROMPT_TYPE_OPTIONS } from '@/constants/prompt'
import VueDraggable from 'vuedraggable'
import { useGuiStore } from '@/store/guiStore'
import { addMiniPrompt } from '@/api/prompts'
import ImportMiniPromptDialog from './ImportMiniPromptDialog.vue'

const guiStore = useGuiStore()

const props = defineProps({
  list: {
    type: Array,
    required: true
  }
})

const emit = defineEmits(['update:list', 'run-single', 'switch-to-system'])

// 本地列表副本，用于拖拽克隆
const localList = computed({
  get: () => props.list,
  set: (val) => emit('update:list', val)
})

// ========== 新增/编辑弹窗相关 ==========
const dialogVisible = ref(false)
const isEdit = ref(false)
const editingId = ref(null)
const formRef = ref(null)
const submitLoading = ref(false)

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

// ========== 导入弹窗相关 ==========
const importDialogVisible = ref(false)

// 生成唯一 ID
let idCounter = 100
const generateId = () => `builder_${Date.now()}_${idCounter++}`

const handleAdd = () => {
  isEdit.value = false
  editingId.value = null
  formData.value = { title: '', content: '', type: undefined, isPublic: 0 }
  dialogVisible.value = true
}

const handleEdit = (item) => {
  isEdit.value = true
  editingId.value = item.id
  formData.value = {
    title: item.title,
    content: item.content,
    type: item.type,
    isPublic: item.isPublic ?? 0
  }
  dialogVisible.value = true
}

const handleDelete = async (item) => {
  try {
    await ElMessageBox.confirm(`确定删除提示词「${item.title}」吗？`, '确认删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const newList = localList.value.filter((i) => i.id !== item.id)
    emit('update:list', newList)
    ElMessage.success('删除成功')
  } catch {
    // 用户取消
  }
}

// ========== 单步运行 ==========
const handleRunSingle = (element) => {
  emit('run-single', element)
}

// ========== 导入逻辑 ==========
const handleImport = (selectedRows) => {
  const existingIds = new Set(localList.value.map((item) => item.id))
  const newItems = selectedRows.filter((row) => !existingIds.has(row.id))
  const duplicateCount = selectedRows.length - newItems.length

  if (newItems.length > 0) {
    emit('update:list', [...localList.value, ...newItems])
    if (duplicateCount > 0) {
      ElMessage.success(`成功导入 ${newItems.length} 条提示词，${duplicateCount} 条重复已跳过`)
    } else {
      ElMessage.success(`成功导入 ${newItems.length} 条提示词`)
    }
  } else if (duplicateCount > 0) {
    ElMessage.warning(`所选 ${duplicateCount} 条提示词已存在，无需重复导入`)
  }
}

// ========== 新增提示词（对接后端接口） ==========
const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return

    if (isEdit.value) {
      // 编辑模式：仅更新本地列表（构建区为临时组装，编辑不影响后端）
      const newList = localList.value.map((item) => {
        if (item.id === editingId.value) {
          return {
            ...item,
            title: formData.value.title,
            content: formData.value.content,
            type: formData.value.type,
            isPublic: formData.value.isPublic
          }
        }
        return item
      })
      emit('update:list', newList)
      ElMessage.success('编辑成功')
      dialogVisible.value = false
    } else {
      // 新增模式：调用 addMiniPrompt 接口创建小提示词
      submitLoading.value = true
      try {
        console.log('【PromptBuilder】正在新增小提示词，提交数据:', JSON.parse(JSON.stringify(formData.value)))
        const res = await addMiniPrompt({
          title: formData.value.title,
          content: formData.value.content,
          type: formData.value.type,
          isPublic: formData.value.isPublic
        })
        // 接口返回后，将新创建的提示词对象 push 到构建区列表
        const newItem = res && res.id ? res : {
          id: generateId(),
          title: formData.value.title,
          content: formData.value.content,
          type: formData.value.type,
          isPublic: formData.value.isPublic
        }
        emit('update:list', [...localList.value, newItem])
        ElMessage.success('新增成功')
        dialogVisible.value = false
      } catch (error) {
        console.error('新增小提示词失败:', error)
      } finally {
        submitLoading.value = false
      }
    }
  })
}
</script>

<style scoped>
.builder-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.builder-card :deep(.el-card__body) {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 16px;
  font-weight: bold;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.builder-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 100px;
}

.prompt-card {
  cursor: grab;
  border: 1px solid var(--el-border-color-lighter);
}

.prompt-card:active {
  cursor: grabbing;
}

.prompt-card-content {
  display: flex;
  align-items: center;
  gap: 10px;
}

.drag-handle {
  color: #c0c4cc;
  cursor: grab;
  flex-shrink: 0;
}

.drag-handle:active {
  cursor: grabbing;
}

.prompt-title {
  flex: 1;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.prompt-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.type-tag {
  flex-shrink: 0;
}

/* 拖拽时的幽灵样式 */
.builder-list :deep(.sortable-ghost) {
  opacity: 0.4;
}

.builder-list :deep(.sortable-drag) {
  opacity: 0.9;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}
</style>
