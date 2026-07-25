// src/api/skillRepo.ts

const BASE_URL = '/api/skill-repo'

export interface BindRequest {
  agentName: string
  repoUrl: string
  skillPatterns?: string[]
}

export interface RefreshRequest {
  agentName: string
  threadId?: string
  repoUrl?: string
  skillPatterns?: string[]
}

export const skillRepoApi = {
  // 绑定仓库地址到指定 Agent
  bind: async (data: BindRequest) => {
    const res = await fetch(`${BASE_URL}/bind`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    })
    if (!res.ok) throw new Error(`HTTP Error: ${res.status}`)
    return res.json()
  },

  // 解绑仓库地址
  unbind: async (agentName: string) => {
    const res = await fetch(`${BASE_URL}/unbind/${encodeURIComponent(agentName)}`, {
      method: 'DELETE'
    })
    if (!res.ok) throw new Error(`HTTP Error: ${res.status}`)
    return res.json()
  },

  // 查看所有绑定
  list: async (): Promise<Record<string, string>> => {
    const res = await fetch(`${BASE_URL}/list`)
    if (!res.ok) throw new Error(`HTTP Error: ${res.status}`)
    return res.json()
  },

  // 刷新仓库 + 更新过滤规则 + 重建 Agent
  refresh: async (data: RefreshRequest) => {
    const res = await fetch(`${BASE_URL}/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    })
    if (!res.ok) throw new Error(`HTTP Error: ${res.status}`)
    return res.json()
  },

  // 查看仓库中的 Skill 列表
  getSkills: async (repoUrl: string): Promise<string[]> => {
    const query = new URLSearchParams({ repoUrl })
    const res = await fetch(`${BASE_URL}/skills?${query.toString()}`)
    if (!res.ok) throw new Error(`HTTP Error: ${res.status}`)
    const data = await res.json()
    return Array.isArray(data) ? data : []
  }
}
