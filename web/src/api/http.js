import { ElMessage } from 'element-plus'
import axios from 'axios'

import { useSessionStore } from '@/stores/session'

/** 后端业务错误（HTTP 200 + code!=0，或 HTTP 4xx + code）。 */
export class ApiError extends Error {
  constructor(code, message) {
    super(message || '请求失败')
    this.name = 'ApiError'
    this.code = code
  }
}

const http = axios.create({
  baseURL: '/api',
  // 扫描是异步的，这里只兜住"上传大包"这类长请求
  timeout: 600000
})

http.interceptors.request.use((config) => {
  const session = useSessionStore()
  if (session.sessionId) {
    config.headers['X-Session-Id'] = session.sessionId
  }
  return config
})

http.interceptors.response.use(
  (response) => {
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
    const body = error.response && error.response.data
    if (body && typeof body.code === 'number') {
      ElMessage.error(`[${body.code}] ${body.message}`)
      return Promise.reject(new ApiError(body.code, body.message))
    }
    if (error.response) {
      ElMessage.error(`服务端返回 HTTP ${error.response.status}`)
      return Promise.reject(new ApiError(error.response.status, `HTTP ${error.response.status}`))
    }
    ElMessage.error('无法连接后端：请确认后端已启动，且 vite 代理指向正确端口')
    return Promise.reject(new ApiError(-2, '网络不可用'))
  }
)

export default http
