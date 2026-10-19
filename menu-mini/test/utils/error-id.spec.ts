import { describe, it, expect, vi, beforeEach } from 'vitest'
import { request, formatErrorId, lastErrorId } from '@/utils/request'
import { __testUni } from '../setup'

// 状态规范：请求失败记下 X-Trace-Id，错误态 / 意见反馈展示短编号。
describe('utils/request 报错编号', () => {
  beforeEach(() => __testUni.resetStorage())

  it('formatErrorId：取前 8 位 4-4 分组', () => {
    expect(formatErrorId('7f3a91c2e5d04b1a')).toBe('7f3a-91c2')
    expect(formatErrorId('7F3A-91C2-XYZ')).toBe('7f3a-91c2')
    expect(formatErrorId('')).toBe('')
  })

  it('业务失败时记录 traceId；silent 不弹 toast', async () => {
    vi.mocked(__testUni.mocks.showToast).mockClear()
    vi.mocked(__testUni.mocks.request).mockImplementation(async () => ({
      statusCode: 200,
      header: { 'X-Trace-Id': 'abcd1234ffff' },
      data: { code: 500, msg: '服务器开小差' },
    }))
    await expect(request({ url: '/pantry/summary', method: 'GET', silent: true })).rejects.toThrow()
    expect(lastErrorId()).toBe('abcd-1234')
    expect(__testUni.mocks.showToast).not.toHaveBeenCalled()
  })
})
