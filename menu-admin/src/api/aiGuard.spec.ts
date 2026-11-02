import { describe, it, expect, vi, beforeEach } from 'vitest'

const requestFn = vi.fn()
vi.mock('./request', () => ({
  request: (...args: any[]) => requestFn(...args),
}))

const { getAiGuard, updateAiGuard } = await import('./aiGuard')

describe('api/aiGuard AI 用量与额度', () => {
  beforeEach(() => {
    requestFn.mockReset()
    requestFn.mockResolvedValue(undefined)
  })

  it('getAiGuard → GET /admin/ai-guard', async () => {
    await getAiGuard()
    expect(requestFn).toHaveBeenCalledWith({ url: '/admin/ai-guard', method: 'get' })
  })

  it('updateAiGuard → PUT，只带传入的字段', async () => {
    await updateAiGuard({ dailyBudgetYuan: 30, limits: { vision: 25 } })
    expect(requestFn).toHaveBeenCalledWith({
      url: '/admin/ai-guard',
      method: 'put',
      data: { dailyBudgetYuan: 30, limits: { vision: 25 } },
    })
  })
})
