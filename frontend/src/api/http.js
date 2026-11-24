import { ElMessage } from 'element-plus'
import axios from 'axios'

export const KEY_TOKEN = 'depcheck.token'
export const KEY_SESSION = 'depcheck.sessionId'
export const KEY_USER = 'depcheck.username'

/** 后端业务错误。 */
export class ApiError extends Error {
  constructor(code, msg) {
    super(msg || '请求失败')
    this.name = 'ApiError'
    this.code = code
  }
}

/** 401：清 token 并回登录页（保留 sessionId 无妨，服务端按账号推导）。 */
function backToLogin() {
  localStorage.removeItem(KEY_TOKEN)
  localStorage.removeItem(KEY_USER)
  if (!window.location.hash.startsWith('#/login')) {
    window.location.hash = '#/login'
  }
}

const http = axios.create({ baseURL: '/api', timeout: 600000 })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(KEY_TOKEN)
  const sessionId = localStorage.getItem(KEY_SESSION)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  if (sessionId) {
    config.headers['X-Session-Id'] = sessionId
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob') {
      return response.data
    }
    const body = response.data
    if (body && typeof body.code === 'number' && body.code !== 0) {
      ElMessage.error(`[${body.code}] ${body.message}`)
      return Promise.reject(new ApiError(body.code, body.message))
    }
    return body ? body.data : null
  },
  (error) => {
    if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请重试')
      return Promise.reject(new ApiError(-1, '请求超时'))
    }
    const status = error.response && error.response.status
    const body = error.response && error.response.data
    if (status === 401) {
      ElMessage.warning('登录已失效，请重新登录')
      backToLogin()
      return Promise.reject(new ApiError(1002, '未登录或会话已过期'))
    }
    if (body && typeof body.code === 'number') {
      ElMessage.error(`[${body.code}] ${body.message}`)
      return Promise.reject(new ApiError(body.code, body.message))
    }
    if (error.response) {
      ElMessage.error(`服务端返回 HTTP ${status}`)
      return Promise.reject(new ApiError(status, `HTTP ${status}`))
    }
    ElMessage.error('无法连接后端：请确认后端已启动，且代理端口正确')
    return Promise.reject(new ApiError(-2, '网络不可用'))
  }
)

export default http
