<!--
  MessageBubble - 单条消息渲染组件
-->
<script setup lang="ts">
import { ref } from 'vue'
import type { Message } from '../../types/chat'
import { parseToolContent, formatJSON } from '../../types/chat'
import MarkdownRender from '../common/MarkdownRender.vue'

// Props 定义
defineProps<{
  message: Message
}>()

// Emits 定义
const emit = defineEmits<{
  'toggle-collapse': []
}>()

// 复制状态
const copiedMessageId = ref<string | null>(null)

// 格式化工具执行结果，处理双重转义字符串与伪 XML 标签
const formatToolResult = (content: string) => {
  if (!content) return ''
  let text = content
  try {
    const parsed = JSON.parse(content)
    if (typeof parsed === 'string') {
      text = parsed
    } else {
      return JSON.stringify(parsed, null, 2)
    }
  } catch {
    // 解析失败则保持原样
  }

  // 1. 强制将字面量的 \n 和 \r 替换为真实的换行符（修复后端双重转义导致 Markdown 无法换行的问题）
  text = text.replace(/\\n/g, '\n').replace(/\\r/g, '\r').replace(/\\"/g, '"')
  
  // 2. 彻底剥离类似 <输出报告格式> 这样的自定义伪标签，防止它们破坏 Markdown 渲染树或被浏览器隐藏
  text = text.replace(/<\/?输出报告格式>/g, '')

  return text.trim()
}

// 复制消息 (兼容 HTTP 与 HTTPS)
const copyMessage = async (content: string, id: string) => {
  try {
    // 判断是否支持现代剪贴板 API 且在安全上下文中
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(content)
    } else {
      // 降级方案：创建隐藏的 textarea 使用 execCommand 复制
      const textArea = document.createElement("textarea")
      textArea.value = content
      // 使其不可见
      textArea.style.position = "absolute"
      textArea.style.left = "-999999px"
      textArea.style.top = "-999999px"
      document.body.appendChild(textArea)
      textArea.focus()
      textArea.select()
      
      const successful = document.execCommand('copy')
      textArea.remove()
      
      if (!successful) {
        throw new Error('复制命令执行失败')
      }
    }

    copiedMessageId.value = id
    setTimeout(() => {
      if (copiedMessageId.value === id) {
        copiedMessageId.value = null
      }
    }, 2000)
  } catch (err) {
    console.error('复制失败:', err)
  }
}

// 切换折叠状态
const toggleCollapse = () => {
  emit('toggle-collapse')
}
</script>

