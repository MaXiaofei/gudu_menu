import { request } from './request'

export interface HealthProfile {
  height?: number
  weight?: number
  age?: number
  gender?: string
  audiences?: string[]
  allergies?: string[]
  sugarMax?: number
  saltMax?: number
  [key: string]: unknown
}

export interface Member {
  id: number
  name: string
  roleTags: string[]
  healthProfile: HealthProfile
}

export interface MemberSaveDTO {
  id?: number
  name: string
  roleTags: string[]
  healthProfile: HealthProfile
}

export function listMembers() {
  return request<Member[]>({ url: '/member', method: 'get' })
}

export function createMember(data: MemberSaveDTO) {
  return request<number>({ url: '/member', method: 'post', data })
}

export function updateMember(data: MemberSaveDTO) {
  return request<void>({ url: '/member', method: 'put', data })
}

export function deleteMember(id: number) {
  return request<void>({ url: `/member/${id}`, method: 'delete' })
}
