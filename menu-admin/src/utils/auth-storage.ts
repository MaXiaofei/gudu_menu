// 后台登录态的 localStorage 读写（无依赖，供 store / router / request 共用，避免循环依赖）。
export const TOKEN_KEY = 'gudu-token'
export const NICKNAME_KEY = 'gudu-nickname'
/** 可进后台（平台运营 OPERATOR 或超级管理员 SUPER_ADMIN，multi-tenant-family F.3）。键名沿用旧值。 */
export const IS_ADMIN_KEY = 'gudu-is-admin'
/** 平台角色：OPERATOR / SUPER_ADMIN（决定超管专属页面是否可见）。 */
export const ROLE_KEY = 'gudu-role'

export type PlatformRole = 'OPERATOR' | 'SUPER_ADMIN'

export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function isAdminStored(): boolean {
  return localStorage.getItem(IS_ADMIN_KEY) === 'true'
}

export function roleStored(): PlatformRole | '' {
  const r = localStorage.getItem(ROLE_KEY)
  return r === 'OPERATOR' || r === 'SUPER_ADMIN' ? r : ''
}

/** 超级管理员（旧登录态未存角色时按超管处理：旧版后台只允许超管登录）。 */
export function isSuperAdminStored(): boolean {
  const r = roleStored()
  return r === 'SUPER_ADMIN' || (r === '' && isAdminStored())
}

export function saveAuth(token: string, nickname: string, isAdmin: boolean, role?: PlatformRole): void {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(NICKNAME_KEY, nickname)
  localStorage.setItem(IS_ADMIN_KEY, String(isAdmin))
  if (role) localStorage.setItem(ROLE_KEY, role)
  else localStorage.removeItem(ROLE_KEY)
}

export function clearAuth(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(NICKNAME_KEY)
  localStorage.removeItem(IS_ADMIN_KEY)
  localStorage.removeItem(ROLE_KEY)
}

/** 后台可访问：有 token 且服务端确认是平台运营或超级管理员。 */
export function hasAdminSession(): boolean {
  return !!getToken() && isAdminStored()
}
