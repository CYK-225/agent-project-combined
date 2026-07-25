<!--
  ChatSidebar - 左侧边栏组件
-->
<script setup lang="ts">
import type { ChatHistory } from '../../types/chat'
import { ref, nextTick, h } from 'vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import { sessionApi } from '../../api/session'

// Props 定义
const props = defineProps<{
  chatHistory: ChatHistory[]
  sidebarCollapsed: boolean
}>()

// Emits 定义
const emit = defineEmits<{
  'update:sidebarCollapsed': [value: boolean]
  'select-chat': [chat: ChatHistory]
  'new-chat': []
  'rename-chat': [id: string, newTitle: string]
  'delete-chat': [id: string]
  'open-skill-manager': []
}>()

// 新增状态：用于编辑标题
const editingChatId = ref<string | null>(null)
const editingTitle = ref('')
// 新增状态：用于跟踪下拉菜单的显示状态
const dropdownVisible = ref<{[key: string]: boolean}>({})

// 切换侧边栏折叠状态
const toggleSidebar = () => {
  emit('update:sidebarCollapsed', !props.sidebarCollapsed)
}

// 选择聊天
const selectChat = (chat: ChatHistory) => {
  // 如果当前正在编辑某个聊天的标题，先保存或取消编辑
  if (editingChatId.value) {
    finishEditing()
  }
  emit('select-chat', chat)
}

// 新建聊天
const newChat = () => {
  emit('new-chat')
}

// 开始编辑标题
const startEditing = async (chat: ChatHistory) => {
  editingChatId.value = chat.id
  editingTitle.value = chat.title
  // 关闭所有下拉菜单
  dropdownVisible.value = {}
  // 等待DOM更新后聚焦到输入框
  await nextTick()
  const inputEl = document.getElementById(`edit-input-${chat.id}`)
  if (inputEl) {
    (inputEl as HTMLInputElement).focus()
  }
}

// 完成编辑
const finishEditing = () => {
  if (editingChatId.value && editingTitle.value.trim()) {
    // 获取原始标题进行比较
    const originalTitle = props.chatHistory.find(c => c.id === editingChatId.value)?.title || ''
    // 只有在标题发生变化时才触发重命名事件
    if (editingTitle.value.trim() !== originalTitle) {
      emit('rename-chat', editingChatId.value, editingTitle.value.trim())
    }
  }
  editingChatId.value = null
  editingTitle.value = ''
}

// 取消编辑
const cancelEditing = () => {
  editingChatId.value = null
  editingTitle.value = ''
}

// 处理输入框按键事件
const handleKeydown = (e: KeyboardEvent, chatId: string) => {
  if (e.key === 'Enter') {
    finishEditing()
  } else if (e.key === 'Escape') {
    cancelEditing()
  }
}

// 标记会话（优化为单列居中布局）
const markSession = async (chatId: string) => {
  // 点击后关闭当前下拉菜单
  dropdownVisible.value[chatId] = false;
  
  try {
    const errorMsgs = await sessionApi.getErrorMsg(chatId);
    
    if (!errorMsgs || errorMsgs.length === 0) {
      ElMessage.info('当前会话暂无报错数据');
      return;
    }

    // 将字符串数组拼接为长文本，仅用于一键复制功能，不直接用于展示
    const errorText = errorMsgs.join('\n\n');

    // 优化布局：单列居中卡片布局
    ElMessageBox({
      title: '会话报错信息',
      customStyle: { maxWidth: '500px', width: '90%' }, // 限制弹窗宽度
      message: h('div', null, [
        // 顶部提示文案居中
        h('p', {
          style: 'color: #f56c6c; font-weight: bold; margin-bottom: 16px; font-size: 15px; text-align: center;'
        }, '⚠️ 请将报错完整复制给管理员。'),
        
        // 滚动容器：使用 Flex 垂直单列、居中对齐
        h('div', {
          style: 'max-height: 300px; overflow-y: auto; background: #f5f7fa; padding: 16px; border-radius: 6px; display: flex; flex-direction: column; align-items: center; gap: 10px;'
        },
        // 遍历生成居中的报错卡片
        errorMsgs.map(msg =>
          h('div', {
            style: 'width: 100%; background: #ffffff; padding: 12px 16px; border-radius: 4px; border: 1px solid #ebeef5; text-align: center; font-family: monospace; font-size: 14px; color: #606266; word-break: break-all; box-shadow: 0 2px 6px rgba(0,0,0,0.02); box-sizing: border-box;'
          }, msg)
        ))
      ]),
      showCancelButton: true,
      confirmButtonText: '一键复制',
      cancelButtonText: '关闭',
      // 拦截关闭事件，处理复制逻辑
      beforeClose: (action, instance, done) => {
        if (action === 'confirm') {
          const doCopy = async () => {
            try {
              if (navigator.clipboard && window.isSecureContext) {
                await navigator.clipboard.writeText(errorText)
              } else {
                const textArea = document.createElement("textarea")
                textArea.value = errorText
                textArea.style.position = "absolute"
                textArea.style.left = "-999999px"
                document.body.appendChild(textArea)
                textArea.focus()
                textArea.select()
                const successful = document.execCommand('copy')
                textArea.remove()
                if (!successful) throw new Error('复制失败')
              }
              ElMessage.success('复制成功！请发送给管理员');
              done();
            } catch (err) {
              console.error('复制失败:', err)
              ElMessage.error('复制失败，请手动全选文本复制');
            }
          }
          doCopy()
        } else {
          done();
        }
      }
    });
  } catch (error) {
    console.error('获取报错数据失败:', error);
    ElMessage.error('获取报错数据失败，请重试');
  }
}

