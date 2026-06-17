import { request, getToken } from '@/utils/request'

export const submitReview = (data: any) => request({ url: '/review', method: 'POST', data })
export const listByDish = (dishId: number) => request<any[]>({ url: `/review/dish/${dishId}`, method: 'GET' })
export const reviewAvg = (dishId: number) => request<any>({ url: `/review/dish/${dishId}/avg`, method: 'GET' })
export const dimensions = () => request<any[]>({ url: '/dict?group=review_dimension', method: 'GET' })

/** 多图上传：逐张 POST /file/upload，收集 url 数组。 */
export async function uploadImages(files: string[]): Promise<string[]> {
  const urls: string[] = []
  for (const f of files) {
    const r: any = await new Promise((resolve, reject) => {
      uni.uploadFile({
        url: '/api/file/upload',
        filePath: f,
        name: 'file',
        header: { Authorization: getToken() },
        success: (x) => resolve(JSON.parse(x.data).data),
        fail: reject
      })
    })
    urls.push(r.url)
  }
  return urls
}
