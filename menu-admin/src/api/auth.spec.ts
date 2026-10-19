import { describe, it, expect, vi, beforeEach } from 'vitest'

// api/auth 是对 request() 的薄封装，验证 url/method 透传正确。
const requestFn = vi.fn()
vi.mock('./request', () => ({
  request: (...args: any[]) => requestFn(...args),
}))

const { login, logout, me } = await import('./auth')

describe('api/auth 接口封装', () => {
  beforeEach(() => {
    requestFn.mockReset()
    requestFn.mockResolvedValue({})
  })

  it('login → POST /auth/login，body 透传', async () => {
    const dto = { username: 'admin', password: '123' }
    await login(dto)
    expect(requestFn).toHaveBeenCalledWith({
      url: '/auth/login',
      method: 'post',
      data: dto,
    })
  })

  it('login 带 scope=admin 原样透传', async () => {
    const dto = { username: 'admin', password: '123', scope: 'admin' as const }
    await login(dto)
    expect(requestFn).toHaveBeenCalledWith({ url: '/auth/login', method: 'post', data: dto })
  })

  it('logout → POST /auth/logout', async () => {
    await logout()
    expect(requestFn).toHaveBeenCalledWith({
      url: '/auth/logout',
      method: 'post',
    })
  })

  it('me → GET /auth/me', async () => {
    await me()
    expect(requestFn).toHaveBeenCalledWith({
      url: '/auth/me',
      method: 'get',
    })
  })

  it('login 返回值透传 request 结果', async () => {
    const vo = { token: 't', nickname: 'n', isAdmin: true }
    requestFn.mockResolvedValue(vo)
    const r = await login({ username: 'u', password: 'p' })
    expect(r).toEqual(vo)
  })
})
