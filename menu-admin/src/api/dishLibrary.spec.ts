import { describe, it, expect, vi, beforeEach } from 'vitest'

const requestFn = vi.fn()
vi.mock('./request', () => ({
  request: (...args: any[]) => requestFn(...args),
}))

const api = await import('./dishLibrary')

describe('api/dishLibrary 公共菜谱库', () => {
  beforeEach(() => {
    requestFn.mockReset()
    requestFn.mockResolvedValue({})
  })

  it('列表带关键词与上架状态筛选', async () => {
    await api.listLibrary({ keyword: '番茄', status: 'offline', pageNum: 2, pageSize: 15 })
    expect(requestFn).toHaveBeenLastCalledWith({
      url: '/admin/dish-library', method: 'get', params: { keyword: '番茄', status: 'offline', pageNum: 2, pageSize: 15 },
    })
  })

  it('下架必带原因 / 重新上架', async () => {
    await api.offlineDish(7, '图片侵权')
    expect(requestFn).toHaveBeenLastCalledWith({ url: '/admin/dish-library/7/offline', method: 'post', data: { reason: '图片侵权' } })
    await api.onlineDish(7)
    expect(requestFn).toHaveBeenLastCalledWith({ url: '/admin/dish-library/7/online', method: 'post', data: { reason: undefined } })
  })
})
