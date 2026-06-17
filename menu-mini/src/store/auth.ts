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
    },
    logout() {
      this.token = ''
      this.nickname = ''
      uni.removeStorageSync('token')
      uni.reLaunch({ url: '/pages/login/Login' })
    }
  }
})
