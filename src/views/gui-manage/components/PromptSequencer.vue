<template>
  <el-card class="sequencer-card" shadow="never">
    <template #header>
      <div class="card-header">
        <div class="header-left">
          <span>流程组装与执行</span>
          <el-button type="success" size="small" plain @click="importFlowVisible = true">
            <el-icon><Download /></el-icon>
            导入流程
          </el-button>
          <el-button type="warning" size="small" plain @click="emit('switch-to-system')">
            <el-icon><Switch /></el-icon>
            系统提示词
          </el-button>
        </div>
        <el-tag size="small" type="info">共 {{ sequenceList.length }} 步</el-tag>
      </div>
    </template>

    <div class="sequencer-body" ref="sequencerBodyRef">
      <div class="connection-container" @click="handleCanvasClick">
      
      <svg class="loop-canvas">
          <defs>
            <marker
              v-for="color in LOOP_COLORS"
              :key="color"
              :id="`arrowhead-${color.replace('#', '')}`"
              markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto"
            >
              <polygon points="0 0, 10 3.5, 0 7" :fill="color" />
            </marker>
          </defs>
          <g v-for="(loop, index) in loopLines" :key="index">
            <path :d="loop.path" fill="none" :stroke="loop.color" stroke-width="2" :marker-end="`url(#arrowhead-${loop.color.replace('#', '')})`" />
            <rect :x="loop.textX - 25" :y="loop.textY - 10" width="50" height="20" fill="#ffffff" :stroke="loop.color" stroke-width="1" rx="4" />
            <text :x="loop.textX" :y="loop.textY + 4" font-size="12" :fill="loop.color" text-anchor="middle">循环 {{ loop.count }} 次</text>
            <circle :cx="loop.textX + 35" :cy="loop.textY" r="8" fill="#f56c6c" style="pointer-events: auto; cursor: pointer;" @click.stop="removeLoop(index)" />
            <text :x="loop.textX + 35" :y="loop.textY + 3" font-size="10" fill="#fff" text-anchor="middle" style="pointer-events: none;">×</text>
          </g>
        </svg>

        <!-- 行级布局：每行包含步骤列 + 卡片列，由 VueDraggable 包裹 -->
        <VueDraggable
          v-model="localList"
          :group="{ name: 'prompts' }"
          :animation="200"
          item-key="id"
          class="sequence-list"
          ghost-class="sortable-ghost"
          drag-class="sortable-drag"
          @change="handleChange"
        >
          <template #item="{ element, index }">
            <div class="sequence-row" :data-id="element.id" @click.stop="handleRowClick(element, index)">
              <div class="step-cell">
                <el-checkbox
                  :model-value="index <= checkedStepIndex"
                  @change="(val) => handleCheckChange(index, val)"
                  @click.stop
                />
                <span class="step-label">步骤 {{ index + 1 }}</span>
              </div>

              <el-card class="sequence-card" shadow="never" :body-style="{ padding: '10px 16px' }">
                <div class="sequence-card-content">
                  <el-icon class="drag-handle" @click.stop><Rank /></el-icon>
                  <span class="step-title">{{ element.title }}</span>
                  <el-tag size="small" type="info" class="type-tag" v-if="element.type">
                    {{ getMiniPromptTypeLabel(element.type) }}
                  </el-tag>
                  <div class="step-actions">
                    <el-button type="primary" link size="small" @click.stop="handleViewDetail(element)">详情</el-button>
                    <el-button type="danger" link size="small" @click.stop="handleDelete(element)">删除</el-button>
                  </div>
                  <div class="loop-dot" :class="{ 'is-active': drawState.isDrawing && drawState.startId === element.id }" @click.stop="handleDotClick($event, element, index)"></div>
                </div>
              </el-card>
            </div>
          </template>

          <template #footer>
            <el-empty
              v-if="localList.length === 0"
              description="从上方拖入提示词卡片以组装流程"
              :image-size="80"
            />
          </template>
        </VueDraggable>
      </div>
    </div>

    <!-- 底部固定操作区 -->
    <div class="sequencer-footer">
      <el-button
        type="success"
        :disabled="sequenceList.length === 0 || checkedStepIndex < 0"
        @click="handleSave"
      >
        <el-icon><FolderChecked /></el-icon>
        保存勾选提示词
      </el-button>
      <el-button
        type="primary"
        :disabled="guiStore.isTaskRunning || !guiStore.taskId || selectedList.length === 0"
        @click="handleRun"
      >
        <el-icon><VideoPlay /></el-icon>
        运行勾选步骤
      </el-button>
      <el-button
        :disabled="sequenceList.length === 0"
        @click="handleClear"
      >
        <el-icon><Delete /></el-icon>
        清空流程
      </el-button>
    </div>

    <!-- 详情查看弹窗 -->
    <el-dialog v-model="detailVisible" title="提示词详情" width="500px">
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
    </el-dialog>

    <!-- 导入流程弹窗 -->
    <ImportPromptFlowDialog
      v-model="importFlowVisible"
      @import="handleImportFlow"
    />

    <!-- 保存流程弹窗 -->
    <el-dialog v-model="saveDialogVisible" title="保存为大提示词" width="450px" destroy-on-close>
      <el-form ref="saveFormRef" :model="saveForm" :rules="saveRules" label-width="100px">
        <el-form-item label="流程标题" prop="promptTitle">
          <el-input v-model="saveForm.promptTitle" placeholder="请输入大提示词标题" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="可见性" prop="isPublic">
          <el-radio-group v-model="saveForm.isPublic">
            <el-radio :label="0">私有</el-radio>
            <el-radio :label="1">公开</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="saveDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleConfirmSave" :loading="saveLoading">确定保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, computed, nextTick, reactive, watch, onMounted, onUnmounted } from 'vue'