<template>
  <div class="message" :class="message.role">
    <!-- AI 正文消息 -->
    <template v-if="message.role === 'assistant'">
      <div class="message-avatar ai-avatar">
        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z" fill="currentColor"/>
        </svg>
      </div>
      <div class="message-content-wrapper">
        <div class="external-actions">
          <button
            class="action-icon-btn"
            :class="{ copied: copiedMessageId === message.id }"
            @click.stop="copyMessage(message.content, message.id)"
            title="复制"
          >
            <svg v-if="copiedMessageId !== message.id" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
              <path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"/>
            </svg>
            <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
              <path d="M5 13l4 4L19 7"/>
            </svg>
          </button>
        </div>
        <div class="message-content text-bubble">
          <MarkdownRender :content="formatToolResult(message.content)" />
        </div>
      </div>
    </template>

    <!-- 用户消息 -->
    <template v-else-if="message.role === 'user'">
      <div class="message-content-wrapper">
        <div class="external-actions">
          <button
            class="action-icon-btn"
            :class="{ copied: copiedMessageId === message.id }"
            @click.stop="copyMessage(message.content, message.id)"
            title="复制"
          >
            <svg v-if="copiedMessageId !== message.id" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
              <path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"/>
            </svg>
            <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
              <path d="M5 13l4 4L19 7"/>
            </svg>
          </button>
        </div>
        <div class="message-content user-bubble">
          <div class="message-text">{{ message.content }}</div>
        </div>
      </div>
    </template>

    <!-- 思考消息 (可折叠) -->
    <template v-else-if="message.role === 'reasoning'">
      <div class="message-avatar reasoning-avatar">
        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </div>
      <div class="message-content-wrapper">
        <div class="external-actions">
          <button
            class="action-icon-btn"
            :class="{ copied: copiedMessageId === message.id }"
            @click.stop="copyMessage(message.content, message.id)"
            title="复制"
          >
            <svg v-if="copiedMessageId !== message.id" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
              <path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"/>
            </svg>
            <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
              <path d="M5 13l4 4L19 7"/>
            </svg>
          </button>
        </div>
        <div class="message-content reasoning-bubble">
          <div class="bubble-header" @click="toggleCollapse">
            <div class="header-left">
              <svg class="header-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M13 10V3L4 14h7v7l9-11h-7z"/>
              </svg>
              <span class="header-title-text">思考过程</span>
              <span v-if="message.status === 'running' && message.isCollapsed" class="running-indicator">
                <span class="dot-flashing"></span>
                <span class="running-text">正在深入思考...</span>
              </span>
              <span v-else-if="message.status === 'success' && message.isCollapsed" class="success-indicator">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
                  <path d="M5 13l4 4L19 7" />
                </svg>
              </span>
            </div>
            <svg class="collapse-icon" :class="{ collapsed: message.isCollapsed }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M19 9l-7 7-7-7" />
            </svg>
          </div>
          <div v-show="!message.isCollapsed" class="bubble-content">
            <MarkdownRender :content="formatToolResult(message.content)" />
          </div>
        </div>
      </div>
    </template>

    <!-- 工具调用消息 (可折叠) -->
    <template v-else-if="message.role === 'tool'">
      <div class="message-avatar tool-avatar">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
          <path d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
        </svg>
      </div>
      <div class="message-content-wrapper">
        <div class="external-actions">
          <button
            class="action-icon-btn"
            :class="{ copied: copiedMessageId === message.id }"
            @click.stop="copyMessage(message.content, message.id)"
            title="复制"
          >
            <svg v-if="copiedMessageId !== message.id" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
              <path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"/>
            </svg>
            <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
              <path d="M5 13l4 4L19 7"/>
            </svg>
          </button>
        </div>
        <div class="message-content tool-bubble">
          <div class="bubble-header" @click="toggleCollapse">
          <div class="header-left">
            <svg class="header-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/>
            </svg>
            <span class="header-title-text">工具: {{ message.toolName || '未知工具' }}</span>
            <span v-if="message.status === 'running' && message.isCollapsed" class="running-indicator">
              <span class="dot-flashing"></span>
              <span class="running-text">正在执行...</span>
            </span>
            <span v-else-if="message.status === 'success' && message.isCollapsed" class="success-indicator">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
                <path d="M5 13l4 4L19 7" />
              </svg>
            </span>
          </div>
          <svg class="collapse-icon" :class="{ collapsed: message.isCollapsed }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M19 9l-7 7-7-7" />
          </svg>
        </div>
        <div v-show="!message.isCollapsed" class="bubble-content">
          <div class="tool-structured-content">
            <div class="tool-section">
              <div class="tool-section-label">
                <span>⚡</span>
                技能名称
              </div>
              <div class="tool-skill-name">{{ message.toolName || '未知工具' }}</div>
            </div>
            
            <div class="tool-section" v-if="message.params">
              <div class="tool-section-label">
                <span>⚙️</span>
                参数详情
              </div>
              <pre class="tool-params-block"><code>{{ formatJSON(message.params) }}</code></pre>
            </div>
            
            <div class="tool-section" v-if="message.content">
              <div class="tool-section-label">
                <span>✅</span>
                执行结果
              </div>
              <pre class="tool-code-block"><code>{{ formatToolResult(message.content) }}</code></pre>
            </div>
          </div>
        </div>
        </div>
      </div>
    </template>

    <!-- 系统/错误消息 -->
    <template v-else-if="message.role === 'system'">
      <div class="message-content system-bubble">
        <div class="system-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <line x1="12" y1="8" x2="12" y2="12"/>
            <line x1="12" y1="16" x2="12.01" y2="16"/>
          </svg>
        </div>
        <div class="message-text">{{ message.content }}</div>
      </div>
    </template>

    <!-- 其他类型消息 -->
    <template v-else>
      <div class="message-content other-bubble">
        <div class="message-text">{{ message.content }}</div>
      </div>
    </template>
  </div>
</template>

