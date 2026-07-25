import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ChatHistory } from '../../types/chat'
import { sessionApi } from '../../api/session'
import { StoreLogger } from '../../utils/storeLogger'

export const AGENT_MODE_MAP: Record<string, string> = {
  'FinanceForecastAgent': '利润分析模式',
  'MasterUserAgents': '用户分析模式'
}

export const useSessionStore = defineStore('session', () => {
  // ==================== 状态 ====================
  /** 会话历史列表 */
  const chatHistory = ref<ChatHistory[]>([])
  /** 当前选中的会话线程 ID */
  const currentThreadId = ref<string>('')
  /** 全局加载状态 */
  const isLoading = ref(false)

  /** 当前会话的agent模式 */
  const currentAgentId = ref<string>('FinanceForecastAgent')

  // ==================== 动作 ====================

  /**
   * 从后端加载会话列表，并刷新 chatHistory
   */
  const loadSessionList = async () => {
    StoreLogger.logAction({
      storeName: 'session',
      action: 'loadSessionList',
      payload: {}
    });

    try {
      const list = await sessionApi.getList()
      chatHistory.value = list.map(item => ({
        id: item.sessionid,
        title: item.title || '新对话',
        timestamp: '历史会话', // 后端数据模型暂无时间字段，做统一文案兜底
        isActive: item.sessionid === currentThreadId.value
      }))

      StoreLogger.logStateChange({
        storeName: 'session',
        action: 'loadSessionListSuccess',
        state: { chatHistory: chatHistory.value },
        result: list
      });
    } catch (error) {
      StoreLogger.logAction({
        storeName: 'session',
        action: 'loadSessionListError',
        error: error
      });
      console.error('获取历史会话列表失败:', error)
    }
  }


  /**
   * 根据会话历史更新当前的 Agent 模式
   */
  const updateSessionAgentMode = (sessionId: string, agentId: string) => {
    if (!agentId) return
    currentAgentId.value = agentId
    
    // 同步修改左侧边栏历史记录绑定的属性
    const target = chatHistory.value.find(c => c.id === sessionId)
    if (target) {
      target.agentId = agentId
      target.agentName = AGENT_MODE_MAP[agentId] || agentId
    }
  }


  /**
   * 创建真实会话：调用后端获取新的 ThreadID，前端预插入记录
   * @returns 新创建的 sessionId
   */
  const createRealSession = async (): Promise<string> => {
    StoreLogger.logAction({
      storeName: 'session',
      action: 'createRealSession',
      payload: {}
    });

    try {
      isLoading.value = true
      const newSessionId = await sessionApi.getThreadID()
      currentThreadId.value = newSessionId

      // ====== 【核心修复 1】乐观更新：不等待接口，直接在前端列表插入一条新记录 ======
      // 防止 loadSessionList 刷新整个数组导致标题生成的动画状态丢失
      const tempChat: ChatHistory = {
        id: newSessionId,
        title: '新对话',
        timestamp: '刚刚',
        isActive: true,
        agentId: currentAgentId.value,
        agentName: AGENT_MODE_MAP[currentAgentId.value] || currentAgentId.value
      }
      
      // 取消原有历史记录的高亮，并将新会话置顶
      chatHistory.value.forEach(c => c.isActive = false)
      chatHistory.value.unshift(tempChat)
      // ======================================================================

      StoreLogger.logStateChange({
        storeName: 'session',
        action: 'createRealSessionSuccess',
        state: {
          currentThreadId: currentThreadId.value,
          chatHistory: chatHistory.value
        },
        result: newSessionId
      });

      return newSessionId
    } catch (error) {
      StoreLogger.logAction({
        storeName: 'session',
        action: 'createRealSessionError',
        error: error
      });
      console.error('创建真实会话请求失败:', error)
      throw error
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 准备本地会话：仅在前端清空当前会话状态，不调用后端API
   */
  const prepareLocalSession = () => {
    StoreLogger.logAction({
      storeName: 'session',
      action: 'prepareLocalSession',
      payload: {}
    });
    // 清空当前选中的 ThreadID，表示这是一个尚未在后端创建的临时会话
    currentThreadId.value = '';
    // 取消侧边栏所有历史记录的高亮
    chatHistory.value.forEach(c => c.isActive = false);
  }

  /**
   * 切换选中的会话，更新高亮状态和 currentThreadId
   * @param chat - 选中的会话对象
   */
  const selectSession = (chat: ChatHistory) => {
    StoreLogger.logAction({
      storeName: 'session',
      action: 'selectSession',
      payload: { chat }
    });

    chatHistory.value.forEach(c => c.isActive = false)
    chat.isActive = true
    currentThreadId.value = chat.id

    StoreLogger.logStateChange({
      storeName: 'session',
      action: 'selectSessionSuccess',
      state: {
        currentThreadId: currentThreadId.value,
        chatHistory: chatHistory.value
      },
      result: { selectedChat: chat }
    });
  }

  /**
   * 更新指定会话的标题（本地 + 后端）
   * @param sessionId - 会话 ID
   * @param title - 新标题
   */
  const updateSessionTitle = async (sessionId: string, title: string) => {
    StoreLogger.logAction({
      storeName: 'session',
      action: 'updateSessionTitle',
      payload: { sessionId, title }
    });

    // 本地同步更新侧边栏显示的标题
    const targetChat = chatHistory.value.find(c => c.id === sessionId)
    if (targetChat) targetChat.title = title

    try {
      await sessionApi.update({
        sessionid: sessionId,
        userid: '', // API 层会默认拦截并写入
        title
      })

      StoreLogger.logStateChange({
        storeName: 'session',
        action: 'updateSessionTitleSuccess',
        state: { chatHistory: chatHistory.value },
        result: { sessionId, title }
      });
    } catch (error) {
      StoreLogger.logAction({
        storeName: 'session',
        action: 'updateSessionTitleError',
        error: error
      });
      console.error('更新会话标题失败:', error)
      throw error
    }
  }

  /**
   * 智能生成并更新会话标题
   * @param sessionId - 会话 ID
   * @param userInput - 用户输入的原始文本
   */
  const generateAndUpdateTitle = async (sessionId: string, userInput: string) => {
    StoreLogger.logAction({
      storeName: 'session',
      action: 'generateAndUpdateTitle',
      payload: { sessionId, userInput }
    });

    // 1. 设置初始生成状态
    const initialChat = chatHistory.value.find(c => c.id === sessionId);
    if (initialChat) {
      initialChat.isGeneratingTitle = true;
      initialChat.title = "智能生成中...";
    }

    try {
      // 调用后端API生成新标题
      const newTitle = await sessionApi.generateTitle({ sessionId, userInput });

      // 更新后端数据库中的标题
      await sessionApi.update({
        sessionid: sessionId,
        userid: '',
        title: newTitle
      });

      // ====== 【核心修复 2】重新获取对象引用 ======
      // 在经历多个 await 后，为了安全起见重新 find 一次最新的对象
      const currentChat = chatHistory.value.find(c => c.id === sessionId);
      if (currentChat) {
        currentChat.title = newTitle;
        currentChat.isGeneratingTitle = false;
      }

      StoreLogger.logStateChange({
        storeName: 'session',
        action: 'generateAndUpdateTitleSuccess',
        state: { chatHistory: chatHistory.value },
        result: { sessionId, title: newTitle }
      });
    } catch (error) {
      StoreLogger.logAction({
        storeName: 'session',
        action: 'generateAndUpdateTitleError',
        error: error
      });

      // 错误兜底处理
      const fallbackTitle = userInput.length > 15 ? userInput.slice(0, 15) + '...' : userInput;
      
      const currentChat = chatHistory.value.find(c => c.id === sessionId);
      if (currentChat) {
        currentChat.title = fallbackTitle;
        currentChat.isGeneratingTitle = false;
      }

      console.error('智能生成标题失败，已使用兜底方案:', error);
    }
  }

  /**
   * 删除指定会话（本地 + 后端）
   * @param sessionId - 会话 ID
   */
  const deleteSession = async (sessionId: string) => {
    StoreLogger.logAction({
      storeName: 'session',
      action: 'deleteSession',
      payload: { sessionId }
    });

    try {
      // 调用后端API删除会话
      await sessionApi.remove(sessionId)

      // 从本地 chatHistory 中移除该记录
      const sessionIndex = chatHistory.value.findIndex(c => c.id === sessionId)
      if (sessionIndex !== -1) {
        chatHistory.value.splice(sessionIndex, 1)
      }

      // 核心判断：如果删除的是当前激活的会话，则自动切换到列表中的第一个会话
      if (currentThreadId.value === sessionId) {
        if (chatHistory.value.length > 0) {
          // 切换到第一个会话
          const firstChat = chatHistory.value[0]
          currentThreadId.value = firstChat.id
          
          // 更新高亮状态
          chatHistory.value.forEach(c => c.isActive = false)
          firstChat.isActive = true
        } else {
          // 如果没有历史记录了，清空当前会话状态
          currentThreadId.value = ''
        }
      }

      StoreLogger.logStateChange({
        storeName: 'session',
        action: 'deleteSessionSuccess',
        state: {
          chatHistory: chatHistory.value,
          currentThreadId: currentThreadId.value
        },
        result: { sessionId }
      });
    } catch (error) {
      StoreLogger.logAction({
        storeName: 'session',
        action: 'deleteSessionError',
        error: error
      });
      console.error('删除会话失败:', error)
      throw error
    }
  }

  return {
    // 状态
    chatHistory,
    currentThreadId,
    isLoading,
    currentAgentId,
    // 动作
    updateSessionAgentMode,
    loadSessionList,
    createRealSession,
    prepareLocalSession,
    selectSession,
    updateSessionTitle,
    generateAndUpdateTitle,
    deleteSession
  }
})
