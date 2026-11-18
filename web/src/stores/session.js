import { defineStore } from 'pinia'

const KEY_ID = 'depcheck.sessionId'
const KEY_NAME = 'depcheck.nickname'

/**
 * 会话：后端用 X-Session-Id 做数据隔离。
 *
 * D9 阶段是"占位登录"——输入昵称即生成一个本地会话标识；
 * D11 会换成正式登录（JWT），届时这里只保留 token。
 */
export const useSessionStore = defineStore('session', {
  state: () => ({
    sessionId: localStorage.getItem(KEY_ID) || '',
    nickname: localStorage.getItem(KEY_NAME) || ''
  }),
  getters: {
    loggedIn: (state) => Boolean(state.sessionId)
  },
  actions: {
    login(nickname) {
      const name = (nickname || '').trim() || '内部用户'
      if (!this.sessionId) {
        // 后端限制 64 字符，这里 "sess-" + 32 位 = 37 位
        this.sessionId = 'sess-' + crypto.randomUUID().replace(/-/g, '')
      }
      this.nickname = name
      localStorage.setItem(KEY_ID, this.sessionId)
      localStorage.setItem(KEY_NAME, this.nickname)
    },
    logout() {
      this.sessionId = ''
      this.nickname = ''
      localStorage.removeItem(KEY_ID)
      localStorage.removeItem(KEY_NAME)
    }
  }
})
