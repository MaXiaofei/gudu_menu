import { request } from '@/utils/request'

export interface FeedbackPayload {
  content: string
  contact?: string
  images?: string[]
  clientInfo?: string
  /** 反馈类型 bug/feature/recipe/other（后端字段 P3 上线前忽略，已同时写入 clientInfo） */
  type?: string
  /** 报错编号（短 traceId） */
  errorId?: string
}

/** 提交意见反馈：POST /feedback → id（每账号每日 ≤ 10 条，超限后端 429）。 */
export function submitFeedback(p: FeedbackPayload): Promise<number> {
  const data: Record<string, unknown> = { content: p.content.trim() }
  if (p.contact?.trim()) data.contact = p.contact.trim()
  if (p.images?.length) data.images = p.images.slice(0, 3)
  if (p.clientInfo) data.clientInfo = p.clientInfo.slice(0, 200)
  if (p.type) data.type = p.type
  if (p.errorId) data.errorId = p.errorId
  return request<number>({ url: '/feedback', method: 'POST', data, contentScene: 'feedback' })
}
