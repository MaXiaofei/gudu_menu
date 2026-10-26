import { describe, it, expect, beforeEach, vi } from 'vitest'
import {
  buildTarget,
  ensureLogin,
  goAfterLogin,
  launchTarget,
  loginUrl,
  resetLoginRedirect,
  sanitizeRedirect,
  trackLaunchAuth,
} from '@/utils/loginRedirect'
import { __testUni } from '../setup'

// A.10 分享落地页登录回跳：只回白名单站内页，防开放跳转
describe('utils/loginRedirect', () => {
  beforeEach(() => {
    __testUni.resetStorage()
    vi.mocked(__testUni.mocks.reLaunch).mockClear()
    vi.mocked(__testUni.mocks.switchTab).mockClear()
    resetLoginRedirect()
    trackLaunchAuth(Promise.resolve())
  })

  it('白名单页面带参数保留（参数重新编码）', () => {
    expect(sanitizeRedirect('/pages/together/Pick?menuId=12&token=abc123')).toBe('/pages/together/Pick?menuId=12&token=abc123')
    expect(sanitizeRedirect('/pages/dish/Detail?id=9')).toBe('/pages/dish/Detail?id=9')
    expect(sanitizeRedirect('/pages/dish/Detail?q=%E7%95%AA%E8%8C%84')).toBe('/pages/dish/Detail?q=%E7%95%AA%E8%8C%84')
    // 整体被 encode 过一次的值
    expect(sanitizeRedirect(encodeURIComponent('/pages/together/Pick?token=t1'))).toBe('/pages/together/Pick?token=t1')
    // Tab 页可回
    expect(sanitizeRedirect('/pages/menu/List')).toBe('/pages/menu/List')
  })

  it('非白名单 / 外链 / 协议 / 路径穿越 / 畸形参数一律拒绝', () => {
    for (const bad of [
      'https://evil.com/pages/dish/Detail',
      '//evil.com/pages/dish/Detail',
      'javascript:alert(1)',
      '/pages/profile/Edit',
      '/pages/login/Login',
      '/pages/dish/Detail/../../profile/Edit',
      '/pages/dish/Detail\\x',
      '/pages/dish/Detail?a-b=1',
      '/pages/dish/Detail?id=' + 'x'.repeat(200),
      'pages/dish/Detail?id=1',
      '',
      null,
      123,
    ]) {
      expect(sanitizeRedirect(bad as any), String(bad)).toBeNull()
    }
  })

  it('loginUrl：合法目标带 redirect，非法目标不带', () => {
    expect(loginUrl('/pages/together/Pick?menuId=1&token=t')).toBe(
      '/pages/login/Login?redirect=' + encodeURIComponent('/pages/together/Pick?menuId=1&token=t'),
    )
    expect(loginUrl('https://evil.com')).toBe('/pages/login/Login')
    expect(loginUrl(null)).toBe('/pages/login/Login')
  })

  it('goAfterLogin：白名单页 reLaunch、Tab 页 switchTab、非法回首页', () => {
    goAfterLogin('/pages/together/Pick?menuId=1&token=t')
    expect(__testUni.mocks.reLaunch).toHaveBeenCalledWith({ url: '/pages/together/Pick?menuId=1&token=t' })
    goAfterLogin('/pages/menu/List')
    expect(__testUni.mocks.switchTab).toHaveBeenCalledWith({ url: '/pages/menu/List' })
    goAfterLogin('https://evil.com')
    expect(__testUni.mocks.switchTab).toHaveBeenLastCalledWith({ url: '/pages/dish/List' })
    goAfterLogin(undefined)
    expect(__testUni.mocks.switchTab).toHaveBeenLastCalledWith({ url: '/pages/dish/List' })
  })

  it('launchTarget：分享卡片进入时取 path + query', () => {
    expect(launchTarget({ path: 'pages/together/Pick', query: { menuId: '3', token: 'tk' } })).toBe(
      '/pages/together/Pick?menuId=3&token=tk',
    )
    expect(launchTarget({ path: 'pages/profile/Edit', query: {} })).toBeNull()
    expect(launchTarget(null)).toBeNull()
    expect(buildTarget('pages/dish/Detail', { id: 5, empty: '' })).toBe('/pages/dish/Detail?id=5')
  })

  it('ensureLogin：等启动登录流程结束；有 token 放行，无 token 带目标跳登录且只跳一次', async () => {
    let finish!: () => void
    trackLaunchAuth(new Promise<void>((r) => (finish = r)))
    const p = ensureLogin('/pages/together/Pick?token=t')
    __testUni.setStorage('token', 'tok') // 静默登录在等待期间完成
    finish()
    expect(await p).toBe(true)
    expect(__testUni.mocks.reLaunch).not.toHaveBeenCalled()

    __testUni.resetStorage()
    expect(await ensureLogin('/pages/together/Pick?token=t')).toBe(false)
    expect(await ensureLogin('/pages/together/Pick?token=t')).toBe(false)
    expect(__testUni.mocks.reLaunch).toHaveBeenCalledTimes(1)
    expect(__testUni.mocks.reLaunch).toHaveBeenCalledWith({
      url: '/pages/login/Login?redirect=' + encodeURIComponent('/pages/together/Pick?token=t'),
    })
  })
})
