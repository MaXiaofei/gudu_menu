import { describe, it, expect, beforeEach, vi } from 'vitest'
import { __testUni } from '../setup'
import { HOME_PAGE, resumeIfLoggedIn, trackLaunchAuth } from '@/utils/loginRedirect'
import { PENDING_INVITE_KEY } from '@/utils/family'

// 冷启动已登录不停在登录页（2026-10-27 真机：登录页是 pages.json 第一页，App.vue 有 token 时不跳转）。
describe('登录页 resumeIfLoggedIn', () => {
  beforeEach(() => {
    __testUni.resetStorage()
    vi.mocked(__testUni.mocks.reLaunch).mockClear()
    vi.mocked(__testUni.mocks.switchTab).mockClear()
    trackLaunchAuth(Promise.resolve())
  })

  it('没有登录态：留在登录页，不跳转', async () => {
    expect(await resumeIfLoggedIn('')).toBeNull()
    expect(__testUni.mocks.reLaunch).not.toHaveBeenCalled()
    expect(__testUni.mocks.switchTab).not.toHaveBeenCalled()
  })

  it('已有 token：直接离开登录页去首页', async () => {
    uni.setStorageSync('token', 't')
    uni.setStorageSync('nickname', '小飞')
    expect(await resumeIfLoggedIn('')).toBe(HOME_PAGE)
  })

  it('已有 token 且带分享回跳：回到分享目标', async () => {
    uni.setStorageSync('token', 't')
    expect(await resumeIfLoggedIn('/pages/menu/Detail?id=3')).toBe('/pages/menu/Detail?id=3')
  })

  it('已有 token 且有待加入家庭邀请：去邀请落地页', async () => {
    uni.setStorageSync('token', 't')
    uni.setStorageSync(PENDING_INVITE_KEY, 'INV9')
    expect(await resumeIfLoggedIn('')).toBe('/pkg-family/Invite?code=INV9')
  })

  it('先等启动静默登录结束：结束时才有 token 也能离开登录页', async () => {
    let finish!: () => void
    trackLaunchAuth(new Promise<void>((r) => (finish = r)))
    const p = resumeIfLoggedIn('')
    uni.setStorageSync('token', 'silent')
    uni.setStorageSync('nickname', '小飞')
    finish()
    expect(await p).toBe(HOME_PAGE)
  })
})
