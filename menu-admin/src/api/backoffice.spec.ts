import { describe, it, expect, vi, beforeEach } from 'vitest'

const requestFn = vi.fn()
vi.mock('./request', () => ({
  request: (...args: any[]) => requestFn(...args),
}))

const api = await import('./backoffice')

describe('api/backoffice 运营后台（F.1–F.4）', () => {
  beforeEach(() => {
    requestFn.mockReset()
    requestFn.mockResolvedValue({ records: [], total: 0 })
  })

  it('账号列表 / 封禁 / 解封 / 同意记录', async () => {
    await api.listAccounts({ keyword: '小王', bannedOnly: true, pageNum: 1, pageSize: 15 })
    expect(requestFn).toHaveBeenLastCalledWith({
      url: '/admin/accounts', method: 'get', params: { keyword: '小王', bannedOnly: true, pageNum: 1, pageSize: 15 },
    })
    await api.banAccount(5, '刷广告')
    expect(requestFn).toHaveBeenLastCalledWith({ url: '/admin/accounts/5/ban', method: 'post', data: { reason: '刷广告' } })
    await api.unbanAccount(5, '申诉通过')
    expect(requestFn).toHaveBeenLastCalledWith({ url: '/admin/accounts/5/unban', method: 'post', data: { reason: '申诉通过' } })
    await api.accountConsents(5)
    expect(requestFn).toHaveBeenLastCalledWith({ url: '/admin/accounts/5/consents', method: 'get' })
  })

  it('注销记录 / 重试', async () => {
    await api.listDeactivations({ status: 'FAILED', overdue: true })
    expect(requestFn).toHaveBeenLastCalledWith({ url: '/admin/deactivations', method: 'get', params: { status: 'FAILED', overdue: true } })
    await api.retryDeactivation(3)
    expect(requestFn).toHaveBeenLastCalledWith({ url: '/admin/deactivations/3/retry', method: 'post' })
  })

  it('违规复核：图片列表 / 原图 / 恢复 / 确认；文本列表 / 确认；操作日志', async () => {
    await api.listMediaViolations({ review: 'PENDING' })
    expect(requestFn).toHaveBeenLastCalledWith({ url: '/admin/content-violations/media', method: 'get', params: { review: 'PENDING' } })
    await api.mediaViolationImage(7)
    expect(requestFn).toHaveBeenLastCalledWith({ url: '/admin/content-violations/media/7/image', method: 'get' })
    await api.restoreMedia(7, '误判')
    expect(requestFn).toHaveBeenLastCalledWith({ url: '/admin/content-violations/media/7/restore', method: 'post', data: { reason: '误判' } })
    await api.confirmMedia(7)
    expect(requestFn).toHaveBeenLastCalledWith({ url: '/admin/content-violations/media/7/confirm', method: 'post', data: { reason: undefined } })
    await api.listTextViolations({ confirmed: false })
    expect(requestFn).toHaveBeenLastCalledWith({ url: '/admin/content-violations/text', method: 'get', params: { confirmed: false } })
    await api.confirmText(8, '确认')
    expect(requestFn).toHaveBeenLastCalledWith({ url: '/admin/content-violations/text/8/confirm', method: 'post', data: { reason: '确认' } })
    await api.listOperationLogs({ targetType: 'ACCOUNT', targetId: 5 })
    expect(requestFn).toHaveBeenLastCalledWith({ url: '/admin/operation-logs', method: 'get', params: { targetType: 'ACCOUNT', targetId: 5 } })
  })
})
