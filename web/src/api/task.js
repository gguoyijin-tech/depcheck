import http from './http'

/**
 * 提交扫描任务（异步，立即返回 taskId）。
 *
 * @param {number} projectId 项目 ID
 */
export function createTask(projectId) {
  return http.post('/scan-tasks', { projectId })
}

/** 任务详情（轮询用）。 */
export function getTask(id) {
  return http.get(`/scan-tasks/${id}`)
}

/**
 * 任务列表。
 *
 * @param {string} [status] QUEUED / RUNNING / SUCCESS / FAILED / CANCELED
 */
export function listTasks(status) {
  return http.get('/scan-tasks', { params: status ? { status } : {} })
}

/** 请求取消任务（排队中立即生效；执行中在阶段边界生效）。 */
export function cancelTask(id) {
  return http.post(`/scan-tasks/${id}/cancel`)
}
