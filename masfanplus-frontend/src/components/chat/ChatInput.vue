<!--
  ChatInput - 底部输入区组件
-->
<script setup lang="ts">
import { ref, watch } from 'vue'
import { Select as ASelect } from 'ant-design-vue'
import { ElMessageBox, ElMessage } from 'element-plus'

// Agent 选项配置
const agentOptions = [
  { label: '利润分析模式', value: 'FinanceForecastAgent' },
  { label: '用户分析模式', value: 'MasterUserAgents' },
  { label: '菜单生成模式', value: 'MasterSPAgents' }

]

// Emits 定义
const emit = defineEmits<{
  send: [text: string]
  stop: []
  'switch-agent': [agentId: string]
}>()

// Props 定义
const props = defineProps<{
  isLoading: boolean
  currentAgent: string
}>()

// 内部状态
const inputText = ref('')
const selectedAgent = ref(props.currentAgent)

// 监听 currentAgent 的变化，同步更新 selectedAgent
watch(() => props.currentAgent, (newVal: string) => {
  selectedAgent.value = newVal
})

// 处理 Agent 切换
const handleAgentChange = (value: any) => {
  emit('switch-agent', value as string);
}

// 发送消息
const handleSend = () => {
  const text = inputText.value.trim()
  if (text) {
    emit('send', text)
    inputText.value = ''
  }
}

// 停止生成
const handleStop = () => {
  emit('stop')
}

// 键盘事件处理
const handleKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

// 打开自定义模式对话框
const openCustomModeDialog = async () => {
  try {
    const { value } = await ElMessageBox.prompt(
      '请输入agentid',
      '自定义模式',
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        inputPlaceholder: '请输入agentid',
        inputValidator: (value: string) => {
          if (!value || value.trim() === '') {
            return 'agentid不能为空'
          }
          return true
        }
      }
    )
    
    if (value && value.trim()) {
      const customAgentId = value.trim()
      // 触发自定义 agent 切换
      emit('switch-agent', customAgentId)
      ElMessage.success(`已切换到自定义模式: ${customAgentId}`)
    }
  } catch (error) {
    // 用户取消操作，不做处理
    console.log('用户取消了自定义模式设置')
  }
}

</script>

<template>
  <div class="input-area">
    <div class="input-container">
      <div class="input-top-row">
        <textarea
          v-model="inputText"
          @keydown="handleKeydown"
          placeholder="输入消息..."
          rows="1"
          :disabled="isLoading"
        ></textarea>
      </div>
      <div class="input-bottom-row">
        <div class="bottom-left">
          <button class="input-tool-btn" title="添加附件">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 4v16m8-8H4" />
            </svg>
          </button>
        </div>
        <div class="bottom-right">
          <a-select
            v-model:value="selectedAgent"
            @change="handleAgentChange"
            :options="agentOptions"
            placeholder="选择模式"
            style="width: 150px;"
            size="middle"
            :bordered="false"
          >
          </a-select>
          <!-- 自定义模式按钮 -->
          <button
            @click="openCustomModeDialog"
            class="custom-mode-btn"
            title="自定义模式"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
            </svg>
            <span>自定义</span>
          </button>
          <button
            v-if="isLoading"
            @click="handleStop"
            class="input-action-btn stop-btn"
            title="停止生成"
          >
            <svg viewBox="0 0 24 24" fill="currentColor">
              <rect x="6" y="6" width="12" height="12" rx="2" />
            </svg>
          </button>
          <button
            v-else
            @click="handleSend"
            :disabled="!inputText.trim()"
            class="input-action-btn send-btn"
            title="发送消息"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z" />
            </svg>
          </button>
        </div>
      </div>
    </div>
    <div class="input-footer">
      AgentScope 可能会生成不准确的信息，请验证重要信息。
    </div>
  </div>
</template>

<style scoped>
/* 底部输入区 */
.input-area {
  padding: 0 24px 24px;
  background: var(--bg-main, #FFFFFF);
}

.input-container {
  max-width: 900px;
  margin: 0 auto;
  display: flex;
  flex-direction: column; /* 改为列方向 */
  gap: 12px;
  padding: 12px 16px;
  background: var(--bg-input, #F0F4F9);
  border-radius: 24px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.05);
  transition: box-shadow 0.2s;
}

.input-top-row {
  width: 100%;
  display: flex;
}

.input-top-row textarea {
  width: 100%;
  /* 保留原有的 textarea 去除边框、背景透明等属性 */
  min-height: 48px; /* 给一个合适的初始高度 */
}

.input-bottom-row {
  display: flex;
  justify-content: space-between; /* 左右两端对齐 */
  align-items: center;
  width: 100%;
  margin-top: 8px; /* 和上方的输入框拉开一点间距 */
}

.bottom-left {
  display: flex;
  align-items: center;
}

.bottom-right {
  display: flex;
  align-items: center;
  gap: 12px; /* 下拉框和发送按钮之间的间距 */
}

/* 清理旧样式：删除之前为了调整顺序而写的 .input-container .ant-select { order: 3; align-self: center; } */

.input-container:focus-within {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.input-tool-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary, #444746);
  transition: all 0.2s;
  flex-shrink: 0;
}

.input-tool-btn:hover {
  background: rgba(0, 0, 0, 0.05);
  color: var(--text-primary, #1F1F1F);
}

.input-tool-btn svg {
  width: 20px;
  height: 20px;
}

/* 自定义模式按钮样式 */
.custom-mode-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: 1px solid var(--border-color, #E3E3E3);
  border-radius: 16px;
  background: var(--bg-main, #FFFFFF);
  color: var(--text-secondary, #444746);
  font-size: 0.8125rem;
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;
}

.custom-mode-btn:hover {
  background: var(--bg-selected, #D3E3FD);
  border-color: var(--accent-color, #667eea);
  color: var(--accent-color, #667eea);
}

.custom-mode-btn svg {
  width: 16px;
  height: 16px;
}

.custom-mode-btn span {
  white-space: nowrap;
}

.input-container textarea {
  flex: 1;
  border: none;
  background: transparent;
  font-size: 0.9375rem;
  line-height: 1.5;
  resize: none;
  outline: none;
  color: var(--text-primary, #1F1F1F);
  font-family: inherit;
  max-height: 120px;
  min-height: 24px;
  padding: 6px 0;
}

.input-container textarea::placeholder {
  color: var(--text-secondary, #444746);
  opacity: 0.6;
}

.input-container textarea:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.input-action-btn {
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  flex-shrink: 0;
}

.send-btn {
  background: var(--accent-color, #667eea);
  color: white;
}

.send-btn:hover:not(:disabled) {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.send-btn svg {
  width: 18px;
  height: 18px;
  margin-left: 2px;
}

.stop-btn {
  background: #ef4444;
  color: white;
}

.stop-btn:hover {
  background: #dc2626;
  transform: scale(1.05);
}

.stop-btn svg {
  width: 14px;
  height: 14px;
}

.input-footer {
  max-width: 900px;
  margin: 8px auto 0;
  text-align: center;
  font-size: 0.75rem;
  color: var(--text-secondary, #444746);
  opacity: 0.6;
}


/* 响应式设计 */
@media (max-width: 768px) {
  .input-area {
    padding: 0 16px 16px;
  }
  
  .bottom-right {
    gap: 8px;
  }
  
  .custom-mode-btn span {
    display: none;
  }
  
  .custom-mode-btn {
    padding: 6px;
  }
}
</style>
