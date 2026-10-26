import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { login as apiLogin, logout as apiLogout, me as apiMe, type LoginDTO, type LoginVO } from '@/api/auth'
import {
  NICKNAME_KEY, TOKEN_KEY, clearAuth, isAdminStored, roleStored, saveAuth, type PlatformRole,
} from '@/utils/auth-storage'

/** 登录成功但账号既不是平台运营也不是超级管理员（后端 scope=admin 兜底之外的前端防线）。 */
export class NoAdminPermissionError extends Error {
  constructor() {
    super('无后台权限')
    this.name = 'NoAdminPermissionError'
  }
}

/** 登录响应 → 平台角色；非运营 / 超管返回 null。 */
export function backofficeRole(data: Pick<LoginVO, 'isAdmin' | 'isOperator' | 'platformRole'> | null | undefined): PlatformRole | null {
  if (!data) return null
  if (data.platformRole === 'SUPER_ADMIN' || data.isAdmin === true) return 'SUPER_ADMIN'
  if (data.platformRole === 'OPERATOR' || data.isOperator === true) return 'OPERATOR'
  return null
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
  const nickname = ref<string>(localStorage.getItem(NICKNAME_KEY) || '')
  /** 可进后台（运营或超管）。 */
  const isAdmin = ref<boolean>(isAdminStored())
  const role = ref<PlatformRole | ''>(roleStored() || (isAdminStored() ? 'SUPER_ADMIN' : ''))
  const isSuperAdmin = computed(() => role.value === 'SUPER_ADMIN')
  const roleLabel = computed(() => (role.value === 'OPERATOR' ? '平台运营' : '超级管理员'))

  function reset() {
    token.value = ''
    nickname.value = ''
    isAdmin.value = false
    role.value = ''
    clearAuth()
  }

  async function login(dto: Omit<LoginDTO, 'scope'>) {
    const data = await apiLogin({ ...dto, scope: 'admin' })
    const r = backofficeRole(data)
    if (!r) {
      reset()
      throw new NoAdminPermissionError()
    }
    token.value = data.token
    nickname.value = data.nickname
    isAdmin.value = true
    role.value = r
    saveAuth(data.token, data.nickname, true, r)
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

  return { token, nickname, isAdmin, role, isSuperAdmin, roleLabel, login, logout, fetchMe }
})