// 删除聊天
const deleteChat = async (chatId: string) => {
  try {
    await ElMessageBox.confirm(
      '确定要删除此对话记录吗？',
      '删除对话',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
    
    emit('delete-chat', chatId)
    ElMessage.success('对话已删除')
  } catch (error) {
    // 用户取消删除操作
    console.log('用户取消了删除操作')
  }
}

// 切换下拉菜单可见性
const toggleDropdown = (chatId: string) => {
  // 如果当前正在编辑标题，不允许打开下拉菜单
  if (editingChatId.value) return
  
  // 如果当前会话正在生成标题，也不允许打开下拉菜单
  const chat = props.chatHistory.find(c => c.id === chatId);
  if (chat?.isGeneratingTitle) return
  
  // 关闭其他所有下拉菜单
  Object.keys(dropdownVisible.value).forEach(key => {
    if (key !== chatId) {
      dropdownVisible.value[key] = false
    }
  })
  
  // 切换当前菜单
  dropdownVisible.value[chatId] = !dropdownVisible.value[chatId]
}

// 点击页面其他地方时关闭下拉菜单
const handleClickOutside = (event: Event) => {
  const target = event.target as HTMLElement
  if (!target.closest('.more-options-btn') && !target.closest('.dropdown-menu')) {
    Object.keys(dropdownVisible.value).forEach(key => {
      dropdownVisible.value[key] = false
    })
  }
}

// 监听点击事件以关闭下拉菜单
document.addEventListener('click', handleClickOutside)
</script>

<template>
  <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
    <!-- 顶部 Logo 区域 -->
    <div class="sidebar-header">
      <div class="logo">
        <svg class="logo-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z" fill="currentColor"/>
        </svg>
        <span class="logo-text">AgentScope</span>
      </div>
    </div>

    <!-- 新建对话按钮 -->
    <div class="sidebar-actions">
      <button class="new-chat-btn" :class="{ 'collapsed-btn': sidebarCollapsed }" @click="newChat" :title="sidebarCollapsed ? '新建对话' : ''">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 4v16m8-8H4" />
        </svg>
        <span v-if="!sidebarCollapsed">发起新对话</span>
      </button>
    </div>

    <!-- 历史对话列表 -->
    <div class="history-section" v-if="!sidebarCollapsed">
      <div class="section-title">最近对话</div>
      <div class="history-list">
        <div
          v-for="chat in chatHistory"
          :key="chat.id"
          class="history-item"
          :class="{ active: chat.isActive }"
        >
          <svg class="history-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8-4.03 8-9 8z" />
          </svg>
          <div class="history-content" @click="selectChat(chat)">
            <div v-if="editingChatId === chat.id" class="history-title-edit">
              <input
                :id="`edit-input-${chat.id}`"
                v-model="editingTitle"
                type="text"
                class="edit-input"
                @keydown="handleKeydown($event, chat.id)"
                @blur="finishEditing"
              />
            </div>
            <div v-else class="history-title" :class="{ 'generating': chat.isGeneratingTitle }" @dblclick="startEditing(chat)">{{ chat.title }}</div>
            <div class="history-time">{{ chat.agentName || chat.timestamp }}</div>
          </div>
          
          <!-- 更多操作按钮，默认隐藏，点击时显示下拉菜单 -->
          <div class="more-options-btn" @click.stop="toggleDropdown(chat.id)">
            <svg class="more-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <circle cx="6" cy="12" r="1.5" fill="currentColor"/>
              <circle cx="12" cy="12" r="1.5" fill="currentColor"/>
              <circle cx="18" cy="12" r="1.5" fill="currentColor"/>
            </svg>
            <div class="dropdown-menu" v-if="dropdownVisible[chat.id]">
              <div class="dropdown-item" @click.stop="startEditing(chat)">重命名</div>
              <div class="dropdown-item delete-item" @click.stop="deleteChat(chat.id)">删除</div>
              <div class="dropdown-item" @click.stop="markSession(chat.id)">标记此会话</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部设置与折叠按钮 -->
    <div class="sidebar-footer">
      <button class="footer-btn" title="Skill 管理" @click="emit('open-skill-manager')">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M4 6h16M4 12h16M4 18h16" />
        </svg>
        <span class="btn-text">Skill 管理</span>
      </button>
      <button class="footer-btn" title="设置">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066 2.573c-.94 1.543.826 3.31 2.37 2.37.996.608 2.296.07 2.572-1.065z" />
          <path d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
        </svg>
        <span class="btn-text">设置</span>
      </button>
      <button class="footer-btn" title="帮助">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M8.228 9c.549-1.165 2.03-2 3.772-2 2.21 0 4 1.343 4 3 0 1.4-1.278 2.575-3.006 2.907-.542.104-.994.54-.994 1.093m0 3h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
        <span class="btn-text">帮助</span>
      </button>
      
      <!-- 折叠/展开按钮 -->
      <button class="collapse-toggle-btn" @click="toggleSidebar" :title="sidebarCollapsed ? '展开' : '收起'">
        <svg v-if="!sidebarCollapsed" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M11 17l-5-5 5-5M18 17l-5-5 5-5" />
        </svg>
        <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M13 17l5-5-5-5M6 17l5-5-5-5" />
        </svg>
        <span class="btn-text">收起</span>
      </button>
    </div>
  </aside>
</template>

<style scoped>
/* 左侧边栏 */
.sidebar {
  width: 280px;
  min-width: 280px;
  background: var(--bg-sidebar, #F0F4F9);
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--border-color, #E3E3E3);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  height: 100vh;
}

.sidebar.collapsed {
  width: 64px;
  min-width: 64px;
}

/* 顶部 Logo 区域 */
.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px 12px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo-icon {
  width: 32px;
  height: 32px;
  color: var(--accent-color, #667eea);
  flex-shrink: 0;
}

.logo-text {
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--text-primary, #1F1F1F);
  white-space: nowrap;
  transition: opacity 0.2s;
}

.sidebar.collapsed .logo-text {
  display: none;
}

/* 新建对话按钮 */
.sidebar-actions {
  padding: 16px 12px;
}

.new-chat-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px 16px;
  border: 1px solid var(--border-color, #E3E3E3);
  border-radius: 24px;
  background: var(--bg-main, #FFFFFF);
  color: var(--text-primary, #1F1F1F);
  font-size: 0.9375rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.new-chat-btn:hover {
  background: var(--bg-selected, #D3E3FD);
  border-color: var(--accent-color, #667eea);
}

.new-chat-btn svg {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
}

/* 折叠状态下的圆形按钮 */
.new-chat-btn.collapsed-btn {
  width: 40px;
  height: 40px;
  padding: 0;
  border-radius: 50%;
  margin: 0 auto;
}

.new-chat-btn.collapsed-btn span {
  display: none;
}

.new-chat-btn.collapsed-btn svg {
  width: 20px;
  height: 20px;
}

/* 历史对话列表 */
.history-section {
  flex: 1;
  overflow-y: auto;
  padding: 0 12px;
}

.section-title {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--text-secondary, #444746);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  padding: 8px 12px;
  margin-top: 8px;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.history-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s;
  color: var(--text-secondary, #444746);
  position: relative;
}

.history-item:hover {
  background: rgba(0, 0, 0, 0.03);
}

.history-item:hover .more-options-btn {
  opacity: 1;
}

.history-item.active {
  background: var(--bg-selected, #D3E3FD);
  color: var(--text-primary, #1F1F1F);
}

.history-icon {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
}

.history-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.history-title {
  font-size: 0.875rem;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  cursor: pointer;
}

.history-title.generating {
  color: var(--accent-color, #667eea);
  animation: title-pulse 1.5s infinite ease-in-out;
  pointer-events: none; /* 生成中禁止双击编辑 */
}

@keyframes title-pulse {
  0% { opacity: 0.5; }
  50% { opacity: 1; }
  100% { opacity: 0.5; }
}

.history-title-edit {
  width: 100%;
}

.edit-input {
  width: 100%;
  padding: 4px 8px;
  border: 1px solid var(--border-color, #E3E3E3);
  border-radius: 4px;
  background: var(--bg-main, #FFFFFF);
  color: var(--text-primary, #1F1F1F);
  font-size: 0.875rem;
  font-weight: 500;
  outline: none;
}

.edit-input:focus {
  border-color: var(--accent-color, #667eea);
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.2);
}

.history-time {
  font-size: 0.75rem;
  opacity: 0.7;
  margin-top: 2px;
}

/* 更多操作按钮 */
.more-options-btn {
  position: relative;
  opacity: 0;
  transition: opacity 0.2s;
  padding: 4px;
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.history-item:hover .more-options-btn {
  opacity: 1;
}

.more-options-btn:hover {
  background: rgba(0, 0, 0, 0.1);
}

.more-icon {
  width: 18px;
  height: 18px;
  color: var(--text-secondary, #444746);
}

/* 下拉菜单 */
.dropdown-menu {
  position: absolute;
  right: 0;
  top: 100%;
  margin-top: 4px;
  background: var(--bg-main, #FFFFFF);
  border: 1px solid var(--border-color, #E3E3E3);
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  z-index: 100;
  min-width: 100px;
  display: block; /* 改为block，由v-if控制显示 */
}

.dropdown-item {
  padding: 8px 12px;
  font-size: 0.8rem;
  cursor: pointer;
  transition: background 0.2s;
  white-space: nowrap;
}

.dropdown-item:hover {
  background: var(--bg-selected, #D3E3FD);
}

.delete-item {
  color: #e74c3c;
}

.delete-item:hover {
  background: #fdf2f2;
  color: #c0392b;
}

/* 侧边栏底部 */
.sidebar-footer {
  padding: 12px;
  border-top: 1px solid rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: auto;
}

.sidebar.collapsed .sidebar-footer {
  padding: 12px 0;
  align-items: center;
}

.footer-btn {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: none;
  background: transparent;
  border-radius: 10px;
  cursor: pointer;
  color: var(--text-secondary, #444746);
  font-size: 0.875rem;
  transition: all 0.2s;
}

.footer-btn:hover {
  background: rgba(0, 0, 0, 0.05);
}

.footer-btn svg {
  width: 22px;
  height: 22px;
  flex-shrink: 0;
}

/* 折叠/展开按钮 */
.collapse-toggle-btn {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: none;
  background: transparent;
  border-radius: 10px;
  cursor: pointer;
  color: var(--text-secondary, #444746);
  font-size: 0.875rem;
  transition: all 0.2s;
}

.collapse-toggle-btn:hover {
  background: rgba(0, 0, 0, 0.05);
  color: var(--text-primary, #1F1F1F);
}

.collapse-toggle-btn svg {
  width: 22px;
  height: 22px;
  flex-shrink: 0;
}

/* 折叠状态下隐藏文字 */
.sidebar.collapsed .footer-btn .btn-text,
.sidebar.collapsed .collapse-toggle-btn .btn-text {
  display: none;
}

/* 折叠状态下按钮样式 */
.sidebar.collapsed .footer-btn,
.sidebar.collapsed .collapse-toggle-btn {
  justify-content: center;
  padding: 10px;
  width: 40px;
  height: 40px;
}

.sidebar.collapsed .footer-btn svg,
.sidebar.collapsed .collapse-toggle-btn svg {
  width: 20px;
  height: 20px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .sidebar {
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    z-index: 100;
    transform: translateX(0);
    box-shadow: 2px 0 8px rgba(0, 0, 0, 0.1);
  }

  .sidebar.collapsed {
    transform: translateX(-100%);
    width: 280px;
    min-width: 280px;
  }
}
</style>
