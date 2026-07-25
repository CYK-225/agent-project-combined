<!--
  ChatHeader - 顶部操作栏组件
-->
<script setup lang="ts">
// Props 定义
defineProps<{
  isLoading: boolean
  messagesLength: number
  isDev: boolean
  showDebug: boolean
  hasReasoningOrTool: boolean
}>()

// Emits 定义
const emit = defineEmits<{
  'toggle-debug': []
  'expand-all': []
  'collapse-all': []
}>()
</script>

<template>
  <header class="main-header">
    <div class="header-title">
      <h1>AgentScope 智能助手</h1>
    </div>
    <div class="header-actions">
      <!-- 调试按钮 (仅开发环境显示) -->
      <button 
        v-if="isDev" 
        class="debug-toggle-btn" 
        :class="{ active: showDebug }"
        @click="emit('toggle-debug')"
        title="调试面板"
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4" />
        </svg>
        <span>调试</span>
      </button>
      
      <!-- 展开/折叠所有思考链 -->
      <div class="expand-controls" v-if="hasReasoningOrTool">
        <button @click="emit('expand-all')" title="展开全部">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M19 13l-7 7-7-7m14-8l-7 7-7-7" />
          </svg>
        </button>
        <button @click="emit('collapse-all')" title="折叠全部">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M5 11l7-7 7 7M5 19l7-7 7 7" />
          </svg>
        </button>
      </div>
      <span class="connection-status" :class="{ connected: !isLoading || messagesLength > 0 }">
        <span class="status-dot"></span>
        {{ !isLoading || messagesLength > 0 ? 'Connected' : 'Connecting...' }}
      </span>
      <button class="user-avatar">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
        </svg>
      </button>
    </div>
  </header>
</template>

<style scoped>
/* 顶部 Header */
.main-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  background: var(--bg-main, #FFFFFF);
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
}

.header-title h1 {
  font-size: 1.125rem;
  font-weight: 500;
  color: var(--text-primary, #1F1F1F);
  margin: 0;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

/* 调试按钮 */
.debug-toggle-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border: 1px solid var(--border-color, #E3E3E3);
  border-radius: 8px;
  background: var(--bg-main, #FFFFFF);
  color: var(--text-secondary, #444746);
  font-size: 0.875rem;
  cursor: pointer;
  transition: all 0.2s;
}

.debug-toggle-btn:hover {
  background: var(--bg-sidebar, #F0F4F9);
}

.debug-toggle-btn.active {
  background: var(--accent-color, #667eea);
  color: white;
  border-color: var(--accent-color, #667eea);
}

.debug-toggle-btn svg {
  width: 16px;
  height: 16px;
}

/* 展开/折叠控制 */
.expand-controls {
  display: flex;
  gap: 4px;
}

.expand-controls button {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary, #444746);
  transition: all 0.2s;
}

.expand-controls button:hover {
  background: rgba(0, 0, 0, 0.05);
  color: var(--text-primary, #1F1F1F);
}

.expand-controls button svg {
  width: 16px;
  height: 16px;
}

.connection-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.875rem;
  color: var(--text-secondary, #444746);
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #f59e0b;
  transition: background 0.3s;
}

.connection-status.connected .status-dot {
  background: #22c55e;
}

.user-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  background: var(--bg-input, #F0F4F9);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary, #444746);
  transition: all 0.2s;
}

.user-avatar:hover {
  background: var(--bg-selected, #D3E3FD);
}

.user-avatar svg {
  width: 20px;
  height: 20px;
}

/* 响应式 */
@media (max-width: 768px) {
  .main-header {
    padding: 12px 16px;
  }
}
</style>
