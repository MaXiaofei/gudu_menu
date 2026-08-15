import { BASE } from '@/utils/request'
import { getToken } from '@/utils/request'

export interface UploadResult {
  url: string // 原图（相对路径 /gudu/uploads/original/xxx.jpg）
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
      fail: () => resolve([]),
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

/** 上传一张图：POST /file/upload（multipart）→ 原图 + 缩略图 URL。 */
export async function uploadImage(path: string): Promise<UploadResult> {
  const filePath = await compress(path)
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: `${BASE}/file/upload`,
      filePath,
      name: 'file',
      formData: { filename: 'upload.jpg' },
      header: { Authorization: getToken() },
      success: (res) => {
        try {
          const body = JSON.parse(res.data)
          if (body.code !== 0) {
            uni.showToast({ title: body.msg || '上传失败', icon: 'none' })
            reject(new Error(body.msg))
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
