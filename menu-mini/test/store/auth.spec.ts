import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { __testUni } from '../setup'

// store/auth：login 写 token 到 state + uni storage；logout 清理 + reLaunch。
// request 依赖 utils/request（依赖 uni.request），整体 mock 掉。
vi.mock('@/utils/request', () => ({
  request: vi.fn(),
}))
const { request } = await import('@/utils/request')
const { useAuthStore } = await import('@/store/auth')

describe('store/auth', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    __testUni.resetStorage()
    vi.mocked(request).mockReset()
    vi.mocked(__testUni.mocks.reLaunch).mockClear()
  })

  describe('login', () => {
    it('成功 → 写 token/nickname 到 state 和 uni storage', async () => {
      vi.mocked(request).mockResolvedValue({ token: 'tok1', nickname: '张爸爸' } as any)
      const store = useAuthStore()

      await store.login('admin', '123')

      expect(store.token).toBe('tok1')
      expect(store.nickname).toBe('张爸爸')
      expect(__testUni.getStorage('token')).toBe('tok1')
    })

    it('透传 username/password 到 request', async () => {
      vi.mocked(request).mockResolvedValue({ token: 't', nickname: 'n' } as any)
      const store = useAuthStore()

      await store.login('u', 'p')

      expect(request).toHaveBeenCalledWith({
        url: '/auth/login',
        method: 'POST',
        data: { username: 'u', password: 'p' },
      })
    })
  })

  describe('logout', () => {
    it('清空 state + storage + reLaunch 回游客态首页', () => {
      const store = useAuthStore()
      store.token = 'tok'
      store.nickname = 'n'
      __testUni.setStorage('token', 'tok')

      store.logout()

      expect(store.token).toBe('')
      expect(store.nickname).toBe('')
      expect(__testUni.getStorage('token')).toBeUndefined()
      expect(__testUni.mocks.reLaunch).toHaveBeenCalledWith({ url: '/pages/dish/List' })
    })
  })

  describe('初始状态', () => {
    it('uni storage 有 token 时恢复', () => {
      __testUni.setStorage('token', 'restored')
      const store = useAuthStore()
      expect(store.token).toBe('restored')
    })

    it('uni storage 无 token 时为空串', () => {
      const store = useAuthStore()
      expect(store.token).toBe('')
    })
  })
})
