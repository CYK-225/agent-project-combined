/**
 * 聊天模块类型定义
 */

// ==================== 历史消息回写 - 接口数据模型 ====================

/** 工具输出项 */
export interface ToolOutput {
  type?: string
  text?: string
}

/** 内容项（支持思考、文本、工具调用等多种类型） */
export interface ContentItem {
  type?: string
  thinking?: string
  text?: string
  metadata?: Record<string, any>
  id?: string
  name?: string
  output?: ToolOutput[]
}

/** Token 用量统计 */
export interface ChatUsage {
  inputTokens?: number
  outputTokens?: number
  time?: number
  totalTokens?: number
}

/** 消息元数据 */
export interface MessageMetadata {
  _chat_usage?: ChatUsage
}

/** 后端 ChatMessage 数据结构 */
export interface ChatMessageDTO {
  id?: string
  name?: string
  role?: string
  content?: ContentItem[]
  metadata?: MessageMetadata
  timestamp?: string
}

/** 后端 ChatMessageVO 数据结构（带 index 包装） */
export interface ChatMessageVO {
  id?: string
  index?: number
  chatMessage?: ChatMessageDTO
}

/** 分页查询结果 */
export interface PageChatMessageVO {
  records?: ChatMessageVO[]
  pageNumber?: number
  pageSize?: number
  maxPageSize?: number
  totalPage?: number
  totalRow?: number
  optimizeCountQuery?: boolean
}

// ==================== 前端 UI 数据模型 ====================

// 消息状态类型
export type MessageStatus = 'running' | 'success' | 'error' | 'idle'

// 消息角色类型
export type MessageRole = 'user' | 'assistant' | 'system' | 'reasoning' | 'tool' | string

// 消息接口
export interface Message {
  id: string
  role: MessageRole
  content: string
  _originalRole?: string
  isCollapsed?: boolean
  status?: MessageStatus
  toolName?: string
  agentId?: string
  params?: string
  [key: string]: any
}

// 聊天历史接口
export interface ChatHistory {
  id: string
  title: string
  timestamp: string
  isActive?: boolean
  isGeneratingTitle?: boolean
  agentId?: string   
  agentName?: string 
}

// 工具内容解析结果接口
export interface ParsedToolContent {
  skillName: string | null
  params: string | null
  description: string
  isStructured: boolean
}

// 角色映射表
export const ROLE_MAPPING: Record<string, string> = {
  reasoning: 'reasoning',
  tool: 'tool',
  function: 'tool',
  assistant: 'assistant',
  user: 'user',
  system: 'system'
}

/**
 * 根据消息内容解析角色
 */
export function resolveRoleByContent(msg: any): string {
  const content = msg.content || msg.delta || ''
  if (typeof content === 'string') {
    if (content.includes('<function=') || content.includes('tool_call')) {
      return 'tool'
    }
  }
  return ROLE_MAPPING[msg.role] || 'assistant'
}

/**
 * 提取文本内容
 */
export function extractTextContent(content: any): string {
  if (typeof content === 'string') return content
  if (Array.isArray(content)) {
    return content
      .map((item: any) => {
        if (typeof item === 'string') return item
        if (item && typeof item === 'object') {
          if (item.text) return item.text
          if (item.content) return extractTextContent(item.content)
        }
        return ''
      })
      .join('')
  }
  if (content && typeof content === 'object') {
    return content.text || content.content || JSON.stringify(content)
  }
  return String(content || '')
}

/**
 * 拦截并处理消息
 */
export function interceptMessage(msg: any): { role: string; content: string } {
  const resolvedRole = resolveRoleByContent(msg)
  const textContent = extractTextContent(msg.content || msg.delta || '')
  return { role: resolvedRole, content: textContent }
}

/**
 * 解析工具内容
 */
