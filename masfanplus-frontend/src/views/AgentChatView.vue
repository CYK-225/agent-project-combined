<!--
  AgentChatView - 主视图容器
  职责：状态管理、SSE通信、核心逻辑
-->
<script setup lang="ts">
import { ref, computed, onUnmounted, nextTick, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessageBox, ElMessage } from 'element-plus'
import type { Message, ChatHistory } from '../types/chat'
import { interceptMessage, convertChatMessageVOToMessages } from '../types/chat'
import ChatSidebar from '../components/chat/ChatSidebar.vue'
import ChatHeader from '../components/chat/ChatHeader.vue'
import MessageList from '../components/chat/MessageList.vue'
import ChatInput from '../components/chat/ChatInput.vue'
import SkillManager from '../components/chat/SkillManager.vue'
import { sessionApi } from '../api/session'
import { useSessionStore } from '../store/modules/sessionStore'
import { useChatStore } from '../store/modules/chatStore'

// ==================== 初始化 Store ====================
const sessionStore = useSessionStore()
const chatStore = useChatStore()

// 使用 storeToRefs 解构 store 中的状态，使其保持响应性
const { chatHistory, currentThreadId, isLoading,currentAgentId } = storeToRefs(sessionStore)
const { messages, isWaitingForResponse } = storeToRefs(chatStore)
// ==================== 响应式状态 ====================
const sidebarCollapsed = ref(false)
const showSkillManager = ref(false)

// ==================== 新增：多会话隔离状态 ====================
// 缓存每个会话的消息列表引用，实现后台静默更新
const sessionMessagesCache = ref<Map<string, Message[]>>(new Map())
// 记录正在后台生成的会话 ID
const activeStreams = ref<Set<string>>(new Set())
// 记录正在等待首 Token 响应的会话 ID
const waitingStreams = ref<Set<string>>(new Set())
// 记录每个会话专属的中断控制器
const abortControllers = ref<Map<string, AbortController>>(new Map())

// 计算当前页面是否处于生成或等待状态
const isGenerating = computed(() => activeStreams.value.has(currentThreadId.value))
const isCurrentWaiting = computed(() => waitingStreams.value.has(currentThreadId.value))
// 综合 Loading 状态：网络请求 isLoading + 本地生成 isGenerating
const isUILoading = computed(() => isLoading.value || isGenerating.value)

// 调试面板状态
const showDebug = ref(false)
const debugEvents = ref<any[]>([])
const isDev = import.meta.env.DEV

// 当前用户信息和会话核心状态
// 注意：当前 userID 已在 API 层强制写死为 user001，此处保留变量供未来扩展
const currentUserId = ref('user-001') // 预留：此处后续替换为系统实际登录用户的 ID

// 消息列表组件引用
const messageListRef = ref<InstanceType<typeof MessageList> | null>(null)

// ==================== 计算属性 ====================
const runningReasoningCount = computed(() => 
  messages.value.filter(m => m.role === 'reasoning' && m.status === 'running').length
)

const runningToolCount = computed(() => 
  messages.value.filter(m => m.role === 'tool' && m.status === 'running').length
)

const hasReasoningOrTool = computed(() => 
  messages.value.some(m => m.role === 'reasoning' || m.role === 'tool')
)

// ==================== 工具函数 ====================

// 添加调试事件
const addDebugEvent = (event: any) => {
  const timestamp = new Date().toLocaleTimeString()
  debugEvents.value.unshift({
    id: Date.now() + Math.random(),
    timestamp,
    type: event.type || 'UNKNOWN',
    data: event
  })
  if (debugEvents.value.length > 50) {
    debugEvents.value = debugEvents.value.slice(0, 50)
  }
}

// 清除调试事件
const clearDebugEvents = () => {
  debugEvents.value = []
}

