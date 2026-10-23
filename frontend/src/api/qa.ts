import { http } from './http'
import type { BugReportVO, PageResult, QaVO } from '../types'

export const qaApi = {
  ask: (repoId: number, question: string) =>
    http.post<never, QaVO>(`/repos/${repoId}/qa`, { question }),
  history: (repoId?: number, page = 1, size = 10) =>
    http.get<never, PageResult<QaVO>>('/qa', { params: { repoId, page, size } }),
  detail: (id: number) => http.get<never, QaVO>(`/qa/${id}`)
}

export interface BugAnalysisPayload {
  title: string
  symptom?: string
  logContent?: string
  expectedResult?: string
  actualResult?: string
}

export const bugApi = {
  analyze: (repoId: number, payload: BugAnalysisPayload) =>
    http.post<never, BugReportVO>(`/repos/${repoId}/bug-analysis`, payload),
  history: (repoId?: number, page = 1, size = 10) =>
    http.get<never, PageResult<BugReportVO>>('/bug-reports', { params: { repoId, page, size } }),
  detail: (id: number) => http.get<never, BugReportVO>(`/bug-reports/${id}`)
}
