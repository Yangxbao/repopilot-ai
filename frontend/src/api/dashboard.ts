import { http } from './http'
import type { AiConfigVO, DashboardSummary } from '../types'
import type { PageResult } from '../types'

export const dashboardApi = {
  summary: () => http.get<never, DashboardSummary>('/dashboard/summary')
}

export const aiApi = {
  config: () => http.get<never, AiConfigVO>('/ai-config'),
  tools: () => http.get<never, Record<string, string>>('/ai-config/tools'),
  test: () =>
    http.post<never, { success: boolean; mode?: string; latencyMs?: number; message?: string; reply?: string }>(
      '/ai-config/test'
    )
}

export const userApi = {
  list: (page = 1, size = 10) =>
    http.get<never, PageResult<{
      id: number
      username: string
      nickname: string
      role: string
      status: string
      createdAt: string
    }>>('/users', { params: { page, size } }),
  changeStatus: (id: number, status: 'ACTIVE' | 'DISABLED') =>
    http.put<never, void>(`/users/${id}/status`, { status })
}
