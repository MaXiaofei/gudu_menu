import { request } from './request'

export interface LoginDTO {
  username: string
  password: string
  /** 'admin'：后台登录，非平台运营 / 超级管理员后端直接返回「无后台权限」 */
  scope?: 'admin'
}

export interface LoginVO {
  token: string
  nickname: string
  /** 超级管理员 */
  isAdmin: boolean
  /** 平台运营及以上（multi-tenant-family F.3：运营可登录后台） */
  isOperator?: boolean
  platformRole?: 'USER' | 'OPERATOR' | 'SUPER_ADMIN'
}

export function login(data: LoginDTO) {
  return request<LoginVO>({ url: '/auth/login', method: 'post', data })
}

export function logout() {
  return request<unknown>({ url: '/auth/logout', method: 'post' })
}

export function me() {
  return request<number | string>({ url: '/auth/me', method: 'get' })
}
