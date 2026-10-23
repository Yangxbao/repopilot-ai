import { defineStore } from 'pinia'

const TOKEN_KEY = 'repopilot_token'
const USER_KEY = 'repopilot_user'

export interface StoredUser {
  id: number
  username: string
  nickname: string
  role: string
}

/** 登录态管理：token 与用户信息持久化到 localStorage。 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: JSON.parse(localStorage.getItem(USER_KEY) || 'null') as StoredUser | null
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    isAdmin: (state) => state.user?.role === 'ADMIN'
  },
  actions: {
    setSession(token: string, user: StoredUser) {
      this.token = token
      this.user = user
      localStorage.setItem(TOKEN_KEY, token)
      localStorage.setItem(USER_KEY, JSON.stringify(user))
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    }
  }
})
