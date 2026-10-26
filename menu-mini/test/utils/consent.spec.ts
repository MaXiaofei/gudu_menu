import { describe, it, expect, vi, beforeEach } from 'vitest'
import { request } from '@/utils/request'
import { hasConsent, setConsent, clearConsent, reportConsent, LEGAL_VERSION, CONSENT_KEY } from '@/utils/consent'
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

  it('B.1 登录成功后上报协议同意：带当前版本与渠道；未勾选不上报；失败不抛', async () => {
    const req = vi.mocked(__testUni.mocks.request)
    req.mockClear()
    expect(await reportConsent()).toBe(false)
    expect(req).not.toHaveBeenCalled()
    setConsent()
    req.mockImplementation(async () => ({ statusCode: 200, data: { code: 0, data: [] } }))
    expect(await reportConsent()).toBe(true)
    const call = req.mock.calls[0][0] as any
    expect(call.url).toMatch(/\/consent$/)
    expect(call.method).toBe('POST')
    expect(call.data).toEqual({ docVersion: LEGAL_VERSION, channel: 'mp-weixin' })
    req.mockImplementation(async () => ({ statusCode: 200, data: { code: 1, msg: 'x' } }))
    expect(await reportConsent()).toBe(false)
  })

  it('F.1 账号被封禁（423）：清 token、弹窗告知原因、回登录页', async () => {
    __testUni.setStorage('token', 'tok')
    vi.mocked(__testUni.mocks.request).mockImplementation(async () => ({
      statusCode: 200, data: { code: 423, msg: '账号已被封禁：刷广告' },
    }))
    vi.mocked(__testUni.mocks.reLaunch).mockClear()
    await expect(request({ url: '/feedback', method: 'POST' })).rejects.toThrow('刷广告')
    expect(uni.getStorageSync('token')).toBeFalsy()
    expect((globalThis as any).uni.showModal).toHaveBeenCalledWith(expect.objectContaining({ content: '账号已被封禁：刷广告' }))
    expect(__testUni.mocks.reLaunch).toHaveBeenCalledWith({ url: '/pages/login/Login' })
  })
})
