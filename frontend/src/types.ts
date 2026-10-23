/** 与后端 DTO 一一对应的类型定义（见 docs/API.md）。 */

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
}

export interface AuthUser {
  id: number
  username: string
  nickname: string
  role: string
}

export interface AuthResponse {
  token: string
  expiresInHours: number
  user: AuthUser
}

export interface RepoVO {
  id: number
  name: string
  description: string | null
  originalFilename: string | null
  status: 'PENDING' | 'INDEXING' | 'READY' | 'FAILED'
  fileCount: number
  indexedCount: number
  totalSize: number
  errorMessage: string | null
  isDemo: boolean
  createdAt: string
}

export interface FileNode {
  label: string
  type: 'directory' | 'file'
  size: number
  children: FileNode[]
}

export interface LineMatch {
  lineNo: number
  text: string
}

export interface SearchHit {
  fileId: number
  path: string
  language: string
  score: number
  matches: LineMatch[]
}

export interface FileContentVO {
  path: string
  language: string
  lineCount: number
  lines: string[]
  content: string
}

export interface OutlineEntry {
  line: number
  kind: string
  text: string
}

export interface EvidenceVO {
  path: string
  startLine?: number
  endLine?: number
  snippet?: string
  reason?: string
}

export interface QaVO {
  id: number
  repoId: number
  repoName: string | null
  question: string
  answer: string | null
  evidence: EvidenceVO[]
  provider: string
  model: string | null
  durationMs: number
  status: 'OK' | 'FAILED'
  errorMessage: string | null
  createdAt: string
}

export interface BugCause {
  cause: string
  evidence: string[]
  confidence: string
}

export interface BugStep {
  priority: number
  step: string
  detail: string
}

export interface BugAnalysisResult {
  summary: string
  possibleCauses: BugCause[]
  troubleshootingSteps: BugStep[]
  openQuestions: string[]
  testSuggestions: string[]
  conclusion: string
  structured: boolean
  provider: string
}

export interface BugReportVO {
  id: number
  repoId: number
  repoName: string | null
  title: string
  symptom: string | null
  logContent: string | null
  expectedResult: string | null
  actualResult: string | null
  report: BugAnalysisResult | null
  status: string
  provider: string | null
  model: string | null
  durationMs: number
  errorMessage: string | null
  createdAt: string
}

export interface AiConfigVO {
  mode: 'mock' | 'remote'
  providerName: string
  model: string
  baseUrl: string | null
  apiKeyConfigured: boolean
  timeoutSeconds: number
  maxRetries: number
}

export interface DashboardSummary {
  repoCount: number
  fileCount: number
  qaCount: number
  bugReportCount: number
  aiMode: string
  aiProvider: string
  recentQas: { id: number; question: string; status: string; createdAt: string }[]
  recentReports: { id: number; title: string; status: string; createdAt: string }[]
}
