import { describe, it, expect, vi, beforeEach } from 'vitest'
import { request } from '@/utils/request'
import { hasConsent, setConsent, clearConsent, LEGAL_VERSION, CONSENT_KEY } from '@/utils/consent'
import { __testUni } from '../setup'

// prototype-ui-rollout 1.16：勾选协议前不发起任何登录（含 401 静默自愈的 wx.login）。
describe('utils/consent 协议同意', () => {
  beforeEach(() => {
    __testUni.resetStorage()
    ;(globalThis as any).uni.login = vi.fn(({ success }: any) => success({ code: 'c' }))
  })

  it('同意记录带协议版本；版本不符视为未同意', () => {
    expect(hasConsent()).toBe(false)
    setConsent()
    expect(uni.getStorageSync(CONSENT_KEY)).toBe(LEGAL_VERSION)
    expect(hasConsent()).toBe(true)
    __testUni.setStorage(CONSENT_KEY, '2020-01')
    expect(hasConsent()).toBe(false)
    setConsent()
    clearConsent()
    expect(hasConsent()).toBe(false)
  })

  it('未同意的微信用户 401：不调用 wx.login，直接回登录页', async () => {
    __testUni.setStorage('token', 'old')
    __testUni.setStorage('login_via', 'wx')
    vi.mocked(__testUni.mocks.request).mockImplementation(async () => ({ statusCode: 200, data: { code: 401, msg: '未登录' } }))
    vi.mocked(__testUni.mocks.reLaunch).mockClear()
    await expect(request({ url: '/dish/search', method: 'GET' })).rejects.toThrow('未登录')
    expect((globalThis as any).uni.login).not.toHaveBeenCalled()
    expect(__testUni.mocks.reLaunch).toHaveBeenCalledWith({ url: '/pages/login/Login' })
  })
})
