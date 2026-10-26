import { defineStore } from 'pinia'
import { request } from '@/utils/request'
import { applyLoginResult, clearLocalSession, type LoginResult } from '@/utils/family'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: uni.getStorageSync('token') || '',
    nickname: ''
  }),
  actions: {
    async login(username: string, password: string): Promise<LoginResult> {
      const r = await request<LoginResult>({
        url: '/auth/login',
        method: 'POST',
        data: { username, password }
      })
      this.token = r.token
      this.nickname = r.nickname || ''
      applyLoginResult(r) // token + 家庭上下文（families / currentFamilyId）
      uni.setStorageSync('login_via', 'account') // 账号用户 401 不静默重登（防串号到微信新号）
      return r
    },
    /** 合并账号后用新 token 替换（storage 已由 api/account 写入）。 */
    replaceToken(token: string, nickname?: string) {
      this.token = token
      if (nickname !== undefined) this.nickname = nickname
    },
    logout() {
      this.token = ''
      this.nickname = ''
      clearLocalSession()
      uni.setStorageSync('logged_out', '1') // 主动退出：下次启动不再自动静默登录
      uni.reLaunch({ url: '/pages/login/Login' })
    }
  }
})
