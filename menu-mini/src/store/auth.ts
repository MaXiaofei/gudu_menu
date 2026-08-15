import { defineStore } from 'pinia'
import { request } from '@/utils/request'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: uni.getStorageSync('token') || '',
    nickname: ''
  }),
  actions: {
    async login(username: string, password: string) {
      const r = await request<any>({
        url: '/auth/login',
        method: 'POST',
        data: { username, password }
      })
      this.token = r.token
      this.nickname = r.nickname
      uni.setStorageSync('token', r.token)
      uni.setStorageSync('login_via', 'account') // 账号用户 401 不静默重登（防串号到微信新号）
      uni.removeStorageSync('logged_out')
    },
    logout() {
      this.token = ''
      this.nickname = ''
      uni.removeStorageSync('token')
      uni.removeStorageSync('login_via')
      uni.setStorageSync('logged_out', '1') // 主动退出：下次启动不再自动静默登录
      uni.reLaunch({ url: '/pages/login/Login' })
    }
  }
})
