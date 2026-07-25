import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Message } from '../../types/chat'
import { convertChatMessageVOToMessages } from '../../types/chat'
import { sessionApi } from '../../api/session'
import { StoreLogger } from '../../utils/storeLogger'
import { useSessionStore } from './sessionStore'



export const useChatStore = defineStore('chat', () => {
  // ==================== 状态 ====================
  /** 当前会话的消息列表 */
  const messages = ref<Message[]>([])
  /** 历史消息加载状态 */
  const isMessageLoading = ref(false)
  /** 是否正在等待 AI 响应（首 token 到达前） */
  const isWaitingForResponse = ref(false)

  // ==================== 动作 ====================

  /**
   * 追加单条消息到消息列表末尾
   * @param message - 要追加的消息对象
   */
  const appendMessage = (message: Message) => {
    StoreLogger.logAction({
      storeName: 'chat',
      action: 'appendMessage',
      payload: { message }
    });

    messages.value.push(message)

    StoreLogger.logStateChange({
      storeName: 'chat',
      action: 'appendMessageSuccess',
      state: { messages: messages.value },
      result: { messageCount: messages.value.length }
    });
  }

  /**
   * 清空当前消息列表
   */
  const clearMessages = () => {
    StoreLogger.logAction({
      storeName: 'chat',
      action: 'clearMessages',
      payload: { messageCount: messages.value.length }
    });

    messages.value = []

    StoreLogger.logStateChange({
      storeName: 'chat',
      action: 'clearMessagesSuccess',
      state: { messages: messages.value },
      result: { messageCount: 0 }
    });
  }

  /**
   * 从后端分页加载指定会话的历史消息，并回写到 messages
   * 内部调用 convertChatMessageVOToMessages 进行数据转换
   * @param sessionId - 会话 ID
   */
  const loadHistoryMessages = async (sessionId: string) => {
    StoreLogger.logAction({
      storeName: 'chat',
      action: 'loadHistoryMessages',
      payload: { sessionId }
    });

    try {
      isMessageLoading.value = true
      const pageSize = 50
      let currentPage = 1
      let allMessages: Message[] = []

      // 循环加载所有页（后端返回 totalPage 时按页加载，否则只加载第一页）
      while (true) {
        const page = await sessionApi.getHistoryMsg({
          sessionId,
          pageNumber: currentPage,
          size: pageSize
        })

        const records = page.records || []
        if (records.length === 0) break

        // 将每条 ChatMessageVO 转换为前端 Message（可能一对多）
        for (const vo of records) {
          const converted = convertChatMessageVOToMessages(vo)
          allMessages.push(...converted)
        }

        // 判断是否还有下一页
        const totalPages = page.totalPage ?? -1
        if (totalPages < 0 || currentPage >= totalPages) break
        currentPage++
      }

      // 按后端返回顺序赋值（历史消息从旧到新）
      messages.value = allMessages

      //提取并同步当前会话的 Agent 模式
      const firstAgentMsg = allMessages.find(m => m.role === 'assistant' && m.agentId)
      console.log("第二次获取agentid：",firstAgentMsg)

      if (firstAgentMsg && firstAgentMsg.agentId) {
        const sessionStore = useSessionStore()
        sessionStore.updateSessionAgentMode(sessionId, firstAgentMsg.agentId)
      }

      StoreLogger.logStateChange({
        storeName: 'chat',
        action: 'loadHistoryMessagesSuccess',
        state: {
          messages: messages.value,
          isMessageLoading: isMessageLoading.value
        },
        result: { messageCount: allMessages.length, sessionId }
      });
    } catch (error) {
      StoreLogger.logAction({
        storeName: 'chat',
        action: 'loadHistoryMessagesError',
        error: error
      });
      console.error('加载历史消息失败:', error)
      // 加载失败时清空，避免残留旧数据
      messages.value = []
    } finally {
      isMessageLoading.value = false
    }
  }

  return {
    // 状态
    messages,
    isMessageLoading,
    isWaitingForResponse,
    // 动作
    appendMessage,
    clearMessages,
    loadHistoryMessages
  }
})
