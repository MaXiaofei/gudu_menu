import { describe, it, expect, vi, beforeEach } from 'vitest'

const requestFn = vi.fn()
vi.mock('./request', () => ({
  request: (...args: any[]) => requestFn(...args),
}))

const { listFeedback, updateFeedbackStatus } = await import('./feedback')

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