export function parseToolContent(content: string): ParsedToolContent {
  // 匹配 [系统决策]: 激活技能 -> xxx | 参数详情 -> {xxx} 格式
  const decisionRegex = /\[系统决策\]:\s*激活技能\s*->\s*(.+?)\s*\|\s*参数详情\s*->\s*(\{[^\}]*\})/
  const match = content.match(decisionRegex)
  
  if (match) {
    const [, skillName, params] = match
    // 提取剩余描述文本
    const description = content.replace(decisionRegex, '').trim()
    
    return {
      skillName: skillName?.trim() || null,
      params: params?.trim() || null,
      description,
      isStructured: true
    }
  }
  
  // 尝试匹配其他工具调用格式
  const toolCallRegex = /工具[\s\w]*调[\s\w]*用|tool[_\s]?call|function[_\s]?call/i
  const jsonRegex = /\{[\s\S]*\}/
  
  const jsonMatch = content.match(jsonRegex)
  
  if (toolCallRegex.test(content) && jsonMatch) {
    return {
      skillName: '工具调用',
      params: jsonMatch[0],
      description: content.replace(jsonMatch[0], '').trim(),
      isStructured: true
    }
  }
  
  return {
    skillName: null,
    params: null,
    description: content,
    isStructured: false
  }
}

/**
 * 格式化 JSON 字符串
 */
export function formatJSON(jsonStr: string): string {
  try {
    const obj = JSON.parse(jsonStr)
    return JSON.stringify(obj, null, 2)
  } catch {
    return jsonStr
  }
}

// ==================== 历史消息转换工具 ====================

/**
 * 将后端 ChatMessageVO 转换为前端 Message 对象
 * 一条 ChatMessageVO 可能包含多种 content 类型（thinking / text / tool），
 * 拆分为多条前端 Message 以适配现有 UI 渲染逻辑。
 */
export function convertChatMessageVOToMessages(vo: ChatMessageVO): Message[] {
  const results: Message[] = []
  const chatMsg = vo.chatMessage
  if (!chatMsg) return results
  const agentId = chatMsg.name
  console.log("获取agentid："+ agentId)
  const baseId = vo.id || chatMsg.id || `hist-${Date.now()}-${Math.random().toString(36).substr(2, 6)}`
  // 后端 role 可能是大写（如 "USER"、"ASSISTANT"），统一转小写后再映射
  const rawRole = (chatMsg.role || 'assistant').toLowerCase()
  const contents = Array.isArray(chatMsg.content) ? chatMsg.content : []
  
  // 如果 content 为空，生成一条占位消息
  if (contents.length === 0) {
    results.push({
      id: baseId,
      role: ROLE_MAPPING[rawRole] || rawRole,
      content: '',
      _originalRole: rawRole,
      status: 'success'
    })
    return results
  }

  for (let i = 0; i < contents.length; i++) {
    const item = contents[i]
    const itemId = `${baseId}-${i}`

    // 1) 思考内容 → reasoning 消息
    if (item.type === 'thinking' || item.thinking) {
      results.push({
        id: `${itemId}-reasoning`,
        role: 'reasoning',
        content: item.thinking || '',
        _originalRole: rawRole,
        isCollapsed: true,
        agentId: agentId,
        status: 'success'
      })
      continue
    }

    // 2) 工具调用内容 → tool 消息
    if (item.type === 'tool_use' || item.type === 'tool_result' || item.name) {
      const toolOutputText = Array.isArray(item.output)
        ? item.output.map(o => o.text || '').join('\n')
        : ''
      results.push({
        id: `${itemId}-tool`,
        role: 'tool',
        content: toolOutputText || item.text || '',
        _originalRole: rawRole,
        toolName: item.name || '未知工具',
        params: '',
        isCollapsed: true,
        agentId: agentId,
        status: 'success'
      })
      continue
    }

    // 3) 普通文本内容 → 按原始角色映射
    const text = item.text || ''
    if (text) {
      results.push({
        id: `${itemId}-text`,
        role: ROLE_MAPPING[rawRole] || rawRole,
        content: text,
        _originalRole: rawRole,
        agentId: agentId,
        status: 'success',
      })
    }
  }

  return results
}
