import { request } from '@/utils/request'

/** 站内消息（后端 /notification；type: expiry 临期 / shopping 采购；后续 invite/feedback/library 见 P3）。 */
export interface Notice {
  id: number
  type?: string | null
  channel?: string | null
  title?: string | null
  content?: string | null
  isRead?: number | null
  createTime?: string | null
}

export function listNotices(): Promise<Notice[]> {
  return request<Notice[]>({ url: '/notification', method: 'GET' }).then((d) => (Array.isArray(d) ? d : []))
}

/** 未读数（失败按 0，不打扰）。 */
export async function unreadCount(): Promise<number> {
  try {
    const r = await request<{ count?: number } | number>({ url: '/notification/unread-count', method: 'GET', silent: true })
    return typeof r === 'number' ? r : Number(r?.count) || 0
  } catch {
    return 0
  }
}

export function markRead(id: number): Promise<void> {
  return request({ url: `/notification/${id}/read`, method: 'PUT' })
}

/** 消息分类（Tab）：提醒 = 临期/采购；家庭 = 邀请；系统 = 反馈/投稿/其他。 */
export function noticeGroup(type?: string | null): 'remind' | 'family' | 'system' {
  if (type === 'expiry' || type === 'shopping') return 'remind'
  if (type === 'invite') return 'family'
  return 'system'
}
