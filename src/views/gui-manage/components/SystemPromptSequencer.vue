<template>
  <el-card class="system-sequencer-card" shadow="never">
    <template #header>
      <div class="card-header">
        <div class="header-left">
          <span>系统提示词编排</span>
          <el-tag size="small" type="warning">共 {{ systemList.length }} 条</el-tag>
        </div>
        <div class="header-right">
          <el-button type="primary" size="small" plain @click="emit('switch-to-step')">
            <el-icon><Switch /></el-icon>
            切换到步骤提示词
          </el-button>
        </div>
      </div>
    </template>

    <div class="system-sequencer-body">
      <VueDraggable
        v-model="systemList"
        :group="{ name: 'systemPrompts' }"
        :animation="200"
        item-key="id"
        class="system-list"
        ghost-class="sortable-ghost"
        drag-class="sortable-drag"
      >
        <template #item="{ element, index }">
          <div class="system-row" :data-id="element.id">
            <div class="step-cell">
              <span class="step-label">系统 {{ index + 1 }}</span>
            </div>

            <el-card class="system-card" shadow="never" :body-style="{ padding: '10px 16px' }">
              <div class="system-card-content">
                <el-icon class="drag-handle"><Rank /></el-icon>
                <span class="system-title">{{ element.title }}</span>
                <el-tag size="small" type="warning" class="type-tag">系统提示词</el-tag>
                <div class="system-actions">
                  <el-button type="primary" link size="small" @click="handleViewDetail(element)">详情</el-button>
                  <el-button type="danger" link size="small" @click="handleDelete(element)">删除</el-button>
                </div>
              </div>
            </el-card>
          </div>
        </template>

        <template #footer>
          <el-empty
            v-if="systemList.length === 0"
            description="从上方系统提示词构建区拖入卡片"
            :image-size="80"
          />
        </template>
      </VueDraggable>
    </div>

    <!-- 底部操作区 -->
    <div class="system-sequencer-footer">
      <el-button
        :disabled="systemList.length === 0"
        @click="handleClear"
      >
        <el-icon><Delete /></el-icon>
        清空系统提示词
      </el-button>
    </div>

    <!-- 详情查看弹窗 -->
    <el-dialog v-model="detailVisible" title="系统提示词详情" width="500px">
      <div class="detail-content">
        <h4>{{ detailItem?.title }}</h4>
        <el-input
          v-if="detailItem"
          :model-value="detailItem.content"
          type="textarea"
          :rows="8"
          readonly
        />
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, computed } from 'vue'
import { Delete, Rank, Switch } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import VueDraggable from 'vuedraggable'

const props = defineProps({
  systemPromptList: {
    type: Array,
    required: true
  }
})

const emit = defineEmits(['update:systemPromptList', 'switch-to-step'])

// 双向绑定列表
const systemList = computed({
  get: () => props.systemPromptList,
  set: (val) => emit('update:systemPromptList', val)
})

// 切换状态
const activeBuilderType = ref('system')

// 详情弹窗
const detailVisible = ref(false)
const detailItem = ref(null)

// 查看详情
const handleViewDetail = (element) => {
  detailItem.value = element
  detailVisible.value = true
}

// 删除系统提示词
const handleDelete = async (element) => {
  try {
    await ElMessageBox.confirm(
      `确定删除系统提示词「${element.title}」吗？`,
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    const newList = systemList.value.filter(item => item.id !== element.id)
    emit('update:systemPromptList', newList)
    ElMessage.success('删除成功')
  } catch {
    // 用户取消
  }
}

// 清空系统提示词
const handleClear = async () => {
  try {
    await ElMessageBox.confirm('确定清空所有系统提示词吗？', '确认清空', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    emit('update:systemPromptList', [])
    ElMessage.success('已清空')
  } catch {
    // 用户取消
  }
}
</script>

<style scoped>
.system-sequencer-card {
  height: 100%;
  display: flex;
  flex-direction: column;
  position: relative;
}

.system-sequencer-card :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow: hidden;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-right {
  display: flex;
  align-items: center;
}

.system-sequencer-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.system-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.system-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.step-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 80px;
}

.step-label {
  font-size: 14px;
  font-weight: 500;
  color: #606266;
}

.system-card {
  flex: 1;
  cursor: pointer;
  transition: all 0.3s;
}

.system-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.system-card-content {
  display: flex;
  align-items: center;
  gap: 12px;
}

.drag-handle {
  cursor: move;
  color: #909399;
}

.system-title {
  flex: 1;
  font-size: 14px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.type-tag {
  flex-shrink: 0;
}

.system-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.system-sequencer-footer {
  padding: 16px;
  border-top: 1px solid #ebeef5;
  display: flex;
  justify-content: flex-end;
}

.detail-content h4 {
  margin: 0 0 16px 0;
  color: #303133;
}

/* 拖拽样式 */
.sortable-ghost {
  opacity: 0.5;
  background: #e6f7ff;
}

.sortable-drag {
  opacity: 0.8;
  transform: rotate(2deg);
}
</style>
