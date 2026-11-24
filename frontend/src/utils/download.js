import { downloadReport } from '@/api/task'

/**
 * 下载某个任务的 HTML 报告。
 *
 * 后端出错时返回 JSON（统一响应结构），这里识别后抛错，避免下成坏文件。
 *
 * @param {number} taskId 任务 ID
 */
export async function downloadHtmlReport(taskId) {
  const blob = await downloadReport(taskId)
  if (blob.type && blob.type.includes('json')) {
    const body = JSON.parse(await blob.text())
    throw new Error(`[${body.code}] ${body.message}`)
  }
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `dependency-check-${taskId}.html`
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
