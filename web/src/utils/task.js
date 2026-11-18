/** 任务状态 → 文案 / 标签色（与后端 TaskStatus 对齐）。 */
export const STATUS_MAP = {
  QUEUED: { text: '排队中', type: 'info' },
  RUNNING: { text: '扫描中', type: 'warning' },
  SUCCESS: { text: '成功', type: 'success' },
  FAILED: { text: '失败', type: 'danger' },
  CANCELED: { text: '已取消', type: 'info' }
}

export const STATUS_OPTIONS = [
  { value: '', label: '全部状态' },
  { value: 'QUEUED', label: '排队中' },
  { value: 'RUNNING', label: '扫描中' },
  { value: 'SUCCESS', label: '成功' },
  { value: 'FAILED', label: '失败' },
  { value: 'CANCELED', label: '已取消' }
]

/** 阶段 → 中文（与后端 TaskStage 对齐，引擎上报 SCAN/ANALYZE/REPORT）。 */
export const STAGE_MAP = {
  QUEUED: '已入队',
  PREPARE: '准备中',
  SCAN: '采集依赖',
  ANALYZE: '漏洞分析',
  REPORT: '生成报告',
  PERSIST: '写入数据库',
  DONE: '已完成',
  FAILED: '已失败',
  CANCELED: '已取消',
  TIMEOUT: '已超时'
}

export function statusText(status) {
  return (STATUS_MAP[status] || {}).text || status || '-'
}

export function statusType(status) {
  return (STATUS_MAP[status] || {}).type || 'info'
}

export function stageText(stage) {
  return STAGE_MAP[stage] || stage || '-'
}

/** 还在跑（需要轮询）的状态。 */
export function isActive(status) {
  return status === 'QUEUED' || status === 'RUNNING'
}

/** 毫秒 → 12.3s / 2m 05s。 */
export function formatDuration(ms) {
  if (ms === null || ms === undefined || ms === '') {
    return '-'
  }
  if (ms < 1000) {
    return `${ms} ms`
  }
  const seconds = ms / 1000
  if (seconds < 60) {
    return `${seconds.toFixed(1)} s`
  }
  const minutes = Math.floor(seconds / 60)
  const rest = Math.round(seconds % 60)
  return `${minutes}m ${String(rest).padStart(2, '0')}s`
}

/** 2026-11-18T18:53:55.13 → 2026-11-18 18:53:55 */
export function formatDateTime(value) {
  if (!value) {
    return '-'
  }
  const text = String(value).replace('T', ' ')
  return text.length > 19 ? text.slice(0, 19) : text
}

/** 进度条颜色：失败红、成功绿、运行中蓝。 */
export function progressStatus(status) {
  if (status === 'FAILED') {
    return 'exception'
  }
  if (status === 'SUCCESS') {
    return 'success'
  }
  return undefined
}
