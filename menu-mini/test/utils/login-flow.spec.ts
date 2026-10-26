import { describe, it, expect, beforeEach, vi } from 'vitest'
import { __testUni } from '../setup'
import { installHttpMock } from '../helpers/http'
import {
  continueFirstRun,
  goAfterJoin,
  goAfterLogin,
  HOME_PAGE,
  launchTarget,
  loginUrl,
  needOnboard,
  ONBOARD_PAGE,
  sanitizeRedirect,
} from '@/utils/loginRedirect'
import { onboardKey, PENDING_INVITE_KEY } from '@/utils/family'
import { PROFILE_PROMPTED_KEY } from '@/utils/profileSetup'
import { silentLogin } from '@/api/auth'
import { setConsent } from '@/utils/consent'

// 登录后统一去向（loginRedirect.goAfterLogin）：待加入家庭邀请 > redirect 回跳 > 首次引导 > 首页；
// 首次引导：头像昵称（A.7）→ 成员档案（Onboard），都可跳过，跳过不再打扰。
describe('登录后流程（家庭邀请 + 首次引导）', () => {
  beforeEach(() => {
    __testUni.resetStorage()
    vi.mocked(__testUni.mocks.reLaunch).mockClear()
    vi.mocked(__testUni.mocks.switchTab).mockClear()
    vi.mocked(__testUni.mocks.navigateTo).mockClear()
  })

  it('家庭邀请落地页在回跳白名单内，登录页地址带回跳', () => {
    expect(sanitizeRedirect('/pkg-family/Invite?code=INV9')).toBe('/pkg-family/Invite?code=INV9')
    expect(loginUrl('/pkg-family/Invite?code=INV9')).toBe(
      '/pages/login/Login?redirect=' + encodeURIComponent('/pkg-family/Invite?code=INV9'),
    )
    expect(sanitizeRedirect('/pkg-family/Index')).toBeNull()
  })

  it('邀请卡片冷启动：launchTarget 返回落地页并记下邀请码；其他页不记', () => {
    expect(launchTarget({ path: 'pages/dish/Detail', query: { id: '1', code: 'X' } })).toBe('/pages/dish/Detail?id=1&code=X')
    expect(__testUni.getStorage(PENDING_INVITE_KEY)).toBeUndefined()
    expect(launchTarget({ path: 'pkg-family/Invite', query: { code: 'AB 12' } })).toBe('/pkg-family/Invite?code=AB%2012')
    expect(__testUni.getStorage(PENDING_INVITE_KEY)).toBe('AB 12')
  })

  it('优先级 1：有待加入邀请 → 回落地页（即使带了别的 redirect）', async () => {
    uni.setStorageSync(PENDING_INVITE_KEY, 'INV9')
    expect(await goAfterLogin('/pages/menu/Detail?id=3', 1)).toBe('/pkg-family/Invite?code=INV9')
    expect(__testUni.mocks.reLaunch).toHaveBeenCalledWith({ url: '/pkg-family/Invite?code=INV9' })
  })

  it('优先级 2：redirect 回跳优先于首次引导', async () => {
    uni.setStorageSync('token', 't')
    uni.setStorageSync('nickname', '微信用户')
    expect(await goAfterLogin('/pages/menu/Detail?id=3', 1)).toBe('/pages/menu/Detail?id=3')
  })

  it('首次引导第 1 步：昵称仍是默认 → 回首页（首页弹头像昵称），此时不查档案、不进 Onboard', async () => {
    uni.setStorageSync('token', 't')
    uni.setStorageSync('nickname', '微信用户')
    const http = installHttpMock({})
    expect(await goAfterLogin(undefined, 1)).toBe(HOME_PAGE)
    expect(http.calls).toHaveLength(0)
    expect(__testUni.mocks.switchTab).toHaveBeenCalledWith({ url: HOME_PAGE })
  })

  it('头像昵称保存后 → 接着进成员档案引导；跳过 → 成员档案引导也不再弹', async () => {
    uni.setStorageSync('gd_account_id', 1)
    installHttpMock({ 'GET /member': { records: [{ id: 7, name: '老张', accountId: 1, healthProfile: null }] } })
    expect(await continueFirstRun(true)).toBe(true)
    expect(__testUni.mocks.navigateTo).toHaveBeenCalledWith({ url: ONBOARD_PAGE })

    __testUni.resetStorage()
    vi.mocked(__testUni.mocks.navigateTo).mockClear()
    uni.setStorageSync('gd_account_id', 1)
    expect(await continueFirstRun(false)).toBe(false)
    expect(__testUni.mocks.navigateTo).not.toHaveBeenCalled()
    expect(uni.getStorageSync(onboardKey(1))).toBe('1')
    expect(await needOnboard(1)).toBe(false)
  })

  it('昵称已改过：本人档案为空 → 直接进成员档案引导；档案已填 → 记已处理回首页', async () => {
    uni.setStorageSync('token', 't')
    uni.setStorageSync('nickname', '老张')
    installHttpMock({ 'GET /member': { records: [{ id: 7, name: '老张', accountId: 1, healthProfile: {} }] } })
    expect(await goAfterLogin(undefined, 1)).toBe(ONBOARD_PAGE)

    __testUni.resetStorage()
    uni.setStorageSync('nickname', '老张')
    installHttpMock({ 'GET /member': { records: [{ id: 7, name: '老张', accountId: 1, healthProfile: { age: 40 } }] } })
    expect(await goAfterLogin(undefined, 1)).toBe(HOME_PAGE)
    expect(uni.getStorageSync(onboardKey(1))).toBe('1')
  })

  it('引导处理过：同步回首页，不查档案', async () => {
    uni.setStorageSync(onboardKey(1), '1')
    const http = installHttpMock({})
    const p = goAfterLogin(undefined, 1)
    expect(__testUni.mocks.switchTab).toHaveBeenCalledWith({ url: HOME_PAGE })
    expect(await p).toBe(HOME_PAGE)
    expect(http.calls).toHaveLength(0)
  })

  it('加入家庭后：昵称是默认先回首页弹头像昵称；否则直接进新家庭的成员档案引导', () => {
    uni.setStorageSync('token', 't')
    uni.setStorageSync('nickname', '微信用户')
    expect(goAfterJoin()).toBe(HOME_PAGE)
    uni.setStorageSync(PROFILE_PROMPTED_KEY, '1')
    expect(goAfterJoin()).toBe(`${ONBOARD_PAGE}?from=join`)
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
