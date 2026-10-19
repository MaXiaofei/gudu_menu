import { describe, it, expect, vi, beforeEach } from 'vitest'
import { silentLogin, wxCode } from '@/api/auth'
import { installHttpMock } from '../helpers/http'
import { setConsent, clearConsent } from '@/utils/consent'

// 微信静默登录：wx.login → /auth/wx-login → 存 token（新用户零门槛）。
describe('api/auth 静默登录', () => {
  beforeEach(() => {
    ;(globalThis as any).uni.login = vi.fn(({ success }: any) => success({ code: 'code-123' }))
    setConsent() // 已勾选协议
  })

  it('wxCode：wx.login 拿一次性 code', async () => {
    expect(await wxCode()).toBe('code-123')
  })

  it('silentLogin 成功：code 送后端，token/nickname 落 storage', async () => {
    const http = installHttpMock({ 'POST /auth/wx-login': { token: 'tok-1', nickname: '微信用户' } })
    const r = await silentLogin()
    expect(r).toEqual({ token: 'tok-1', nickname: '微信用户' })
    expect(http.calls[0].data).toEqual({ code: 'code-123' })
    expect(uni.getStorageSync('token')).toBe('tok-1')
  })

  it('silentLogin 失败（wx.login 挂）→ 返回 null，不落 token', async () => {
    ;(globalThis as any).uni.login = vi.fn(({ fail }: any) => fail({ errMsg: 'login:fail' }))
    const r = await silentLogin()
    expect(r).toBeNull()
  })

  it('未勾选协议：不调用 wx.login，也不请求后端（合规）', async () => {
    clearConsent()
    const http = installHttpMock({ 'POST /auth/wx-login': { token: 'tok-x', nickname: 'x' } })
    const r = await silentLogin()
    expect(r).toBeNull()
    expect((globalThis as any).uni.login).not.toHaveBeenCalled()
    expect(http.calls.length).toBe(0)
  })
})
