import { describe, it, expect, vi, beforeEach } from 'vitest'

const requestFn = vi.fn()
vi.mock('./request', () => ({
  request: (...args: any[]) => requestFn(...args),
}))

const { getOpsStats } = await import('./ops')

describe('api/ops 运营统计', () => {
  beforeEach(() => {
    requestFn.mockReset()
    requestFn.mockResolvedValue({})
  })

  it('getOpsStats 默认 days=14 → GET /ops/stats（走 params）', async () => {
    await getOpsStats()
    expect(requestFn).toHaveBeenCalledWith({ url: '/ops/stats', method: 'get', params: { days: 14 } })
  })

  it('getOpsStats 透传自定义 days', async () => {
    await getOpsStats(30)
    expect(requestFn).toHaveBeenCalledWith({ url: '/ops/stats', method: 'get', params: { days: 30 } })
  })
})
