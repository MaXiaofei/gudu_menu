import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as apiLogin, logout as apiLogout, me as apiMe, type LoginDTO } from '@/api/auth'

const TOKEN_KEY = 'yanhuo-token'
const NICKNAME_KEY = 'yanhuo-nickname'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
  const nickname = ref<string>(localStorage.getItem(NICKNAME_KEY) || '')

  async function login(dto: LoginDTO) {
    const data = await apiLogin(dto)
    token.value = data.token
    nickname.value = data.nickname
    localStorage.setItem(TOKEN_KEY, data.token)
    localStorage.setItem(NICKNAME_KEY, data.nickname)
    return data
  }

  async function logout() {
    try {
      await apiLogout()
    } catch {
      // 即使后端报错也继续清理本地态
    }
    token.value = ''
    nickname.value = ''
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(NICKNAME_KEY)
  }

  async function fetchMe() {
    const id = await apiMe()
    return id
  }

  return { token, nickname, login, logout, fetchMe }
})
