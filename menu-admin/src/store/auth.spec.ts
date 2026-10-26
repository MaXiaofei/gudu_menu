import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// mock @/api/auth：可控 login/logout/me 的返回与抛错
const apiLogin = vi.fn()
const apiLogout = vi.fn()
const apiMe = vi.fn()
vi.mock('@/api/auth', () => ({
  login: (...args: any[]) => apiLogin(...args),
  logout: (...args: any[]) => apiLogout(...args),
  me: (...args: any[]) => apiMe(...args),
}))

const { useAuthStore, NoAdminPermissionError } = await import('./auth')

describe('useAuthStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    apiLogin.mockReset()
    apiLogout.mockReset()
    apiMe.mockReset()
  })

  describe('login', () => {
    it('成功 → 写 token/nickname/isAdmin 到 state 和 localStorage', async () => {
      apiLogin.mockResolvedValue({ token: 'tok1', nickname: '张爸爸', isAdmin: true })
      const store = useAuthStore()

      await store.login({ username: 'admin', password: '123' })

      expect(store.token).toBe('tok1')
      expect(store.nickname).toBe('张爸爸')
      expect(localStorage.getItem('gudu-token')).toBe('tok1')
      expect(localStorage.getItem('gudu-nickname')).toBe('张爸爸')
      expect(store.isAdmin).toBe(true)
      expect(localStorage.getItem('gudu-is-admin')).toBe('true')
    })

    it('透传 dto 给 apiLogin，并固定带 scope=admin', async () => {
      apiLogin.mockResolvedValue({ token: 't', nickname: 'n', isAdmin: true })
      const store = useAuthStore()
      await store.login({ username: 'u', password: 'p' })
      expect(apiLogin).toHaveBeenCalledWith({ username: 'u', password: 'p', scope: 'admin' })
    })

    it('平台运营（isOperator / platformRole=OPERATOR）可登录，角色记为 OPERATOR', async () => {
      apiLogin.mockResolvedValue({ token: 't', nickname: '运营小李', isAdmin: false, isOperator: true, platformRole: 'OPERATOR' })
      const store = useAuthStore()
      await store.login({ username: 'u', password: 'p' })
      expect(store.isAdmin).toBe(true)
      expect(store.role).toBe('OPERATOR')
      expect(store.isSuperAdmin).toBe(false)
      expect(store.roleLabel).toBe('平台运营')
      expect(localStorage.getItem('gudu-role')).toBe('OPERATOR')
    })

    it('返回 isAdmin!==true → 抛 NoAdminPermissionError 且不落登录态', async () => {
      apiLogin.mockResolvedValue({ token: 't', nickname: 'n', isAdmin: false })
      const store = useAuthStore()
      await expect(store.login({ username: 'u', password: 'p' })).rejects.toBeInstanceOf(NoAdminPermissionError)
      expect(store.token).toBe('')
      expect(store.isAdmin).toBe(false)
      expect(localStorage.getItem('gudu-token')).toBeNull()
      expect(localStorage.getItem('gudu-is-admin')).toBeNull()
    })

    it('后端拒绝（无后台权限）→ 透传错误，不落登录态', async () => {
      apiLogin.mockRejectedValue(new Error('无后台权限'))
      const store = useAuthStore()
      await expect(store.login({ username: 'u', password: 'p' })).rejects.toThrow('无后台权限')
      expect(localStorage.getItem('gudu-token')).toBeNull()
    })
  })

  describe('logout', () => {
    it('成功 → 清空 state 和 localStorage', async () => {
      apiLogout.mockResolvedValue(undefined)
      const store = useAuthStore()
      store.token = 'tok'
      store.nickname = 'n'
      localStorage.setItem('gudu-token', 'tok')
      localStorage.setItem('gudu-nickname', 'n')
      localStorage.setItem('gudu-is-admin', 'true')

      await store.logout()

      expect(localStorage.getItem('gudu-is-admin')).toBeNull()
      expect(store.isAdmin).toBe(false)
      expect(store.token).toBe('')
      expect(store.nickname).toBe('')
      expect(localStorage.getItem('gudu-token')).toBeNull()
      expect(localStorage.getItem('gudu-nickname')).toBeNull()
    })

    it('apiLogout 抛错 → 仍清本地态（容错分支）', async () => {
      apiLogout.mockRejectedValue(new Error('网络错误'))
      const store = useAuthStore()
      store.token = 'tok'
      store.nickname = 'n'
      localStorage.setItem('gudu-token', 'tok')
      localStorage.setItem('gudu-nickname', 'n')

      // 即使后端 logout 报错，本地态也必须被清理（不能残留登录态）
      await store.logout()

      expect(store.token).toBe('')
      expect(store.nickname).toBe('')
      expect(localStorage.getItem('gudu-token')).toBeNull()
      expect(localStorage.getItem('gudu-nickname')).toBeNull()
    })
  })

  describe('fetchMe', () => {
    it('透传调用 apiMe 并返回 id', async () => {
      apiMe.mockResolvedValue(1)
      const store = useAuthStore()
      const id = await store.fetchMe()
      expect(id).toBe(1)
      expect(apiMe).toHaveBeenCalled()
    })
  })

  describe('初始状态', () => {
    it('localStorage 有 token 时初始化恢复', () => {
      localStorage.setItem('gudu-token', 'restored')
      localStorage.setItem('gudu-nickname', 'restored-nick')
      localStorage.setItem('gudu-is-admin', 'true')
      const store = useAuthStore()
      expect(store.isAdmin).toBe(true)
      expect(store.token).toBe('restored')
      expect(store.nickname).toBe('restored-nick')
    })

    it('localStorage 无 token 时初始为空串', () => {
      const store = useAuthStore()
      expect(store.token).toBe('')
      expect(store.nickname).toBe('')
    })
  })
})
