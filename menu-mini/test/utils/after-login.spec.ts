import { describe, it, expect, beforeEach, vi } from 'vitest'
import { __testUni } from '../setup'
import { installHttpMock } from '../helpers/http'
import { needOnboard, routeAfterLogin, safeRedirect, ONBOARD_PAGE, HOME_PAGE } from '@/utils/afterLogin'
import { onboardKey, PENDING_INVITE_KEY } from '@/utils/family'
import { silentLogin } from '@/api/auth'
import { setConsent } from '@/utils/consent'

// 登录后去向：待加入邀请 > redirect > 首次登录引导 > 首页。
describe('utils/afterLogin', () => {
  beforeEach(() => {
    __testUni.resetStorage()
    vi.mocked(__testUni.mocks.reLaunch).mockClear()
    vi.mocked(__testUni.mocks.switchTab).mockClear()
  })

  it('有待加入邀请 → 回邀请落地页', async () => {
    uni.setStorageSync(PENDING_INVITE_KEY, 'INV9')
    const url = await routeAfterLogin({ token: 't', accountId: 1 }, '/pages/menu/List')
    expect(url).toBe('/pkg-family/Invite?code=INV9')
    expect(__testUni.mocks.reLaunch).toHaveBeenCalledWith({ url: '/pkg-family/Invite?code=INV9' })
  })

  it('redirect 为站内页 → 回跳', async () => {
    uni.setStorageSync(onboardKey(1), '1')
    const url = await routeAfterLogin({ token: 't', accountId: 1 }, encodeURIComponent('/pages/menu/Detail?id=3'))
    expect(url).toBe('/pages/menu/Detail?id=3')
  })

  it('safeRedirect 拒绝外链与协议跳转', () => {
    expect(safeRedirect('https://evil.com')).toBe('')
    expect(safeRedirect('//evil.com/pages/x')).toBe('')
    expect(safeRedirect('javascript:alert(1)')).toBe('')
    expect(safeRedirect('/pkg-family/Invite?code=A')).toBe('/pkg-family/Invite?code=A')
  })

  it('首次登录：本人档案没填 → 引导页；填过 → 记标记直接首页', async () => {
    installHttpMock({ 'GET /member': { records: [{ id: 7, name: '微信用户', accountId: 1, healthProfile: null }] } })
    expect(await routeAfterLogin({ token: 't', accountId: 1 })).toBe(ONBOARD_PAGE)

    __testUni.resetStorage()
    installHttpMock({ 'GET /member': { records: [{ id: 7, name: '老张', accountId: 1, healthProfile: { age: 40 } }] } })
    expect(await routeAfterLogin({ token: 't', accountId: 1 })).toBe(HOME_PAGE)
    expect(uni.getStorageSync(onboardKey(1))).toBe('1')
    expect(__testUni.mocks.switchTab).toHaveBeenCalledWith({ url: HOME_PAGE })
  })

  it('引导处理过（含跳过）不再弹', async () => {
    uni.setStorageSync(onboardKey(1), '1')
    const http = installHttpMock({})
    expect(await needOnboard(1)).toBe(false)
    expect(http.calls).toHaveLength(0)
  })

  it('静默登录带上待加入邀请码（新微信用户直接进邀请家庭），并保存家庭上下文', async () => {
    setConsent()
    ;(globalThis as any).uni.login = vi.fn(({ success }: any) => success({ code: 'c1' }))
    uni.setStorageSync(PENDING_INVITE_KEY, 'INV9')
    const http = installHttpMock({
      'POST /auth/wx-login': { token: 'tk', accountId: 2, families: [{ id: 5, name: '小姨家', role: 'MEMBER', memberCount: 2 }], currentFamilyId: 5 },
    })
    await silentLogin()
    expect(http.calls[0].data).toEqual({ code: 'c1', inviteCode: 'INV9' })
    expect(uni.getStorageSync('gd_current_family')).toBe(5)
  })
})
