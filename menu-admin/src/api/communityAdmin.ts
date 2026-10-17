import { request } from './request'
import type { CursorPage, UserLite } from './agentAdmin'

// 厨房圈后台：审核队列 / 统计 / 处置（contracts.md §2 后台）

export type Risk = 'HIGH' | 'MEDIUM' | 'LOW'
export type PostStatus = 'PUBLISHED' | 'REVIEWING' | 'HIDDEN'
export type ModerateAction = 'APPROVE' | 'HIDE' | 'BAN_7D'
/** 队列状态：待处理 / 已处理 / 作者申诉 */
export type QueueStatus = 'PENDING' | 'DONE' | 'APPEALED'

export interface ModerationRecord {
  action: ModerateAction
  reason: string
  note?: string | null
  operator: string
  at: string
}

export interface QueueItem {
  postId: number
  coverUrl: string | null
  title: string
  author: UserLite
  risk: Risk
  reasons: string[]
  reportCount: number
  status: PostStatus
  createdAt: string
  // 契约外的可选增强字段（后端提供则展示）：AI 预审分项、举报记录
  aiScores?: { label: string; score: number; unit?: string }[]
  aiSummary?: string | null
  reports?: { user: UserLite; reason: string; text?: string | null; createdAt: string }[]
  /** 已处理 / 申诉中的帖子带上一次处置记录 */
  moderation?: ModerationRecord | null
  /** 申诉中：作者的申诉理由 */
  appeal?: { text: string; createdAt: string } | null
}

/** 统计：契约只给了路径，按以下字段对接，缺失的字段显示「—」。 */
export interface CommunityStats {
  newPosts?: number
  fromCookLog?: number
  autoPassRate?: number
  pending?: number
  pendingHigh?: number
  pendingMedium?: number
  reports?: number
  autoHidden?: number
  avgHandleMinutes?: number
  appealed?: number
}

/** 帖子详情（管理员视角 GET /admin/community/posts/{id}，可看到审核中 / 已隐藏的帖子）。 */
export interface PostDetailLite {
  postId: number
  title: string
  text: string
  images: string[]
  topics: { topicId: number; name: string }[]
  recipe: { id: number; name: string } | null
  status: PostStatus
  createdAt: string
  city: string | null
  // 管理员视角附带的审核信息（有则覆盖队列项里的同名字段）
  aiScores?: QueueItem['aiScores']
  aiSummary?: string | null
  reports?: QueueItem['reports']
  moderation?: ModerationRecord | null
  appeal?: QueueItem['appeal']
  authorHistory?: string | null
}

export function listQueue(q: { status?: QueueStatus; risk?: Risk; cursor?: string | null; size?: number } = {}) {
  const params: Record<string, unknown> = { size: q.size ?? 20, status: q.status ?? 'PENDING' }
  if (q.risk) params.risk = q.risk
  if (q.cursor) params.cursor = q.cursor
  return request<CursorPage<QueueItem>>({ url: '/admin/community/queue', method: 'get', params })
}

export function getCommunityStats() {
  return request<CommunityStats>({ url: '/admin/community/stats', method: 'get' })
}

export function getAdminPost(postId: number) {
  return request<PostDetailLite>({ url: `/admin/community/posts/${postId}`, method: 'get' })
}

export function moderatePost(postId: number, body: { action: ModerateAction; reason: string; note: string }) {
  return request<unknown>({ url: `/admin/community/posts/${postId}/moderate`, method: 'post', data: body })
}
