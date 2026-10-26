import { request } from './request'
import type { Page } from './dish'

// 意见反馈管理（api-contract §7，超管；A.11 闭环：类型 / 来源 / 回复）
export type FeedbackStatus = 'NEW' | 'REPLIED' | 'DONE'
export type FeedbackType = 'general' | 'ai' | 'complaint'

export interface Feedback {
  id: number
  memberId: number
  memberName: string | null
  /** general 一般 / ai AI 内容 / complaint 投诉举报（老数据可能缺省，按 general） */
  type?: FeedbackType | null
  /** 来源引用：AI 内容反馈为「agent:消息id」之类，投诉为来源页面 */
  sourceRef?: string | null
  content: string
  contact: string | null
  images: string[] | null
  clientInfo: string | null
  status: FeedbackStatus
  reply?: string | null
  repliedAt?: string | null
  repliedBy?: number | null
  createTime: string
}

export interface FeedbackQuery {
  type?: FeedbackType | ''
  status?: FeedbackStatus | ''
  pageNum: number
  pageSize: number
}

export function listFeedback(query: FeedbackQuery) {
  const params: Record<string, unknown> = { pageNum: query.pageNum, pageSize: query.pageSize }
  if (query.type) params.type = query.type
  if (query.status) params.status = query.status
  return request<Page<Feedback>>({ url: '/feedback', method: 'get', params })
}

export function updateFeedbackStatus(id: number, status: FeedbackStatus) {
  return request<void>({ url: `/feedback/${id}/status`, method: 'put', data: { status } })
}

/** 回复反馈（同时给提交人发站内消息）；markDone 时一并标记已处理。 */
export function replyFeedback(id: number, reply: string, markDone = false) {
  return request<void>({ url: `/feedback/${id}/reply`, method: 'put', data: { reply: reply.trim(), markDone } })
}
