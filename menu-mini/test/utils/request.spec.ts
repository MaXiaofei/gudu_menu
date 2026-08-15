import { describe, it, expect, vi, beforeEach } from 'vitest'
import { __testUni } from '../setup'
import { request, BASE } from '@/utils/request'

// utils/request：统一解包 R{code,msg,data}。
// 401 清 token+reLaunch；code!=0 toast+抛错；正常返 data。
// uni.* 由 setup.ts 全局 mock。
describe('utils/request', () => {
  beforeEach(() => {
    __testUni.resetStorage()
    vi.mocked(__testUni.mocks.reLaunch).mockClear()
    vi.mocked(__testUni.mocks.showToast).mockClear()
    vi.mocked(__testUni.mocks.request).mockReset()
  })

  it('code==0 → 返回 data', async () => {
    vi.mocked(__testUni.mocks.request).mockResolvedValue({
      data: { code: 0, msg: 'ok', data: { id: 1 } },
      statusCode: 200,
    } as any)

    const r = await request({ url: '/x', method: 'GET' })

    expect(r).toEqual({ id: 1 })
    // 请求头带 Authorization（空 token 时不带，但 header 仍构造）
    expect(__testUni.mocks.request).toHaveBeenCalledWith(
      expect.objectContaining({
        url: BASE + '/x',
        header: expect.any(Object),
      }),
    )
  })

  it('有 token 时请求头带 Authorization', async () => {
    __testUni.setStorage('token', 'tok123')
    vi.mocked(__testUni.mocks.request).mockResolvedValue({
      data: { code: 0, data: null },
      statusCode: 200,
    } as any)

    await request({ url: '/y', method: 'GET' })

    expect(__testUni.mocks.request).toHaveBeenCalledWith(
      expect.objectContaining({
        header: expect.objectContaining({ Authorization: 'tok123' }),
      }),
    )
  })

  it('code==401 → 清 token + reLaunch 到登录页 + reject', async () => {
    __testUni.setStorage('token', 'abc')
    vi.mocked(__testUni.mocks.request).mockResolvedValue({
      data: { code: 401, msg: '未登录' },
      statusCode: 200,
    } as any)

    await expect(request({ url: '/x', method: 'GET' })).rejects.toThrow('未登录')
    expect(__testUni.getStorage('token')).toBeUndefined()
    expect(__testUni.mocks.reLaunch).toHaveBeenCalledWith({ url: '/pages/login/Login' })
  })

  it('其它非 0 code → toast + reject', async () => {
    vi.mocked(__testUni.mocks.request).mockResolvedValue({
      data: { code: 500, msg: '服务器错误' },
      statusCode: 200,
    } as any)

    await expect(request({ url: '/x', method: 'GET' })).rejects.toThrow('服务器错误')
    expect(__testUni.mocks.showToast).toHaveBeenCalledWith(
      expect.objectContaining({ title: '服务器错误', icon: 'none' }),
    )
    // 未登录场景才 reLaunch，业务错误不 reLaunch
    expect(__testUni.mocks.reLaunch).not.toHaveBeenCalled()
  })

  it('url 拼接 /gudu 前缀', async () => {
    vi.mocked(__testUni.mocks.request).mockResolvedValue({
      data: { code: 0, data: null },
      statusCode: 200,
    } as any)

    await request({ url: '/auth/login', method: 'POST' })

    expect(__testUni.mocks.request).toHaveBeenCalledWith(
      expect.objectContaining({ url: BASE + '/auth/login' }),
    )
  })
})
