import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

const getOpsStats = vi.fn()
vi.mock('@/api/ops', () => ({ getOpsStats: (...a: unknown[]) => getOpsStats(...a) }))
const getCommunityStats = vi.fn()
vi.mock('@/api/communityAdmin', () => ({ getCommunityStats: () => getCommunityStats() }))
const { useBadgeStore } = await import('./badges')

describe('useBadgeStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    getOpsStats.mockReset()
    getCommunityStats.mockReset()
    getCommunityStats.mockResolvedValue({})
  })

  it('refresh 读取 totals.feedbackOpen', async () => {
    getOpsStats.mockResolvedValue({ totals: { feedbackOpen: 3 } })
    const s = useBadgeStore()
    await s.refresh()
    expect(getOpsStats).toHaveBeenCalledWith(7)
    expect(s.feedbackOpen).toBe(3)
  })

  it('接口失败 / 空数据不抛错，保持 0', async () => {
    getOpsStats.mockRejectedValue(new Error('x'))
    const s = useBadgeStore()
    await s.refresh()
    expect(s.feedbackOpen).toBe(0)
    getOpsStats.mockResolvedValue({})
    await s.refresh()
    expect(s.feedbackOpen).toBe(0)
  })

  it('社区待审数独立拉取，一方失败不影响另一方', async () => {
    getOpsStats.mockRejectedValue(new Error('x'))
    getCommunityStats.mockResolvedValue({ pending: 12 })
    const s = useBadgeStore()
    await s.refresh()
    expect(s.communityPending).toBe(12)
    expect(s.feedbackOpen).toBe(0)
    s.setCommunityPending(-3)
    expect(s.communityPending).toBe(0)
  })

  it('setFeedbackOpen 不会为负', () => {
    const s = useBadgeStore()
    s.setFeedbackOpen(-1)
    expect(s.feedbackOpen).toBe(0)
  })
})
