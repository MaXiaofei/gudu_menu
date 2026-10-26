import { request } from '@/utils/request'

export interface Member {
  id: number
  name: string
  roleTags?: string[] | null
  audienceTags?: string[] | null
}

/** 成员列表（兼容数组 / IPage 两种返回）。 */
export async function listMembers(): Promise<Member[]> {
  const data = await request<any>({ url: '/member', method: 'GET', data: { pageNum: 1, pageSize: 100 } })
  return Array.isArray(data) ? data : (data?.records ?? [])
}

/** 当前成员 id（无则 0）。 */
export function getCurrentMember(): Promise<number> {
  return request<number>({ url: '/member/current', method: 'GET' })
}

/** 切换当前就餐成员（写后端 session）。 */
export function setCurrentMember(memberId: number): Promise<void> {
  return request({
    url: `/member/current?memberId=${memberId}`,
    method: 'POST',
  })
}

/** 当前登录成员资料（GET/PUT /member/me）。phone 已脱敏。 */
export interface MemberMe {
  id: number
  name: string
  avatarUrl?: string | null
  phone?: string | null
  isAdmin?: boolean
}

export function getMe(): Promise<MemberMe> {
  return request<MemberMe>({ url: '/member/me', method: 'GET' })
}

/** 改昵称 / 头像（只传改动字段；avatarUrl 必须是本站 /uploads/ 地址）。 */
export function updateMe(p: { name?: string; avatarUrl?: string }): Promise<void> {
  const data: Record<string, string> = {}
  if (p.name !== undefined) data.name = p.name.trim()
  if (p.avatarUrl) data.avatarUrl = p.avatarUrl
  // 内容安全（A.6）：后端只检有变化的昵称/头像；按本次提交内容给场景文案
  const contentScene = p.avatarUrl ? (p.name !== undefined ? 'profile' : 'avatar') : 'nickname'
  return request({ url: '/member/me', method: 'PUT', data, contentScene })
}

/** 「我的」统计（P0 契约 GET /me/stats）；旧后端无此接口返回 null（页面隐藏或回退本地聚合）。 */
export interface MeStats {
  cookedThisMonth: number
  familyDishes: number
  savedFromWaste: number
}

export async function meStats(): Promise<MeStats | null> {
  try {
    const r = await request<MeStats>({ url: '/me/stats', method: 'GET', silent: true })
    return r && typeof r.cookedThisMonth === 'number' ? r : null
  } catch {
    return null
  }
}
