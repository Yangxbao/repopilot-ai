import { http } from './http'
import type { FileContentVO, FileNode, PageResult, RepoVO, SearchHit } from '../types'

export const repoApi = {
  list: (page = 1, size = 10) =>
    http.get<never, PageResult<RepoVO>>('/repos', { params: { page, size } }),
  detail: (id: number) => http.get<never, RepoVO>(`/repos/${id}`),
  upload: (file: File, name: string, description?: string) => {
    const form = new FormData()
    form.append('file', file)
    form.append('name', name)
    if (description) form.append('description', description)
    return http.post<never, RepoVO>('/repos/upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  importSample: () => http.post<never, RepoVO>('/repos/import-sample'),
  tree: (id: number) => http.get<never, FileNode[]>(`/repos/${id}/tree`),
  file: (id: number, path: string) =>
    http.get<never, FileContentVO>(`/repos/${id}/files`, { params: { path } }),
  outline: (id: number, path: string) =>
    http.get<never, { path: string; entries: { line: number; kind: string; text: string }[] }>(
      `/repos/${id}/outline`,
      { params: { path } }
    ),
  search: (id: number, q: string, topK = 10) =>
    http.get<never, SearchHit[]>(`/repos/${id}/search`, { params: { q, topK } }),
  reindex: (id: number) => http.post<never, RepoVO>(`/repos/${id}/reindex`),
  remove: (id: number) => http.delete<never, void>(`/repos/${id}`)
}