// ==================== SSE 原生实现 ====================
function handleAguiEvent(event: any, targetThreadId: string) {
  const {
    type,
    content,
    delta,
    messageId,
    id,
    toolCallId,
    role,
    tool_calls,
    tool_call_id,
    name
  } = event

  // 修复：使用 toolCallId 强制绑定工具事件，防止 messageId 漂移导致气泡脱节
  let baseId = messageId || id || toolCallId || `msg-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`
  if (type?.startsWith('TOOL_CALL_') && toolCallId) {
    baseId = toolCallId
  }

  // 核心隔离：优先从缓存找，找不到且是当前会话才用 messages.value
  const cachedMsgs = sessionMessagesCache.value.get(targetThreadId)
  const targetMessages = cachedMsgs
    ? cachedMsgs
    : (targetThreadId === currentThreadId.value ? messages.value : null)

  // 如果既没缓存，也不是当前显示的 ID（说明该流已过时或属于已删除会话），直接丢弃该包
  if (!targetMessages) return

  if (type?.endsWith('_END') || type === 'RUN_STARTED') {
    if (type === 'REASONING_MESSAGE_END') {
      const reasoningMsg = targetMessages.find(m => m.id?.startsWith(baseId) && m.role === 'reasoning')
      if (reasoningMsg) reasoningMsg.status = 'success'
    }
    if (type === 'TOOL_CALL_END') {
      const toolMsg = targetMessages.find(m => m.id?.startsWith(baseId) && m.role === 'tool')
      if (toolMsg) toolMsg.status = 'success'
    }
    return
  }

  const textContent = delta || content || ''
  if (!textContent && type !== 'TOOL_CALL_START' && type !== 'TOOL_CALL_END') {
    return
  }

  // 接收到第一个有效Chunk，立即关闭等待状态
  waitingStreams.value.delete(targetThreadId)

  switch (type) {
    case 'REASONING_MESSAGE_START':
    case 'REASONING_MESSAGE_CONTENT': {
      const uiId = `${baseId}-reasoning`
      const existingIndex = targetMessages.findIndex(m => m.id === uiId)
      if (existingIndex >= 0) {
        targetMessages[existingIndex].content += textContent
      } else {
        targetMessages.push({
          id: uiId,
          role: 'reasoning',
          content: textContent,
          _originalRole: role || 'reasoning',
          isCollapsed: true,
          status: 'running'
        })
      }
      break
    }

    case 'TEXT_MESSAGE_START':
    case 'TEXT_MESSAGE_CONTENT': {
      const uiId = `${baseId}-assistant`
      const existingIndex = targetMessages.findIndex(m => m.id === uiId)
      if (existingIndex >= 0) {
        targetMessages[existingIndex].content += textContent
      } else {
        targetMessages.push({
          id: uiId,
          role: 'assistant',
          content: textContent,
          _originalRole: role || 'assistant'
        })
      }
      break
    }

    case 'TOOL_CALL_START': {
      const toolName = name || event.toolCallName || tool_calls?.[0]?.function?.name || '未知工具'
      targetMessages.push({
        id: `${baseId}-tool`,
        role: 'tool',
        content: '',
        params: '',
        toolName: toolName,
        _originalRole: 'tool',
        isCollapsed: true,
        status: 'running'
      })
      break
    }

    case 'TOOL_CALL_ARGS': {
      const toolMsg = targetMessages.find(m => m.id === `${baseId}-tool`)
      if (toolMsg) {
        toolMsg.params = (toolMsg.params || '') + textContent
      }
      break
    }

    case 'TOOL_CALL_RESULT': {
      const toolMsg = targetMessages.find(m => m.id === `${baseId}-tool`)
      if (toolMsg) {
        toolMsg.content = textContent
        toolMsg.status = 'success'
      }
      break
    }

    default: {
      const intercepted = interceptMessage(event)
      if (intercepted.content) {
        const uiId = `${baseId}-default`
        const existingIndex = targetMessages.findIndex(m => m.id === uiId)
        if (existingIndex >= 0) {
          targetMessages[existingIndex].content += intercepted.content
        } else {
          targetMessages.push({
            id: uiId,
            role: intercepted.role,
            content: intercepted.content,
            _originalRole: role || intercepted.role
          })
        }
      }
    }
  }

  nextTick(() => {
    if (targetThreadId === currentThreadId.value) {
      messageListRef.value?.scrollToBottom()
    }
  })
}

