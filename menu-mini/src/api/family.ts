import { request } from '@/utils/request'
import type { FamilyRole, FamilySummary } from '@/utils/family'

/** 家庭接口（menu-api FamilyController，design §6）。跨家庭 404、角色不足 403。 */

export interface SessionFamily {
  familyId: number
  profileId: number | null
  role: FamilyRole | string
}

export interface FamilyMemberView {
  accountId: number
  nickname: string
  role: FamilyRole | string
  joinedAt?: string
  profileId?: number | null
}

export interface InvitationView {
  code: string
  expiresAt: string
  maxUses: number
  usedCount: number
  revoked: boolean
}

export interface FamilyDetail {
  id: number
  name: string
  status: string
  myRole: FamilyRole | string
  members: FamilyMemberView[]
  /** 仅 OWNER/ADMIN 返回（其他角色为 null / 空） */
  invitations?: InvitationView[] | null
}

export interface InvitationPreview {
  familyName: string
  inviterNickname?: string | null
  expiresAt?: string | null
  usable: boolean
  reason?: string | null
}

export function myFamilies(): Promise<{ families: FamilySummary[]; currentFamilyId: number | null }> {
  return request({ url: '/family/mine', method: 'GET' })
}

export function createFamily(name?: string): Promise<SessionFamily> {
  return request({ url: '/family', method: 'POST', data: name ? { name } : {} })
}

export function currentFamilyDetail(): Promise<FamilyDetail> {
  return request({ url: '/family/current', method: 'GET' })
}

export function switchFamily(familyId: number): Promise<SessionFamily> {
  return request({ url: '/family/current', method: 'POST', data: { familyId } })
}

export function renameFamily(id: number, name: string): Promise<void> {
  return request({ url: `/family/${id}`, method: 'PUT', data: { name } })
}

export function createInvitation(id: number): Promise<InvitationView> {
  return request({ url: `/family/${id}/invitations`, method: 'POST' })
}

export function revokeInvitation(id: number, code: string): Promise<void> {
  return request({ url: `/family/${id}/invitations/${encodeURIComponent(code)}`, method: 'DELETE' })
}

/** 邀请预览（游客可访问）。 */
export function previewInvitation(code: string): Promise<InvitationPreview> {
  return request({ url: `/family/invitations/${encodeURIComponent(code)}`, method: 'GET', silent: true })
}

export function joinFamily(code: string): Promise<SessionFamily> {
  return request({ url: '/family/join', method: 'POST', data: { code } })
}

export function changeMemberRole(id: number, accountId: number, role: 'ADMIN' | 'MEMBER'): Promise<void> {
  return request({ url: `/family/${id}/members/${accountId}/role`, method: 'PUT', data: { role } })
}

export function removeMember(id: number, accountId: number): Promise<void> {
  return request({ url: `/family/${id}/members/${accountId}`, method: 'DELETE' })
}

export function leaveFamily(id: number): Promise<void> {
  return request({ url: `/family/${id}/leave`, method: 'POST' })
}

export function transferOwnership(id: number, accountId: number): Promise<void> {
  return request({ url: `/family/${id}/transfer`, method: 'POST', data: { accountId } })
}

export function dissolveFamily(id: number): Promise<void> {
  return request({ url: `/family/${id}`, method: 'DELETE' })
}
