import { describe, it, expect, vi, beforeEach } from 'vitest'

const requestFn = vi.fn()
vi.mock('./request', () => ({
  request: (...args: any[]) => requestFn(...args),
}))

const { getOpsStats, getOpsHealth, rebuildDishVectors } = await import('./ops')

describe('api/ops 系统健康', () => {
  beforeEach(() => {
    requestFn.mockReset()
    requestFn.mockResolvedValue({})
  })

  it('getOpsHealth → GET /ops/health', async () => {
    await getOpsHealth()
    expect(requestFn).toHaveBeenCalledWith({ url: '/ops/health', method: 'get' })
  })

  it('rebuildDishVectors → POST /dish/vector/rebuild 且放宽超时', async () => {
    await rebuildDishVectors()
    expect(requestFn).toHaveBeenCalledWith({ url: '/dish/vector/rebuild', method: 'post', timeout: 600000 })
  })
})

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
