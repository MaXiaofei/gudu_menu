import { describe, it, expect, vi, beforeEach } from 'vitest'

const requestFn = vi.fn()
vi.mock('./request', () => ({
  request: (...args: any[]) => requestFn(...args),
}))

const { listFeedback, updateFeedbackStatus, replyFeedback } = await import('./feedback')

describe('api/feedback A.11 筛选与回复', () => {
  beforeEach(() => {
    requestFn.mockReset()
    requestFn.mockResolvedValue(undefined)
  })

  it('listFeedback 带 type → 透传 type', async () => {
    await listFeedback({ type: 'ai', status: 'REPLIED', pageNum: 1, pageSize: 15 })
    expect(requestFn).toHaveBeenCalledWith({
      url: '/feedback',
      method: 'get',
      params: { type: 'ai', status: 'REPLIED', pageNum: 1, pageSize: 15 },
    })
  })

  it('replyFeedback → PUT /feedback/{id}/reply，内容 trim，默认不标记处理', async () => {
    await replyFeedback(7, '  已修复  ')
    expect(requestFn).toHaveBeenCalledWith({ url: '/feedback/7/reply', method: 'put', data: { reply: '已修复', markDone: false } })
    await replyFeedback(7, '好的', true)
    expect(requestFn).toHaveBeenLastCalledWith({ url: '/feedback/7/reply', method: 'put', data: { reply: '好的', markDone: true } })
  })
})

describe('api/feedback 意见反馈管理', () => {
  beforeEach(() => {
    requestFn.mockReset()
    requestFn.mockResolvedValue({ records: [], total: 0 })
  })

  it('listFeedback 带 status → GET /feedback 分页 + status', async () => {
    await listFeedback({ status: 'NEW', pageNum: 2, pageSize: 15 })
    expect(requestFn).toHaveBeenCalledWith({
      url: '/feedback',
      method: 'get',
      params: { status: 'NEW', pageNum: 2, pageSize: 15 },
    })
  })

  it('listFeedback status 为空串 → 不传 status（全部）', async () => {
    await listFeedback({ status: '', pageNum: 1, pageSize: 15 })
    expect(requestFn).toHaveBeenCalledWith({ url: '/feedback', method: 'get', params: { pageNum: 1, pageSize: 15 } })
  })

  it('updateFeedbackStatus → PUT /feedback/{id}/status', async () => {
    await updateFeedbackStatus(7, 'DONE')
    expect(requestFn).toHaveBeenCalledWith({ url: '/feedback/7/status', method: 'put', data: { status: 'DONE' } })
    await updateFeedbackStatus(7, 'NEW')
    expect(requestFn).toHaveBeenLastCalledWith({ url: '/feedback/7/status', method: 'put', data: { status: 'NEW' } })
  })
})
