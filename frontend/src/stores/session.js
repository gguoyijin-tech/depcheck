import { defineStore } from 'pinia'

import { login as loginApi, logout as logoutApi } from '@/api/auth'
import { KEY_SESSION, KEY_TOKEN, KEY_USER } from '@/api/http'

/**
 * 会话：登录后持有 token；业务数据按账号隔离，换浏览器登录也能看到自己的任务。
 */
export const useSessionStore = defineStore('session', {
  state: () => ({
    token: localStorage.getItem(KEY_TOKEN) || '',
    sessionId: localStorage.getItem(KEY_SESSION) || '',
    username: localStorage.getItem(KEY_USER) || ''
  }),
  getters: {
    loggedIn: (state) => Boolean(state.token)
  },
  actions: {
    async login(username, password) {
      const data = await loginApi(username, password)
      localStorage.setItem(KEY_TOKEN, data.token)
      localStorage.setItem(KEY_SESSION, data.sessionId)
      localStorage.setItem(KEY_USER, data.username)
      this.token = data.token
      this.sessionId = data.sessionId
      this.username = data.username
      return data
    },
    async logout() {
      try {
        await logoutApi()
      } catch {
        // token 可能已过期，忽略
      }
      localStorage.removeItem(KEY_TOKEN)
      localStorage.removeItem(KEY_USER)
      this.token = ''
      this.username = ''
    }
  }
})
