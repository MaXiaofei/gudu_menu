import { request } from './request'

export interface UploadResult {
  url: string
  name: string
}

/** 上传文件，返回可访问 URL */
export function upload(file: File) {
  const form = new FormData()
  form.append('file', file)
  return request<UploadResult>({
    url: '/file/upload',
    method: 'post',
    data: form,
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
