// src/api/session.ts
import type { PageChatMessageVO } from '../types/chat'

export interface UserSessionMappingEntity {
  sessionid: string;
  userid: string;
  title: string;
}

// 注意：若项目配置了 Vite Proxy，可将 URL 前缀改为 '/api/userSessionMapping' 等
const BASE_URL = import.meta.env.VITE_SESSION_BASE_URL || '/userSessionMapping';
// 鉴权系统尚未接入，全局写死测试用 userID
const DEFAULT_USER_ID = 'user001';

export const sessionApi = {
  // 1. 新建会话获取 ThreadID (忽略外部传入的 userID，强制使用默认值)
  getThreadID: async (userID?: string): Promise<string> => {
    const res = await fetch(`${BASE_URL}/getThreadID?userID=${DEFAULT_USER_ID}`, { method: 'POST' });
    if (!res.ok) throw new Error(`HTTP Error: ${res.status}`);
    return res.text();
  },
  
  // 2. 获取所有会话列表
  getList: async (): Promise<UserSessionMappingEntity[]> => {
    const res = await fetch(`${BASE_URL}/list`);
    if (!res.ok) throw new Error(`HTTP Error: ${res.status}`);
    const data = await res.json();
    // 兼容处理：确保返回的一定是数组格式，防止后端报错返回对象导致前端 map 崩溃
    return Array.isArray(data) ? data : (data.data || []);
  },

  // 3. 更新会话标题
  update: async (data: UserSessionMappingEntity): Promise<boolean> => {
    // 强制覆盖更新时的 userid
    const payload = { ...data, userid: DEFAULT_USER_ID };
    const res = await fetch(`${BASE_URL}/update`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    if (!res.ok) throw new Error(`HTTP Error: ${res.status}`);
    return res.json();
  },

  // 4. 删除指定会话
  remove: async (id: string): Promise<boolean> => {
    const res = await fetch(`${BASE_URL}/remove/${id}`, { method: 'DELETE' });
    if (!res.ok) throw new Error(`HTTP Error: ${res.status}`);
    return res.json();
  },

  // 5. 获取会话历史消息（回写）
  getHistoryMsg: async (params: {
    sessionId: string;
    pageNumber: number;
    size: number;
  }): Promise<PageChatMessageVO> => {
    const query = new URLSearchParams({
      sessionId: params.sessionId,
      pageNumber: String(params.pageNumber),
      size: String(params.size)
    })
    const res = await fetch(`${BASE_URL}/historyMsg?${query.toString()}`)
    if (!res.ok) throw new Error(`HTTP Error: ${res.status}`)
    return res.json()
  },

  // 6. 生成会话标题
  generateTitle: async (params: { sessionId: string; userInput: string }): Promise<string> => {
    const query = new URLSearchParams({
      sessionId: params.sessionId,
      userId: DEFAULT_USER_ID,
      userInput: params.userInput
    });
    const res = await fetch(`${BASE_URL}/newTitle?${query.toString()}`, {
      method: 'POST'
    });
    if (!res.ok) throw new Error(`HTTP Error: ${res.status}`);
    return res.text();
  },

  // 7. 获取会话报错数据 (标记会话功能)
  getErrorMsg: async (sessionId: string): Promise<string[]> => {
    const res = await fetch(`${BASE_URL}/getErrorMsg?sessionId=${sessionId}`);
    if (!res.ok) throw new Error(`HTTP Error: ${res.status}`);
    return res.json();
  }
};