import { describe, it, expect, beforeEach } from 'vitest'
import { authGuard } from './index'

// 路由守卫：后台页要求 token 且 isAdmin===true（S3.4），否则清理残留登录态回登录页。
describe('router authGuard', () => {
  beforeEach(() => localStorage.clear())

  const home = { path: '/home', meta: {} }

  it('公开页 / 登录页 → 放行', () => {
    expect(authGuard({ path: '/login', meta: { public: true } })).toBe(true)
  })

  it('无 token → 回登录页', () => {
    expect(authGuard(home)).toEqual({ path: '/login' })
  })

  it('有 token 但无 isAdmin（旧版本残留登录态）→ 回登录页并清空', () => {
    localStorage.setItem('gudu-token', 't')
    localStorage.setItem('gudu-nickname', 'n')
    expect(authGuard(home)).toEqual({ path: '/login' })
    expect(localStorage.getItem('gudu-token')).toBeNull()
    expect(localStorage.getItem('gudu-nickname')).toBeNull()
  })

  it('isAdmin=false → 回登录页并清空', () => {
    localStorage.setItem('gudu-token', 't')
    localStorage.setItem('gudu-is-admin', 'false')
    expect(authGuard(home)).toEqual({ path: '/login' })
    expect(localStorage.getItem('gudu-token')).toBeNull()
  })

  it('token + isAdmin=true → 放行', () => {
    localStorage.setItem('gudu-token', 't')
    localStorage.setItem('gudu-is-admin', 'true')
    expect(authGuard(home)).toBe(true)
  })
})
