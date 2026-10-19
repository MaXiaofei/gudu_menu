import { request } from './request'
import type { Page } from './dish'

// 意见反馈管理（api-contract §7，超管）
export type FeedbackStatus = 'NEW' | 'DONE'

export interface Feedback {
  id: number
  memberId: number
  memberName: string | null
  content: string
  contact: string | null
  images: string[] | null
  clientInfo: string | null
  status: FeedbackStatus
  createTime: string
}

export interface FeedbackQuery {
  status?: FeedbackStatus | ''
  pageNum: number
  pageSize: number
}

export function listFeedback(query: FeedbackQuery) {
  const params: Record<string, unknown> = { pageNum: query.pageNum, pageSize: query.pageSize }
  if (query.status) params.status = query.status
  return request<Page<Feedback>>({ url: '/feedback', method: 'get', params })
}

export function updateFeedbackStatus(id: number, status: FeedbackStatus) {
  return request<void>({ url: `/feedback/${id}/status`, method: 'put', data: { status } })
}