<style scoped>
/* 消息样式基础 */
.message {
  display: flex;
  gap: 16px;
  animation: fadeIn 0.3s ease;
  max-width: 100%;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.message.assistant,
.message.reasoning,
.message.tool {
  align-items: flex-start;
}

.message.user {
  justify-content: flex-end;
}

/* 头像样式 */
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

.reasoning-avatar {
  background: linear-gradient(135deg, #A78BFA 0%, #8B5CF6 100%);
  color: white;
}

.tool-avatar {
  background: linear-gradient(135deg, #0EA5E9 0%, #0284C7 100%);
  color: white;
}

/* 正文气泡 */
.text-bubble {
  flex: 1;
  min-width: 0;
  padding: 16px 20px;
  background: var(--bubble-text-bg, #FFFFFF);
  border-radius: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  border: 1px solid var(--border-color, #E3E3E3);
  position: relative;
}

/* 用户气泡 */
.user-bubble {
  background: var(--bubble-user-bg, #F0F4F9);
  border-radius: 16px 16px 4px 16px;
  max-width: 100%; /* 修复: 移除百分比宽度防止在 auto-fit 容器中产生文本坍塌 */
  padding: 12px 16px;
}

.user-bubble .message-text {
  font-size: 0.9375rem;
  line-height: 1.6;
  color: var(--text-primary, #1F1F1F);
  white-space: pre-wrap;
  word-break: break-word;
}

/* 思考气泡 */
.reasoning-bubble {
  flex: 1;
  min-width: 0;
  background: var(--bubble-reasoning-bg, #F9FAFB);
  border-radius: 12px;
  border: 1px solid #E5E7EB;
  border-left: 4px solid var(--bubble-reasoning-border, #A78BFA);
  overflow: hidden;
  position: relative;
}

/* 工具气泡 */
.tool-bubble {
  flex: 1;
  position: relative;
  min-width: 0;
  background: var(--bubble-tool-bg, #F0F9FF);
  border-radius: 12px;
  border: 1px solid #E0F2FE;
  border-left: 4px solid var(--bubble-tool-border, #0EA5E9);
  overflow: hidden;
}

/* 复制按钮 */
.copy-btn {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 28px;
  height: 28px;
  border: none;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary, #444746);
  opacity: 0;
  transition: all 0.2s;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  z-index: 10;
}

.text-bubble:hover .copy-btn,
.reasoning-bubble:hover .copy-btn,
.tool-bubble:hover .copy-btn {
  opacity: 1;
}

.copy-btn:hover {
  background: var(--bg-sidebar, #F0F4F9);
  color: var(--text-primary, #1F1F1F);
  transform: scale(1.05);
}

.copy-btn.copied {
  opacity: 1;
  background: #DCFCE7;
  color: #16A34A;
}

.copy-btn svg {
  width: 14px;
  height: 14px;
}

/* 气泡头部 */
.bubble-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  cursor: pointer;
  user-select: none;
  transition: background 0.2s;
}

.bubble-header:hover {
  background: rgba(0, 0, 0, 0.02);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-icon {
  width: 16px;
  height: 16px;
  color: var(--text-secondary, #444746);
}

.header-title-text {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--text-secondary, #444746);
}

.collapse-icon {
  width: 16px;
  height: 16px;
  color: var(--text-secondary, #444746);
  transition: transform 0.2s;
}

.collapse-icon.collapsed {
  transform: rotate(-90deg);
}

/* 动态 Loading 状态 */
.running-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: 8px;
}

.dot-flashing {
  position: relative;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background-color: #A78BFA;
  color: #A78BFA;
  animation: dot-flashing 1s infinite linear alternate;
  animation-delay: 0.5s;
}

.dot-flashing::before,
.dot-flashing::after {
  content: "";
  display: inline-block;
  position: absolute;
  top: 0;
}

.dot-flashing::before {
  left: -10px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background-color: #A78BFA;
  color: #A78BFA;
  animation: dot-flashing 1s infinite alternate;
  animation-delay: 0s;
}

.dot-flashing::after {
  left: 10px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background-color: #A78BFA;
  color: #A78BFA;
  animation: dot-flashing 1s infinite alternate;
  animation-delay: 1s;
}

@keyframes dot-flashing {
  0% {
    background-color: #A78BFA;
  }
  50%, 100% {
    background-color: #E9D5FF;
  }
}

.running-text {
  margin-left: 12px;
  font-size: 0.8rem;
  color: #8B5CF6;
  font-style: italic;
}

.success-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  background: #22C55E;
  border-radius: 50%;
  margin-left: 8px;
}

.success-indicator svg {
  width: 12px;
  height: 12px;
  color: white;
}

.bubble-content {
  padding: 0 16px 16px;
  font-size: 0.875rem;
  color: var(--text-secondary, #444746);
}

/* 工具气泡特殊样式 */
.tool-bubble .bubble-header:hover {
  background: rgba(14, 165, 233, 0.05);
}

.tool-bubble .header-icon {
  color: #0EA5E9;
}

.tool-bubble .header-title-text {
  color: #0369A1;
}

.tool-bubble .dot-flashing,
.tool-bubble .dot-flashing::before,
.tool-bubble .dot-flashing::after {
  background-color: #0EA5E9;
  color: #0EA5E9;
}

.tool-bubble .running-text {
  color: #0284C7;
}

.tool-result-label {
  font-size: 0.75rem;
  font-weight: 600;
  color: #0369A1;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 8px;
}

.tool-code-block {
  background: #0C4A6E;
  color: #E0F2FE;
  padding: 12px;
  border-radius: 8px;
  font-family: 'JetBrains Mono', 'Fira Code', 'Consolas', monospace;
  font-size: 0.8125rem;
  line-height: 1.5;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 300px;
  overflow-y: auto;
}

.tool-code-block code {
  font-family: inherit;
}

/* 结构化工具内容样式 */
.tool-structured-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.tool-section {
  background: rgba(14, 165, 233, 0.08);
  border-radius: 8px;
  padding: 12px;
  border: 1px solid rgba(14, 165, 233, 0.15);
}

.tool-section-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.75rem;
  font-weight: 600;
  color: #0284C7;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 8px;
}

.tool-section-label svg {
  width: 14px;
  height: 14px;
}

.tool-skill-name {
  font-family: 'JetBrains Mono', 'Fira Code', 'Consolas', monospace;
  font-size: 0.9375rem;
  font-weight: 600;
  color: #0C4A6E;
  background: rgba(255, 255, 255, 0.6);
  padding: 8px 12px;
  border-radius: 6px;
  border: 1px solid rgba(14, 165, 233, 0.2);
}

.tool-params-block {
  background: #0C4A6E;
  color: #E0F2FE;
  padding: 12px;
  border-radius: 6px;
  font-family: 'JetBrains Mono', 'Fira Code', 'Consolas', monospace;
  font-size: 0.8125rem;
  line-height: 1.5;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 250px;
  overflow-y: auto;
  margin: 0;
}

.tool-description {
  font-size: 0.875rem;
  color: #4B5563;
  line-height: 1.5;
  padding: 8px 0;
  border-top: 1px dashed #E5E7EB;
  margin-top: 4px;
}

/* 系统消息气泡 */
.system-bubble {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #FEF3C7;
  border-radius: 12px;
  max-width: 90%;
  margin: 0 auto;
  padding: 12px 16px;
}

.system-icon {
  width: 24px;
  height: 24px;
  color: #D97706;
  flex-shrink: 0;
}

.system-icon svg {
  width: 24px !important;
  height: 24px !important;
  flex-shrink: 0;
}

.system-bubble .message-text {
  color: #92400E;
  font-size: 0.875rem;
}

/* 其他消息 */
.other-bubble {
  background: #F9FAFB;
  border-radius: 12px;
  max-width: 90%;
  margin: 0 auto;
  padding: 12px 16px;
}

.other-bubble .message-text {
  color: var(--text-secondary, #444746);
  font-size: 0.875rem;
}

/* --- 新增：外部复制按钮样式 --- */
.message-content-wrapper {
  position: relative;
  flex: 1;
  min-width: 0;
  max-width: 85%;
}
.message.user .message-content-wrapper { flex: 0 1 auto; }

.external-actions {
  position: absolute;
  top: 0;
  opacity: 0;
  transition: opacity 0.2s;
  z-index: 10;
}
/* 用户消息靠右，操作按钮放在其左下角 */
.message.user .external-actions { 
  left: -36px; 
} 

/* AI 系列消息靠左，操作按钮放在其右下角，避免遮挡左侧头像 */
.message.assistant .external-actions,
.message.reasoning .external-actions,
.message.tool .external-actions { 
  right: -36px; 
  left: auto; /* 重置可能存在的 left 属性 */
}

.message-content-wrapper:hover .external-actions { opacity: 1; }

.action-icon-btn {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: 1px solid var(--border-color);
  background: var(--bg-main);
  color: var(--text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}
.action-icon-btn:hover { background: var(--bg-sidebar); color: var(--accent-color); }
.action-icon-btn svg { width: 14px; height: 14px; }
</style>
