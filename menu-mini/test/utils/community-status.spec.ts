import { describe, it, expect, vi, beforeEach } from 'vitest'
import { __testUni } from '../setup'
import { request } from '@/utils/request'
import { communityStatus, isCommunityPath, noteCommunityResponse } from '@/utils/communityStatus'

// A.12 厨房圈紧急关停：5031 → 暂停提示（不弹报错），厨房圈请求恢复成功后自动解除
describe('utils/communityStatus', () => {
  beforeEach(() => {
    communityStatus.suspended = false
    vi.mocked(__testUni.mocks.showToast).mockClear()
    vi.mocked(__testUni.mocks.request).mockReset()
  })

  const reply = (code: number, msg = 'ok', data: any = null) =>
    vi.mocked(__testUni.mocks.request).mockResolvedValueOnce({ statusCode: 200, data: { code, msg, data } } as any)

  it('厨房圈接口返回 5031：置暂停、抛「厨房圈暂停服务」、不弹 toast', async () => {
    reply(5031, '厨房圈暂停服务')
    await expect(request({ url: '/community/feed', method: 'GET' })).rejects.toThrow('厨房圈暂停服务')
    expect(communityStatus.suspended).toBe(true)
    expect(__testUni.mocks.showToast).not.toHaveBeenCalled()
  })

  it('恢复后厨房圈请求成功：解除暂停；非厨房圈请求不影响状态', async () => {
    communityStatus.suspended = true
    reply(0, 'ok', { id: 1 })
    await request({ url: '/dish/1', method: 'GET' })
    expect(communityStatus.suspended).toBe(true)
    reply(0, 'ok', { items: [], nextCursor: null })
    await request({ url: '/community/feed', method: 'GET' })
    expect(communityStatus.suspended).toBe(false)
  })

  it('路径判断与其他业务码', () => {
    expect(isCommunityPath('/community/posts/1')).toBe(true)
    expect(isCommunityPath('/community?x=1')).toBe(true)
    expect(isCommunityPath('/admin/community/queue')).toBe(false)
    expect(isCommunityPath('/communityx')).toBe(false)
    communityStatus.suspended = true
    noteCommunityResponse('/community/feed', 1) // 普通业务失败不改变暂停状态
    expect(communityStatus.suspended).toBe(true)
  })
})
