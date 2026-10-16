import { request } from '@/utils/request'

export interface FeedbackPayload {
  content: string
  contact?: string
  images?: string[]
  clientInfo?: string
}

/** 提交意见反馈：POST /feedback → id（每账号每日 ≤ 10 条，超限后端 429）。 */
export function submitFeedback(p: FeedbackPayload): Promise<number> {
  const data: Record<string, unknown> = { content: p.content.trim() }
  if (p.contact?.trim()) data.contact = p.contact.trim()
  if (p.images?.length) data.images = p.images.slice(0, 3)
  if (p.clientInfo) data.clientInfo = p.clientInfo.slice(0, 200)
  return request<number>({ url: '/feedback', method: 'POST', data })
}