async function runAgentNative(runId: string, userMessage: Message, targetThreadId: string) {
  const controller = new AbortController()
  abortControllers.value.set(targetThreadId, controller)
  activeStreams.value.add(targetThreadId)
  
  try {
    const response = await fetch(`/agui/run/${currentAgentId.value}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream'
      },
      body: JSON.stringify({
        threadId: targetThreadId || 'default-thread',
        runId: runId,
        messages: [userMessage]
      }),
      signal: controller.signal
    })

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    if (!response.body) {
      throw new Error('Response body is null')
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      
      if (done) {
        break
      }

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('data: ')) {
          const dataStr = line.slice(6).trim()
          
          if (dataStr === '[DONE]') {
            continue
          }

          try {
            const event = JSON.parse(dataStr)
            if (isDev) {
              addDebugEvent(event)
            }
            handleAguiEvent(event, targetThreadId)
          } catch (e) {
            console.warn('Failed to parse SSE data:', dataStr, e)
          }
        }
      }
    }

    if (buffer.startsWith('data: ')) {
      const dataStr = buffer.slice(6).trim()
      if (dataStr && dataStr !== '[DONE]') {
        try {
          const event = JSON.parse(dataStr)
          if (isDev) {
            addDebugEvent(event)
          }
          handleAguiEvent(event, targetThreadId)
        } catch (e) {
          console.warn('Failed to parse final SSE data:', dataStr)
        }
      }
    }

  } catch (error: any) {
    if (error.name === 'AbortError') {
      console.log('Request was aborted')
    } else {
      console.error('SSE request failed:', error)
      throw error
    }
  } finally {
    abortControllers.value.delete(targetThreadId)
    activeStreams.value.delete(targetThreadId)
    waitingStreams.value.delete(targetThreadId)
  }
}

// ==================== 事件处理 ====================

// 发送消息
const handleSend = async (text: string) => {
  if (!text || isUILoading.value) return

  // 拦截：如果当前没有 currentThreadId，说明这是一个临时的本地新会话，需要先在后端创建真实会话
  if (!currentThreadId.value) {
    try {
      isLoading.value = true
      await sessionStore.createRealSession()
    } catch (error) {
      console.error('创建会话失败，无法发送消息:', error)
      ElMessage.error('网络错误：创建会话失败')
      isLoading.value = false
      return // 创建失败则终止发送
    }
  }

  // 记录是否为当前会话的第一条消息
  const isFirstMessage = messages.value.length === 0

  const runId = 'run-' + Date.now()
  const msgId = 'msg-' + Date.now()

  const userMessage: Message = {
    id: msgId,
    role: 'user',
    content: text
  }

  messages.value.push(userMessage)
  
  const targetThreadId = currentThreadId.value
  sessionMessagesCache.value.set(targetThreadId, messages.value)
  waitingStreams.value.add(targetThreadId)

  nextTick(() => {
    messageListRef.value?.scrollToBottom()
  })

  // 如果是首条消息，异步更新会话标题（不阻塞对话请求）
  if (isFirstMessage && currentThreadId.value) {
    // 注意：这里不要加 await，让它在后台静默生成，不阻塞底下的对话流请求
    sessionStore.generateAndUpdateTitle(currentThreadId.value, text)
  }

  try {
    await runAgentNative(runId, userMessage, targetThreadId)
  } catch (error) {
    console.error('智能体运行失败:', error)
    messages.value.push({
      id: 'error-' + Date.now(),
      role: 'system',
      content: '抱歉，发生了错误，请稍后重试。'
    })
  } finally {
    isLoading.value = false
  }
}

// 停止生成
const handleStop = () => {
  const controller = abortControllers.value.get(currentThreadId.value)
  if (controller) {
    controller.abort()
    abortControllers.value.delete(currentThreadId.value)
    activeStreams.value.delete(currentThreadId.value)
    waitingStreams.value.delete(currentThreadId.value)
  }
}

// 切换调试面板
const toggleDebug = () => {
  showDebug.value = !showDebug.value
}

// 展开全部
const expandAll = () => {
  messages.value.forEach(m => {
    if (m.role === 'reasoning' || m.role === 'tool') {
      m.isCollapsed = false
    }
  })
}

// 折叠全部
const collapseAll = () => {
  messages.value.forEach(m => {
    if (m.role === 'reasoning' || m.role === 'tool') {
      m.isCollapsed = true
    }
  })
}

// 切换消息折叠状态
const toggleCollapse = (message: Message) => {
  message.isCollapsed = !message.isCollapsed
}

// 处理 Agent 切换
const handleSwitchAgent = async (newAgentId: string) => {
  try {
    const confirm = await ElMessageBox.confirm(
      '切换模式会新建一个会话，但是当前会话的记录会被保留，确定要继续吗？',
      '切换模式',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      }
    );
    
    if (confirm) {
      // 切换前保存快照
      if (currentThreadId.value) {
        sessionMessagesCache.value.set(currentThreadId.value, messages.value)
      }
      sessionStore.currentAgentId = newAgentId
      sessionStore.prepareLocalSession()
      chatStore.clearMessages()
      ElMessage.success('模式切换成功')
    }
  } catch (error) {
    // 用户点击取消或关闭对话框
    console.log('用户取消了模式切换');
    return;
  }
};

// 接入真实 API 的新建对话逻辑
const handleNewChat = () => {
  // 核心修复：在切走前，如果当前有 ID，强制保存当前消息引用的快照
  if (currentThreadId.value) {
    sessionMessagesCache.value.set(currentThreadId.value, messages.value)
  }
  // 准备本地会话（将 currentThreadId 置为 ""）
  sessionStore.prepareLocalSession()
  chatStore.clearMessages()
}

// 切换选择历史对话
const handleSelectChat = async (chat: ChatHistory) => {
  if (currentThreadId.value) {
    sessionMessagesCache.value.set(currentThreadId.value, messages.value)
  }
  sessionStore.selectSession(chat)

  if (activeStreams.value.has(chat.id)) {
    // 如果目标会话正在后台生成，直接恢复缓存数组接管视图
    messages.value = sessionMessagesCache.value.get(chat.id) || []
  } else {
    await chatStore.loadHistoryMessages(chat.id)
    sessionMessagesCache.value.set(chat.id, messages.value)
  }
}

// 处理重命名聊天
const handleRenameChat = async (id: string, newTitle: string) => {
  try {
    await sessionStore.updateSessionTitle(id, newTitle)
    ElMessage.success('对话标题已更新')
  } catch (error) {
    console.error('更新会话标题失败:', error)
    ElMessage.error('更新标题失败')
  }
}

// 处理删除聊天
const handleDeleteChat = async (id: string) => {
  try {
    isLoading.value = true
    
    // 记录删除前的当前会话ID
    const oldCurrentId = currentThreadId.value
    const wasCurrentSession = (id === oldCurrentId)
    
    // 调用删除会话
    await sessionStore.deleteSession(id)

    // 清理相关缓存和控制器
    sessionMessagesCache.value.delete(id)
    const controller = abortControllers.value.get(id)
    if (controller) controller.abort()
    activeStreams.value.delete(id)
    
    // 检查删除后的情况
    if (wasCurrentSession && !currentThreadId.value) {
      // 如果删除的是当前会话且没有会话了，创建新会话
      chatStore.clearMessages()
      await handleNewChat()
    } else if (wasCurrentSession && currentThreadId.value) {
      // 如果删除的是当前会话但切换到了其他会话，加载新会话的消息
      await chatStore.loadHistoryMessages(currentThreadId.value)
    }
    
    ElMessage.success('对话已删除')
  } catch (error) {
    console.error('删除会话失败:', error)
    ElMessage.error('删除对话失败')
  } finally {
    isLoading.value = false
  }
}

// 挂载时初始化应用
onMounted(async () => {
  await sessionStore.loadSessionList()
  if (chatHistory.value.length === 0) {
    // 首次进入无历史，自动建新会话
    await handleNewChat()
  } else {
    // 默认回显选中第一个会话
    handleSelectChat(chatHistory.value[0])
  }
})

// 组件卸载清理
onUnmounted(() => {
  handleStop()
})
</script>

<template>
  <div class="app-container">
    <!-- 左侧边栏 -->
    <ChatSidebar
      :chat-history="chatHistory"
      :sidebar-collapsed="sidebarCollapsed"
      @update:sidebarCollapsed="sidebarCollapsed = $event"
      @select-chat="handleSelectChat"
      @new-chat="handleNewChat"
      @rename-chat="handleRenameChat"
      @delete-chat="handleDeleteChat"
      @open-skill-manager="showSkillManager = true"
    />

    <!-- 右侧主工作区 -->
    <main class="main-area">
      <!-- 顶部 Header -->
      <ChatHeader
        :is-loading="isUILoading"
        :messages-length="messages.length"
        :is-dev="isDev"
        :show-debug="showDebug"
        :has-reasoning-or-tool="hasReasoningOrTool"
        @toggle-debug="toggleDebug"
        @expand-all="expandAll"
        @collapse-all="collapseAll"
      />

      <!-- 内容区域：消息列表 + 调试面板 -->
      <div class="content-wrapper">
        <!-- 消息列表 -->
        <MessageList
          ref="messageListRef"
          :messages="messages"
          :is-loading="isUILoading"
          :is-waiting-for-response="isCurrentWaiting"
          @toggle-collapse="toggleCollapse"
        />

        <!-- 调试面板 -->
        <div v-show="showDebug" class="debug-panel">
          <div class="debug-header">
            <span class="debug-title">实时事件流 ({{ debugEvents.length }})</span>
            <button @click="clearDebugEvents" class="debug-clear-btn">清空</button>
          </div>
          <div class="debug-content">
            <div v-for="evt in debugEvents" :key="evt.id" class="debug-event">
              <div class="debug-event-header">[{{ evt.timestamp }}] {{ evt.type }}</div>
              <pre class="debug-event-data">{{ JSON.stringify(evt.data, null, 2) }}</pre>
            </div>
          </div>
        </div>
      </div>

      <!-- 底部输入区 -->
      <ChatInput
        :is-loading="isUILoading"
        :current-agent="currentAgentId"
        @send="handleSend"
        @stop="handleStop"
        @switch-agent="handleSwitchAgent"
      />
    </main>

    <!-- Skill 仓库管理抽屉 -->
    <SkillManager v-model:visible="showSkillManager" />
  </div>
</template>

<style scoped>
/* CSS Variables */
:root {
  --bg-sidebar: #F0F4F9;
  --bg-main: #FFFFFF;
  --bg-input: #F0F4F9;
  --bg-message-user: #F0F4F9;
  --bg-message-ai: transparent;
  --text-primary: #1F1F1F;
  --text-secondary: #444746;
  --accent-color: #667eea;
  --border-color: #E3E3E3;
  --bg-selected: #D3E3FD;
  
  /* 气泡颜色 */
  --bubble-text-bg: #FFFFFF;
  --bubble-reasoning-bg: #F9FAFB;
  --bubble-reasoning-border: #A78BFA;
  --bubble-tool-bg: #F0F9FF;
  --bubble-tool-border: #0EA5E9;
  --bubble-user-bg: #F0F4F9;
}

/* 整体容器 */
.app-container {
  display: flex;
  height: 100vh;
  width: 100vw;
  overflow: hidden;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  background: var(--bg-main, #FFFFFF);
}

/* 右侧主工作区 */
.main-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--bg-main, #FFFFFF);
  overflow: hidden;
}

/* 内容包装器：消息列表 + 调试面板 */
.content-wrapper {
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* 调试面板 */
.debug-panel {
  width: 320px;
  background: #1e1e1e;
  color: #d4d4d4;
  display: flex;
  flex-direction: column;
  border-left: 1px solid #333;
}

.debug-header {
  padding: 12px;
  background: #2d2d2d;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.debug-title {
  font-size: 0.85rem;
  font-weight: bold;
  color: #d4d4d4;
}

.debug-clear-btn {
  background: transparent;
  border: none;
  color: #ef4444;
  cursor: pointer;
  font-size: 0.8rem;
  padding: 4px 8px;
  border-radius: 4px;
  transition: background 0.2s;
}

.debug-clear-btn:hover {
  background: rgba(239, 68, 68, 0.1);
}

.debug-content {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.debug-event {
  margin-bottom: 12px;
  font-family: 'JetBrains Mono', 'Fira Code', 'Consolas', monospace;
  font-size: 0.75rem;
  background: #252526;
  padding: 8px;
  border-radius: 4px;
}

.debug-event-header {
  color: #4EC9B0;
  margin-bottom: 4px;
}

.debug-event-data {
  margin: 0;
  white-space: pre-wrap;
  word-wrap: break-word;
  color: #9CDCFE;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .main-header {
    padding: 12px 16px;
  }
}

/* 自定义滚动条 */
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-track {
  background: transparent;
}

::-webkit-scrollbar-thumb {
  background: #D1D5DB;
  border-radius: 3px;
}

::-webkit-scrollbar-thumb:hover {
  background: #9CA3AF;
}
</style>
