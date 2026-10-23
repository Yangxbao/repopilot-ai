import axios from 'axios'
import type { ApiResponse } from '../types'
import { useAuthStore } from '../stores/auth'

/**
 * 统一的 axios 实例：
 * - 自动携带 JWT；
 * - 自动解包后端的 ApiResponse：拦截器直接返回业务数据（data 字段），
 *   code != 0 时弹出错误并抛出异常；
 * - 401 时清除本地登录态并跳转登录页。
 */
export const http = axios.create({ baseURL: '/api', timeout: 120000 })

http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

function gotoLoginIfNeeded() {
  const auth = useAuthStore()
  auth.logout()
  if (window.location.pathname !== '/login') {
    window.location.href = '/login'
  }
}

http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown> | undefined
    // 后端统一返回 {code, message, data}；这里解包出 data，让 api 层直接拿到业务对象
    if (body && typeof body.code === 'number' && ('data' in body || 'message' in body)) {
      if (body.code !== 0) {
        if (body.code === 40101) {
          gotoLoginIfNeeded()
        }
        return Promise.reject(new Error(body.message || '请求失败'))
      }
      return body.data as never
    }
    return response
  },
  (error) => {
    // HTTP 层错误（401/403/500 等），尝试读出后端 JSON 错误信息
    const body = error.response?.data as ApiResponse<unknown> | undefined
    if (body && typeof body.code === 'number') {
      if (body.code === 40101) {
        gotoLoginIfNeeded()
      }
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return Promise.reject(new Error(error.message || '网络错误，请检查后端是否已启动'))
  }
)
