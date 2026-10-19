// 后台登录态的 localStorage 读写（无依赖，供 store / router / request 共用，避免循环依赖）。
export const TOKEN_KEY = 'gudu-token'
export const NICKNAME_KEY = 'gudu-nickname'
export const IS_ADMIN_KEY = 'gudu-is-admin'

export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function isAdminStored(): boolean {
  return localStorage.getItem(IS_ADMIN_KEY) === 'true'
}

export function saveAuth(token: string, nickname: string, isAdmin: boolean): void {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(NICKNAME_KEY, nickname)
  localStorage.setItem(IS_ADMIN_KEY, String(isAdmin))
}

export function clearAuth(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(NICKNAME_KEY)
  localStorage.removeItem(IS_ADMIN_KEY)
}

/** 后台可访问：有 token 且服务端确认是超级管理员。 */
export function hasAdminSession(): boolean {
  return !!getToken() && isAdminStored()
}
