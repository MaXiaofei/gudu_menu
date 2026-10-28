import { BASE } from '@/utils/request'
import { authHeaders } from '@/utils/request'
import { contentSecurityMessage } from '@/utils/contentSecurity'
import { toastChooseImageFail } from '@/utils/chooseImage'

export interface UploadResult {
  url: string // 原图（相对路径 /gudu/uploads/original/{日期}/{uuid}.jpg?e=&s=，带短时签名，原样回传保存）
  thumbnailUrl: string
  name: string
}

/** 选图（相册，count 张）→ 返回本地临时路径。 */
export function chooseImages(count = 1): Promise<string[]> {
  return new Promise((resolve) => {
    uni.chooseMedia({
      count,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      success: (res) => resolve(res.tempFiles.map((f) => f.tempFilePath)),
      fail: (e) => {
        toastChooseImageFail(e?.errMsg)
        resolve([])
      },
    })
  })
}

/** 压缩（失败回退原图，对齐 Flutter 策略）。 */
async function compress(path: string): Promise<string> {
  try {
    const r = await uni.compressImage({ src: path, quality: 80 })
    return r.tempFilePath
  } catch {
    return path
  }
}

/**
 * 上传一张图：POST /file/upload（multipart）→ 原图 + 缩略图 URL。
 * usage：图片用途（avatar / post / …），后端据此选微信内容安全场景；上传后异步检测，违规图片会被隔离。
 */
export async function uploadImage(path: string, usage?: 'avatar' | 'post' | 'feedback'): Promise<UploadResult> {
  const filePath = await compress(path)
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: `${BASE}/file/upload`,
      filePath,
      name: 'file',
      formData: usage ? { filename: 'upload.jpg', usage } : { filename: 'upload.jpg' },
      header: authHeaders(),
      success: (res) => {
        try {
          const body = JSON.parse(res.data)
          if (body.code !== 0) {
            const msg = contentSecurityMessage(body.code, usage === 'avatar' ? 'avatar' : 'default') || body.msg || '上传失败'
            uni.showToast({ title: msg, icon: 'none' })
            reject(new Error(msg))
            return
          }
          resolve(body.data as UploadResult)
        } catch (e) {
          reject(e)
        }
      },
      fail: (e) => reject(new Error(e.errMsg)),
    })
  })
}
