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
