import http from './http'

/** 登录（固定账号）。 */
export function login(username, password) {
  return http.post('/auth/login', { username, password })
}

/** 退出登录。 */
export function logout() {
  return http.post('/auth/logout')
}

/** 当前会话信息。 */
export function fetchMe() {
  return http.get('/auth/me')
}
