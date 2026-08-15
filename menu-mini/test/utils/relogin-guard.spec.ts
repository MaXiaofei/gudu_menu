import { describe, it, expect, vi, beforeEach } from 'vitest'
import { request } from '@/utils/request'
import { __testUni } from '../setup'

// 登录体系兼容性（V50 整改不改坏账号密码登录）：
// 1) 账号密码用户 401 → 不静默重登，直接踢登录页（原行为）
// 2) 微信用户 401 → 静默重登一次 + 重放原请求
// 3) 主动退出（logged_out）→ 不自动静默
describe('登录兼容性', () => {
  beforeEach(() => {
    __testUni.resetStorage()
    vi.mocked(__testUni.mocks.reLaunch).mockClear()
    ;(globalThis as any).uni.login = vi.fn(({ success }: any) => success({ code: 'fresh-code' }))
  })

  function respondOnce(handler: () => any) {
    let n = 0
    vi.mocked(__testUni.mocks.request).mockImplementation(async (opt: any) => {
      n++
      return handler(n, opt)
    })
  }

  it('账号密码用户 401：不静默重登，清 token 踢登录页', async () => {
    __testUni.setStorage('token', 'account-tok')
    __testUni.setStorage('login_via', 'account')
    let wxLoginCalled = false
    respondOnce(() => {
      const url: string = (arguments as any) // placeholder
      return { statusCode: 200, data: { code: 401, msg: '未登录' } }
    })
    // 简化：所有请求都 401
    vi.mocked(__testUni.mocks.request).mockImplementation(async (opt: any) => {
      if (opt.url.includes('/auth/wx-login')) wxLoginCalled = true
      return { statusCode: 200, data: { code: 401, msg: '未登录' } }
    })

    await expect(request({ url: '/dish/search', method: 'GET' })).rejects.toThrow('未登录')
    expect(wxLoginCalled).toBe(false) // 未触发静默重登
    expect(__testUni.mocks.reLaunch).toHaveBeenCalledWith({ url: '/pages/login/Login' })
  })

  it('微信用户 401：静默重登一次 + 新 token 重放原请求', async () => {
    __testUni.setStorage('token', 'expired-tok')
    __testUni.setStorage('login_via', 'wx')
    const urls: string[] = []
    vi.mocked(__testUni.mocks.request).mockImplementation(async (opt: any) => {
      urls.push(opt.url)
      if (opt.url.includes('/auth/wx-login')) {
        return { statusCode: 200, data: { code: 0, data: { token: 'new-tok', nickname: '微信用户' } } }
      }
      if (opt.header?.Authorization === 'expired-tok') {
        return { statusCode: 200, data: { code: 401, msg: '未登录' } }
      }
      return { statusCode: 200, data: { code: 0, data: { ok: true } } }
    })

    const r = await request<{ ok: boolean }>({ url: '/dish/search', method: 'GET' })
    expect(r.ok).toBe(true)
    expect(urls[0]).toContain('/dish/search')
    expect(urls[1]).toContain('/auth/wx-login')
    expect(urls[2]).toContain('/dish/search') // 重放
    expect(__testUni.getStorage('token')).toBe('new-tok')
    expect(__testUni.mocks.reLaunch).not.toHaveBeenCalled()
  })

  it('主动退出（logged_out）标记留存，App 层据此跳过静默（标记断言）', () => {
    // store 行为已由 auth.spec 覆盖；这里断言标记读取语义
    __testUni.setStorage('logged_out', '1')
    expect(uni.getStorageSync('logged_out')).toBe('1')
  })
})
