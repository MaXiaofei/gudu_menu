import { request } from './request'

export interface LoginDTO {
  username: string
  password: string
}

export interface LoginVO {
  token: string
  nickname: string
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
