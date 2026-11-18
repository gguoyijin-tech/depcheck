import http from './http'

/** 当前会话的项目列表（按 id 倒序）。 */
export function listProjects() {
  return http.get('/projects')
}

/** 项目详情。 */
export function getProject(id) {
  return http.get(`/projects/${id}`)
}

/**
 * 上传 zip 并解压。
 *
 * @param {File} file zip 文件
 * @param {(percent:number)=>void} onProgress 上传字节进度（不含服务端解压时间）
 */
export function uploadProject(file, onProgress) {
  const form = new FormData()
  form.append('file', file)
  return http.post('/projects/upload', form, {
    timeout: 0,
    onUploadProgress: (event) => {
      if (onProgress && event.total) {
        onProgress(Math.round((event.loaded / event.total) * 100))
      }
    }
  })
}
