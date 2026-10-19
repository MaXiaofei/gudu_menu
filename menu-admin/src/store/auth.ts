import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as apiLogin, logout as apiLogout, me as apiMe, type LoginDTO } from '@/api/auth'
import { NICKNAME_KEY, TOKEN_KEY, clearAuth, isAdminStored, saveAuth } from '@/utils/auth-storage'

/** 登录成功但账号不是超级管理员（后端 scope=admin 兜底之外的前端防线）。 */
export class NoAdminPermissionError extends Error {
  constructor() {
    super('无后台权限')
    this.name = 'NoAdminPermissionError'
  }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
  const nickname = ref<string>(localStorage.getItem(NICKNAME_KEY) || '')
  const isAdmin = ref<boolean>(isAdminStored())

  function reset() {
    token.value = ''
    nickname.value = ''
    isAdmin.value = false
    clearAuth()
  }

  async function login(dto: Omit<LoginDTO, 'scope'>) {
    const data = await apiLogin({ ...dto, scope: 'admin' })
    if (data?.isAdmin !== true) {
      reset()
      throw new NoAdminPermissionError()
    }
    token.value = data.token
    nickname.value = data.nickname
    isAdmin.value = true
    saveAuth(data.token, data.nickname, true)
    return data
  }

  async function logout() {
    try {
      await apiLogout()
    } catch {
      // 即使后端报错也继续清理本地态
    }
    reset()
  }

  async function fetchMe() {
    const id = await apiMe()
    return id
  }

  return { token, nickname, isAdmin, login, logout, fetchMe }
})
