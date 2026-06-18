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
  /** 角色标签：逗号分隔的 role 字典 id 字符串（如 "32,34"）。 */
  roleTags: string
  healthProfile: HealthProfile
}

export interface MemberSaveDTO {
  id?: number
  name: string
  /** 提交时为逗号分隔的 role 字典 id 字符串。 */
  roleTags: string
  healthProfile: HealthProfile
}

export interface MemberPage {
  records: Member[]
  total: number
}

export function listMembers(params: { pageNum: number; pageSize: number }) {
  return request<MemberPage>({ url: '/member', method: 'get', params })
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
