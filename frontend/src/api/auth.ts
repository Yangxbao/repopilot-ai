import { http } from './http'
import type { AuthResponse, AuthUser } from '../types'

export const authApi = {
  register: (username: string, password: string, nickname?: string) =>
    http.post<never, AuthResponse>('/auth/register', { username, password, nickname }),
  login: (username: string, password: string) =>
    http.post<never, AuthResponse>('/auth/login', { username, password }),
  me: () => http.get<never, AuthUser>('/auth/me')
}
