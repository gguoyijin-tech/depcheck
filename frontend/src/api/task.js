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

/** 下载该任务的 HTML 报告（返回 Blob）。 */
export function downloadReport(id) {
  return http.get(`/scan-tasks/${id}/report`, { responseType: 'blob', timeout: 120000 })
}

/** 结果概览：依赖数、漏洞数、等级与许可证分布。 */
export function getSummary(id) {
  return http.get(`/scan-tasks/${id}/summary`)
}

/** 依赖分页。 */
export function listDependencies(id, query = {}) {
  return http.get(`/scan-tasks/${id}/dependencies`, { params: query })
}

/** 漏洞分页（按 CVSS 降序）。 */
export function listVulnerabilities(id, query = {}) {
  return http.get(`/scan-tasks/${id}/vulnerabilities`, { params: query })
}