import { Rank, View, VideoPlay, Delete, FolderChecked, Download } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import VueDraggable from 'vuedraggable'
import { useGuiStore } from '@/store/guiStore'
import { getMiniPromptTypeLabel } from '@/constants/prompt'
import { createPrompt } from '@/api/prompts'
import ImportPromptFlowDialog from './ImportPromptFlowDialog.vue'

const guiStore = useGuiStore()

const props = defineProps({
  sequenceList: {
    type: Array,
    required: true
  },
  systemPromptList: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['update:sequenceList', 'update:systemPromptList', 'run', 'switch-to-system'])

// 双向绑定列表
const localList = computed({
  get: () => props.sequenceList,
  set: (val) => emit('update:sequenceList', val)
})

// ========== 循环连线相关状态 ==========
// 连线色板
const LOOP_COLORS = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#8e44ad', '#d35400', '#16a085', '#2980b9']

// 循环连线数据: { fromId: string, toId: string, count: number, color: string }
const loopConnections = ref([])

// 画线状态
const drawState = reactive({
  isDrawing: false,
  startId: null,
  startIndex: -1
})
const sequencerBodyRef = ref(null)

// ========== 级联勾选逻辑 ==========
const checkedStepIndex = ref(-1)

// 当前勾选的步骤列表（用于按钮禁用判断）
const selectedList = computed(() => {
  if (checkedStepIndex.value < 0) return []
  return localList.value.slice(0, checkedStepIndex.value + 1)
})

const handleCheckChange = (index, checked) => {
  if (checked) {
    checkedStepIndex.value = index
  } else {
    checkedStepIndex.value = index - 1
  }
}

// ========== 拖拽逻辑 ==========
const handleChange = (evt) => {
  if (evt.added) {
    nextTick(() => {
      const newList = [...localList.value]
      if (newList[evt.added.newIndex]) {
        newList[evt.added.newIndex] = {
          ...newList[evt.added.newIndex],
          id: `seq_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
        }
        localList.value = newList
      }
      checkedStepIndex.value = -1
      console.log('【PromptSequencer】添加提示词:', JSON.parse(JSON.stringify(localList.value)))
    })
  }
  // 拖拽排序后，过滤掉不合规的连线
  const validIds = new Set(localList.value.map(item => item.id))
  loopConnections.value = loopConnections.value.filter(loop =>
    validIds.has(loop.fromId) && validIds.has(loop.toId)
  )
  console.log('【PromptSequencer】拖拽排序后提示词数组:', JSON.parse(JSON.stringify(localList.value)))
}

// ========== 详情弹窗 ==========
const detailVisible = ref(false)
const detailItem = ref(null)

const handleViewDetail = (item) => {
  detailItem.value = item
  detailVisible.value = true
}

// ========== 删除操作 ==========
const handleDelete = (item) => {
  const newList = localList.value.filter((i) => i.id !== item.id)
  localList.value = newList
  if (checkedStepIndex.value >= newList.length) {
    checkedStepIndex.value = newList.length - 1
  }
  console.log('【PromptSequencer】删除提示词后数组:', JSON.parse(JSON.stringify(localList.value)))
}

// ========== 导入流程逻辑 ==========
const importFlowVisible = ref(false)

/**
 * 处理导入流程：智能识别扁平数组中的重复结构并逆向折叠还原循环连线
 */
const handleImportFlow = async (row) => {
  try {
    await ElMessageBox.confirm('导入流程会将工作区的现有流程覆盖，是否继续？', '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    const miniPrompts = row.miniPrompts || []
    
    // 1. 初始化还原后的步骤卡片容器与连线容器
    const compressedList = []
    const restoredLoops = []
    
    // 2. 核心算法：滑动窗口连续模式匹配状态机
    let i = 0
    while (i < miniPrompts.length) {
      let foundLoop = false
      // 最大可能的循环体长度为剩余数组长度的一半
      const maxL = Math.floor((miniPrompts.length - i) / 2)
      
      // 优先匹配最长的重复块，确保大循环优先被捕获
      for (let L = 1; L <= maxL; L++) {
        const pattern = miniPrompts.slice(i, i + L)
        
        let matchCount = 1
        // 探测后续连续块是否与当前 pattern 完全匹配
        while (i + (matchCount + 1) * L <= miniPrompts.length) {
          const nextBlock = miniPrompts.slice(i + matchCount * L, i + (matchCount + 1) * L)
          // 通过比对 content 核心文本内容判断是否为重复步骤
          const isMatch = pattern.every((item, idx) => item.content === nextBlock[idx].content)
          
          if (isMatch) {
            matchCount++
          } else {
            break
          }
        }
        
        // 如果 matchCount > 1，证明找到了连续循环结构
        if (matchCount > 1) {
          // 为第一个周期的步骤元素生成全新的唯一 ID
          const patternWithIds = pattern.map((item, idx) => ({
            ...item,
            id: `seq_${Date.now()}_${compressedList.length + idx}_${Math.random().toString(36).substr(2, 5)}`
          }))
          
          // 将压缩后的首个周期塞入列表
          compressedList.push(...patternWithIds)
          
          // 逆向构筑连线关系：从循环体的最后一个元素连回到第一个元素
          const toItem = patternWithIds[0]
          const fromItem = patternWithIds[patternWithIds.length - 1]
          
          restoredLoops.push({
            fromId: fromItem.id,
            toId: toItem.id,
            count: matchCount - 1, // 除去本身的执行，循环次数 = 总块数 - 1
            color: LOOP_COLORS[restoredLoops.length % LOOP_COLORS.length] // 按顺序分配色板颜色
          })
          
          // 指针跨越式前移，直接跳过后面所有被折叠的重复数据块
          i += matchCount * L
          foundLoop = true
          break
        }
      }
      
      // 如果在此位置未探测到任何循环模式，作为普通单步步骤推入，并赋予新 ID
      if (!foundLoop) {
        compressedList.push({
          ...miniPrompts[i],
          id: `seq_${Date.now()}_${compressedList.length}_${Math.random().toString(36).substr(2, 5)}`
        })
        i++
      }
    }

    // 3. 将还原并压缩好的数据响应式写入系统状态
    emit('update:sequenceList', compressedList)
    loopConnections.value = restoredLoops
    checkedStepIndex.value = -1 // 重置勾选

    // 4. 导入系统提示词（如果存在）
    const systemPrompts = row.systemPrompts || []
    if (systemPrompts.length > 0) {
      const systemListWithIds = systemPrompts.map((item) => ({
        ...item,
        id: item.id || `system_${Date.now()}_${Math.random().toString(36).substr(2, 5)}`
      }))
      emit('update:systemPromptList', systemListWithIds)
    }

    console.log('【PromptSequencer】智能导入逆向还原成功！卡片数:', compressedList.length, '连线数:', restoredLoops.length, '系统提示词:', systemPrompts.length)
    ElMessage.success(`成功导入流程「${row.promptTitle}」，已自动识别并智能折叠还原循环结构`)

  } catch (error) {
    if (error !== 'cancel') {
      console.error('导入流程失败:', error)
      ElMessage.error('导入流程解析出现异常')
    }
  }
}

// ========== 保存流程逻辑 ==========
const saveDialogVisible = ref(false)
const saveLoading = ref(false)
const saveFormRef = ref(null)
const saveForm = ref({
  promptTitle: '',
  isPublic: 0
})
const pendingSaveList = ref([])

const saveRules = {
  promptTitle: [
    { required: true, message: '请输入流程标题', trigger: 'blur' },
    { min: 2, max: 100, message: '长度在 2 到 100 个字符', trigger: 'blur' }
  ]
}

/**
 * 保存勾选步骤
 * 弹出对话框让用户输入标题和选择可见性
 */
const handleSave = () => {
  if (checkedStepIndex.value < 0) {
    ElMessage.warning('请至少勾选一个步骤后再保存')
    return
  }
  
  // 🔥 核心修改：保存时也使用展平后的步骤数组
  const unrolledSteps = generateUnrolledSteps()
  if (!unrolledSteps) return

  pendingSaveList.value = unrolledSteps
  saveForm.value = { promptTitle: '', isPublic: 0 }
  saveDialogVisible.value = true
}

/**
 * 确认保存：组装 payload 并调用 createPrompt 接口
 */
const handleConfirmSave = async () => {
  if (!saveFormRef.value) return
  await saveFormRef.value.validate(async (valid) => {
    if (!valid) return

    saveLoading.value = true
    try {
      const payload = {
        userId: 1,
        categoryId: '1',
        status: '1',
        isPublic: String(saveForm.value.isPublic),
        promptTitle: saveForm.value.promptTitle,
        systemPrompts: props.systemPromptList.map((item) => ({
          id: item.id.includes('system') ? '' : String(item.id),
          title: item.title,
          content: item.content
        })),
        miniPrompts: pendingSaveList.value.map((item, index) => ({
          id: item.id.includes('builder') || item.id.includes('seq') ? '' : String(item.id),
          title: item.title,
          content: item.content,
          step: index + 1
        }))
      }
      await createPrompt(payload)
      ElMessage.success('流程保存成功')
      saveDialogVisible.value = false
    } catch (error) {
      console.error('保存流程失败:', error)
    } finally {
      saveLoading.value = false
    }
  })
}

// ========== 循环连线核心逻辑 ==========
// 点击圆点：开始或完成画线
const handleDotClick = async (e, item, index) => {
  if (guiStore.isTaskRunning) return
  
  if (!drawState.isDrawing) {
    // 第一次点击：设为起点
    drawState.isDrawing = true
    drawState.startId = item.id
    drawState.startIndex = index
  } else {
    const targetIndex = index
    const sourceIndex = drawState.startIndex
    
    // 1. 点自己，取消
    if (drawState.startId === item.id) {
      resetDraw()
      return
    }
    // 2. 方向必须向上
    if (targetIndex >= sourceIndex) {
      ElMessage.warning('循环连线只能向上指（指向当前步骤之前的步骤）')
      resetDraw()
      return
    }
    
    // 3. 校验区间交叉重叠
    const hasIntersection = loopConnections.value.some(loop => {
      const eStart = localList.value.findIndex(i => i.id === loop.fromId)
      const eTarget = localList.value.findIndex(i => i.id === loop.toId)
      if (eStart === -1 || eTarget === -1) return false
      
      // 判断两个区间 [targetIndex, sourceIndex] 和 [eTarget, eStart] 是否有交集
      const min1 = targetIndex, max1 = sourceIndex
      const min2 = eTarget, max2 = eStart
      return Math.max(min1, min2) <= Math.min(max1, max2)
    })
    
    if (hasIntersection) {
      ElMessage.warning('循环区间不能与其他循环交叉、重叠或包含')
      resetDraw()
      return
    }
    
    // 触发连线弹窗
    try {
      const { value } = await ElMessageBox.prompt('请输入循环次数', '设置循环', {
        inputPattern: /^[1-9]\d*$/,
        inputErrorMessage: '请输入大于0的正整数'
      })

      loopConnections.value.push({
        fromId: drawState.startId,
        toId: item.id,
        count: Number(value),
        color: LOOP_COLORS[loopConnections.value.length % LOOP_COLORS.length]
      })
    } catch {}
    resetDraw()
  }
}

// 点击卡片行：取消画线状态
const handleRowClick = (item, index) => {
  if (drawState.isDrawing) {
    resetDraw()
  }
}

// 点击画布空白处取消
const handleCanvasClick = () => {
  if (drawState.isDrawing) resetDraw()
}

const resetDraw = () => {
  drawState.isDrawing = false
  drawState.startId = null
  drawState.startIndex = -1
}

const removeLoop = (index) => loopConnections.value.splice(index, 1)

// ========== 强制 SVG 重绘触发器 ==========
const renderTrigger = ref(0)

const triggerSvgRender = () => {
  renderTrigger.value++
}

// 监听窗口大小变化，防止调整浏览器窗口时连线错位
onMounted(() => window.addEventListener('resize', triggerSvgRender))
onUnmounted(() => window.removeEventListener('resize', triggerSvgRender))

// 🔥 核心修复：监听列表和连线的数据变化，等待 Vue 将新 DOM 渲染到屏幕后，再强行触发连线坐标的计算
watch([localList, loopConnections], () => {
  nextTick(() => {
    triggerSvgRender()
  })
}, { deep: true })

// 计算生成的折线
const loopLines = computed(() => {
  // 🔥 核心修复：声明依赖，只要 renderTrigger 变动，必定重新计算连线
  renderTrigger.value

  if (!sequencerBodyRef.value) return []
  const containerRect = sequencerBodyRef.value.getBoundingClientRect()
  const scrollTop = sequencerBodyRef.value.scrollTop // 必须加上滚动条偏移量
  
  return loopConnections.value.map(loop => {
    const fromEl = sequencerBodyRef.value.querySelector(`[data-id="${loop.fromId}"] .loop-dot`)
    const toEl = sequencerBodyRef.value.querySelector(`[data-id="${loop.toId}"] .loop-dot`)
    if (!fromEl || !toEl) return null
    
    const fromRect = fromEl.getBoundingClientRect()
    const toRect = toEl.getBoundingClientRect()
    
    // 加上 scrollTop 修正 Y 轴坐标
    const startX = fromRect.left - containerRect.left + fromRect.width / 2
    const startY = fromRect.top - containerRect.top + scrollTop + fromRect.height / 2
    const endY = toRect.top - containerRect.top + scrollTop + toRect.height / 2
    const extendX = startX + 45 // 向右延伸形成折线
    
    return {
      path: `M ${startX} ${startY} L ${extendX} ${startY} L ${extendX} ${endY} L ${startX + 10} ${endY}`, // startX + 10 让箭头刚好顶到圆点
      textX: extendX,
      textY: startY - (startY - endY) / 2,
      count: loop.count,
      color: loop.color || '#409eff'
    }
  }).filter(Boolean)
})

// ========== 虚拟执行指针：展平循环数组 ==========
const generateUnrolledSteps = () => {
  const baseList = localList.value.slice(0, checkedStepIndex.value + 1)
  const unrolledList = []
  const loopCounters = {}
  
  let pointer = 0
  let safeGuard = 0
  const MAX_STEPS = 1000 // 防死循环

  while (pointer < baseList.length && safeGuard < MAX_STEPS) {
    const currentStep = baseList[pointer]
    // 注意：这里推入的是完整的对象，方便保存和运行各自提取所需字段
    unrolledList.push(currentStep)

    const loop = loopConnections.value.find(c => c.fromId === currentStep.id)
    if (loop) {
      const loopKey = `${loop.fromId}_${loop.toId}`
      if (loopCounters[loopKey] === undefined) loopCounters[loopKey] = 0

      if (loopCounters[loopKey] < loop.count) {
        loopCounters[loopKey]++
        const targetIndex = baseList.findIndex(step => step.id === loop.toId)
        if (targetIndex !== -1 && targetIndex <= pointer) {
          pointer = targetIndex // 指针回跳
          safeGuard++
          continue
        }
      } else {
        // 循环完毕，重置计数器以便外层嵌套循环重新进入
        loopCounters[loopKey] = 0
      }
    }
    pointer++
    safeGuard++
  }

  if (safeGuard >= MAX_STEPS) {
    ElMessage.error('循环次数过多，触发安全保护停止')
    return null
  }

  return unrolledList
}

// ========== 运行与清空 ==========
const handleRun = () => {
  if (checkedStepIndex.value < 0) {
    ElMessage.warning('请至少勾选一个步骤后再运行')
    return
  }
  
  // 1. 调用公共展平算法获取完整步骤流
  const unrolledSteps = generateUnrolledSteps()
  if (!unrolledSteps) return // 如果触发了死循环保护则中止
  
  // 2. 提取后端接口所需的 content 数组
  const instructions = unrolledSteps.map(step => step.content)

  // 3. 获取系统提示词列表
  const sysPrompts = props.systemPromptList.map(item => ({
    id: item.id,
    content: item.content,
    title: item.title
  }))

  console.log('【PromptSequencer】运行流程生成的指令数组:', instructions)
  console.log('【PromptSequencer】系统提示词:', sysPrompts)
  emit('run', { instructions, sysPrompts })
}

const handleClear = async () => {
  try {
    await ElMessageBox.confirm('确定清空所有流程步骤吗？', '确认清空', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    emit('update:sequenceList', [])
    checkedStepIndex.value = -1
    loopConnections.value = []
    console.log('【PromptSequencer】清空后数组:', JSON.parse(JSON.stringify(localList.value)))
    ElMessage.success('已清空')
  } catch {
    // 用户取消
  }
}
</script>

<style scoped>
.sequencer-card {
  height: 100%;
  display: flex;
  flex-direction: column;
  position: relative;
}

.sequencer-card :deep(.el-card__body) {
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
  font-size: 16px;
  font-weight: bold;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* 主体区域 */
.sequencer-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  position: relative;
}

/* 连线容器 */
.connection-container {
  position: relative;
  width: 100%;
  height: 100%;
}

/* SVG 画布 */
.loop-canvas {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 10;
  overflow: visible;
}

/* ========== 行级布局（保证左右完美对齐） ========== */
.sequence-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 200px;
  height: 100%;
}

.sequence-row {
  display: flex;
  align-items: stretch;
  gap: 12px;
  min-height: 48px;
  /* 🔥 核心优化：强制留出右侧 70px 空间给连线使用，避免被卡片挤压或遮挡 */
  padding-right: 70px;
  position: relative;
  z-index: 2; /* 保证卡片交互在最上 */
}

/* 左侧步骤与勾选列 */
.step-cell {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  padding-right: 12px;
  border-right: 1px solid var(--el-border-color-lighter);
  min-width: 100px;
}

.step-label {
  flex-shrink: 0;
  font-size: 12px;
  color: #409eff;
  font-weight: bold;
  background: #ecf5ff;
  padding: 2px 8px;
  border-radius: 4px;
  white-space: nowrap;
}

/* 右侧卡片列 */
.sequence-card {
  flex: 1;
  min-width: 0;
  cursor: default;
  border: 1px solid var(--el-border-color-lighter);
}

.sequence-card-content {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  position: relative;
}

/* 循环连线圆点 */
.loop-dot {
  width: 12px;
  height: 12px;
  background-color: #fff;
  border: 2px solid #c0c4cc;
  border-radius: 50%;
  cursor: crosshair;
  pointer-events: auto;
  transition: all 0.3s;
  margin-left: 10px;
  flex-shrink: 0;
}

.loop-dot:hover {
  border-color: #409eff;
  transform: scale(1.3);
  background-color: #ecf5ff;
}

.loop-dot.is-active {
  border-color: #f56c6c;
  background-color: #fef0f0;
  transform: scale(1.3);
  box-shadow: 0 0 8px rgba(245, 108, 108, 0.5);
}

.drag-handle {
  color: #c0c4cc;
  cursor: grab;
  flex-shrink: 0;
}

.drag-handle:active {
  cursor: grabbing;
}

.step-title {
  flex: 1;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.step-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.type-tag {
  flex-shrink: 0;
}

/* ========== 底部操作区 ========== */
.sequencer-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px 16px;
  border-top: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
  position: sticky;
  bottom: 0;
  z-index: 10;
}

/* 详情弹窗 */
.detail-content h4 {
  margin: 0 0 12px 0;
  color: #303133;
}

/* 拖拽时的幽灵样式 */
.sequence-list :deep(.sortable-ghost) {
  opacity: 0.4;
}

.sequence-list :deep(.sortable-drag) {
  opacity: 0.9;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}
</style>
