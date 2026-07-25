<!--
  MessageList - 消息列表容器组件
-->
<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import type { Message } from '../../types/chat'
import MessageBubble from './MessageBubble.vue'

// Props 定义
const props = defineProps<{
  messages: Message[]
  isLoading: boolean
  isWaitingForResponse: boolean
}>()

// 滚动状态
const isUserScrollingUp = ref(false)
const messageContainer = ref<HTMLElement | null>(null)

// 智能滚动到底部
const scrollToBottom = () => {
  if (messageContainer.value && !isUserScrollingUp.value) {
    messageContainer.value.scrollTop = messageContainer.value.scrollHeight
  }
}

// 强制滚动到底部
const forceScrollToBottom = () => {
  if (messageContainer.value) {
    messageContainer.value.scrollTop = messageContainer.value.scrollHeight
    isUserScrollingUp.value = false
  }
}

// 滚动事件处理
const handleScroll = () => {
  if (!messageContainer.value) return
  
  const { scrollTop, scrollHeight, clientHeight } = messageContainer.value
  const distanceFromBottom = scrollHeight - scrollTop - clientHeight
  
  isUserScrollingUp.value = distanceFromBottom > 50
  
  if (distanceFromBottom <= 20) {
    isUserScrollingUp.value = false
  }
}

// 监听消息变化自动滚动
watch(() => props.messages, () => {
  nextTick(() => {
    scrollToBottom()
  })
}, { deep: true })

// 暴露方法给父组件
defineExpose({
  scrollToBottom,
  forceScrollToBottom
})
</script>

<template>
  <div class="message-list" ref="messageContainer" @scroll="handleScroll">
    <div class="message-list-content">
      <!-- 空状态 -->
      <div v-if="messages.length === 0" class="empty-state">
        <div class="empty-icon">
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm-1-13h2v6h-2zm0 8h2v2h-2z" fill="currentColor"/>
          </svg>
        </div>
        <h2>有什么可以帮您的？</h2>
        <p>我可以协助您编写代码、解答问题或进行创意构思。</p>
      </div>

      <!-- 消息列表 -->
      <template v-else>
        <MessageBubble
          v-for="message in messages"
          :key="message.id"
          :message="message"
          @toggle-collapse="message.isCollapsed = !message.isCollapsed"
        />

        <!-- 等待响应占位 Loading -->
        <div v-if="isWaitingForResponse" class="message assistant">
          <div class="message-avatar ai-avatar">
            <span class="dot-flashing" style="margin: auto;"></span>
          </div>
          <div class="message-content text-bubble" style="padding: 12px 16px; min-height: 44px; display: flex; align-items: center;">
            <span class="typing-indicator"><span></span><span></span><span></span></span>
          </div>
        </div>

        <!-- 加载指示器 -->
        <div v-if="isLoading && messages.filter(m => m.role === 'assistant').length === 0 && !isWaitingForResponse && !messages.some(m => (m.role === 'reasoning' || m.role === 'tool') && m.status === 'running')" class="message assistant loading">
          <div class="message-avatar ai-avatar">
            <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z" fill="currentColor"/>
            </svg>
          </div>
          <div class="message-content">
            <div class="typing-indicator">
              <span></span>
              <span></span>
              <span></span>
            </div>
          </div>
        </div>
      </template>
    </div>

    <!-- 回到底部按钮 -->
    <button
      v-if="isUserScrollingUp"
      class="scroll-to-bottom-btn"
      @click="forceScrollToBottom"
      title="回到底部"
    >
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M19 14l-7 7-7-7" />
      </svg>
      <span v-if="isLoading" class="new-message-indicator"></span>
    </button>
  </div>
</template>

<style scoped>
/* 消息列表 */
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

.message-list-content {
  max-width: 800px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  text-align: center;
  animation: fadeIn 0.5s ease;
}

/* 强制约束所有图标尺寸，防止 Flex 撑爆屏幕 */
.empty-icon {
  width: 64px;
  height: 64px;
  flex-shrink: 0;
  margin: 0 auto 24px auto; /* 确保居中 */
  color: var(--accent-color, #667eea);
  opacity: 0.8;
}

.empty-icon svg {
  width: 100%;
  height: 100%;
  display: block;
}

.empty-state h2 {
  font-size: 1.5rem;
  font-weight: 500;
  color: var(--text-primary, #1F1F1F);
  margin: 0 0 8px 0;
}

.empty-state p{
  font-size: 0.9375rem;
  color: var(--text-secondary, #444746);
  margin: 0;
}

/* 补全由于组件拆分遗漏的占位头像约束 */
.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.message-avatar svg {
  width: 24px !important;
  height: 24px !important;
  flex-shrink: 0;
}
.ai-avatar {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

/* 加载动画 */
.typing-indicator{
  display: flex;
  gap: 4px;
  padding: 8px 0;
}

.typing-indicator span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--text-secondary, #444746);
  opacity: 0.4;
  animation: typing 1.4s infinite ease-in-out;
}

.typing-indicator span:nth-child(1) {
  animation-delay: 0s;
}

.typing-indicator span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-indicator span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing {
  0%, 60%, 100% {
    transform: translateY(0);
    opacity: 0.4;
  }
  30% {
    transform: translateY(-8px);
    opacity: 1;
  }
}

/* 回到底部按钮 */
.scroll-to-bottom-btn {
  position: sticky;
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
  width: 44px;
  height: 44px;
  border: none;
  border-radius: 50%;
  background: var(--bg-main, #FFFFFF);
  color: var(--text-secondary, #444746);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  transition: all 0.2s;
  z-index: 50;
  margin-top: -60px;
}

.scroll-to-bottom-btn:hover {
  transform: translateX(-50%) translateY(-2px);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.2);
  color: var(--accent-color, #667eea);
}

.scroll-to-bottom-btn svg {
  width: 20px;
  height: 20px;
}

.new-message-indicator {
  position: absolute;
  top: -2px;
  right: -2px;
  width: 10px;
  height: 10px;
  background: #EF4444;
  border-radius: 50%;
  border: 2px solid var(--bg-main, #FFFFFF);
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% {
    transform: scale(1);
    opacity: 1;
  }
  50% {
    transform: scale(1.2);
    opacity: 0.8;
  }
}

/* 响应式 */
@media (max-width: 768px) {
  .message-list {
    padding: 16px;
  }
}
</style>
