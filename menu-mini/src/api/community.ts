/**
 * 厨房圈（contracts §2）。
 */
import { request } from '@/utils/request'
import { normalizePage, type CursorPage } from '@/utils/cursor'
import type { CDishCard, UserLite } from './contract'

export type FeedTab = 'recommend' | 'follow' | 'topic'

export interface PostCard {
  postId: number
  author: UserLite
  title: string
  coverUrl: string
  /** 封面宽 / 高（后端 0.5–2），瀑布流按它定卡片高度 */
  coverRatio: number
  imageCount: number
  likeCount: number
  liked: boolean
  copyCount: number
  tags: string[]
}

export interface Topic {
  topicId: number
  name: string
  intro: string
  rule: string
  startDate: string
  endDate: string
  /** 第几期（周） */
  weekNo?: number | null
  participantCount: number
  postCount: number
  coverUrls: string[]
}

export interface PostDetail extends PostCard {
  text: string
  images: string[]
  topics: { topicId: number; name: string }[]
  recipe: (CDishCard & { copyCount: number }) | null
  commentCount: number
  collectCount: number
  collected: boolean
  following: boolean
  createdAt: string
  city: string | null
  status: 'PUBLISHED' | 'REVIEWING' | 'HIDDEN'
  /** 仅作者可见的处置原因（如「图片未通过审核」） */
  statusReason?: string | null
}

export interface Comment {
  commentId: number
  author: UserLite
  text: string
  isAuthor: boolean
  likeCount: number
  createdAt: string
  replies: Comment[]
}

export interface TopicDetail extends Topic {
  myProgress: { days: { date: string; done: boolean; coverUrl: string | null }[]; doneCount: number }
  ranking: { user: UserLite; days: number; likeCount: number; rank: number }[]
  myRank: number | null
}

export interface CreatePostBody {
  title: string
  text: string
  imageUrls: string[]
  dishId?: number
  topicIds: number[]
  visibility: 'PUBLIC' | 'FOLLOWERS'
  /** 从食记分享：食记列表/详情的 foodLogId */
  fromFoodLogId?: number
  /** 封面宽 / 高（可选，0.5–2） */
  coverRatio?: number
}

export async function feed(tab: FeedTab, cursor: string | null, opts: { topicId?: number; size?: number } = {}): Promise<CursorPage<PostCard>> {
  return normalizePage(
    await request({
      url: '/community/feed',
      method: 'GET',
      data: { tab, topicId: opts.topicId, cursor, size: opts.size ?? 20 },
    }),
  )
}

/** 封面高 / 宽（瀑布流卡片高度）：coverRatio 夹到后端约定的 0.5–2 再取倒数。 */
export function coverHeightRatio(coverRatio?: number | null): number {
  const r = Math.min(2, Math.max(0.5, coverRatio || 1))
  return 1 / r
}

/** 宽高 → coverRatio（夹到 0.5–2，两位小数）；拿不到尺寸返回 undefined。 */
export function toCoverRatio(width?: number, height?: number): number | undefined {
  if (!width || !height) return undefined
  return Math.round(Math.min(2, Math.max(0.5, width / height)) * 100) / 100
}

/** 进行中的话题（发布页选择用）：GET /community/topics?status=ACTIVE */
export async function listTopics(status: 'ACTIVE' | 'ENDED' = 'ACTIVE', size = 10): Promise<Topic[]> {
  const r = await request<Topic[] | null>({ url: '/community/topics', method: 'GET', data: { status, size }, silent: true })
  return Array.isArray(r) ? r : []
}

/** 话题期数文案：「第 3 周」 */
export function weekNoText(t?: Pick<Topic, 'weekNo'> | null): string {
  return t?.weekNo ? `第 ${t.weekNo} 周` : ''
}

export async function banner(): Promise<{ topic: Topic | null; followingUpdates: UserLite[] }> {
  const r = await request<any>({ url: '/community/banner', method: 'GET', silent: true })
  return { topic: r?.topic ?? null, followingUpdates: r?.followingUpdates ?? [] }
}

/** 发布前校验：标题 1–30、正文 ≤1000、图 1–9 张。返回错误文案，合法返回空串。 */
export function validatePost(b: Pick<CreatePostBody, 'title' | 'text' | 'imageUrls'>): string {
  const title = (b.title || '').trim()
  if (!b.imageUrls.length) return '至少放 1 张照片'
  if (b.imageUrls.length > 9) return '最多 9 张照片'
  if (!title) return '写个标题吧'
  if ([...title].length > 30) return '标题最多 30 字'
  if ([...(b.text || '')].length > 1000) return '正文最多 1000 字'
  return ''
}

export function createPost(b: CreatePostBody): Promise<{ postId: number; status: string }> {
  const data: Record<string, unknown> = { ...b, title: b.title.trim(), text: (b.text || '').trim(), imageUrls: b.imageUrls.slice(0, 9) }
  if (b.coverRatio !== undefined) data.coverRatio = toCoverRatio(b.coverRatio, 1)
  return request({ url: '/community/posts', method: 'POST', data })
}

export function postDetail(id: number): Promise<PostDetail> {
  return request({ url: `/community/posts/${id}`, method: 'GET' })
}

export function deletePost(id: number): Promise<unknown> {
  return request({ url: `/community/posts/${id}`, method: 'DELETE' })
}

export function react(
  id: number,
  type: 'LIKE' | 'COLLECT',
  on: boolean,
): Promise<{ likeCount: number; collectCount: number; liked: boolean; collected: boolean }> {
  return request({ url: `/community/posts/${id}/reactions`, method: 'POST', data: { type, on } })
}

export function copyRecipe(id: number): Promise<{ dishId: number }> {
  return request({ url: `/community/posts/${id}/copy-recipe`, method: 'POST' })
}

export async function copies(id: number, size = 10): Promise<{ user: UserLite; imageUrl: string }[]> {
  const r = await request<any>({ url: `/community/posts/${id}/copies`, method: 'GET', data: { size }, silent: true })
  return r?.items ?? []
}

export async function comments(id: number, cursor: string | null, size = 20): Promise<CursorPage<Comment>> {
  return normalizePage(await request({ url: `/community/posts/${id}/comments`, method: 'GET', data: { cursor, size } }))
}

export function addComment(id: number, text: string, parentId?: number): Promise<Comment> {
  return request({
    url: `/community/posts/${id}/comments`,
    method: 'POST',
    data: parentId ? { text: text.trim(), parentId } : { text: text.trim() },
  })
}

export function follow(userId: number, on: boolean): Promise<{ following: boolean }> {
  return request({ url: `/community/users/${userId}/follow`, method: 'POST', data: { on } })
}

export function topicDetail(id: number): Promise<TopicDetail> {
  return request({ url: `/community/topics/${id}`, method: 'GET' })
}

export function aiText(b: {
  dishId?: number
  cookRecordId?: number
  imageUrls?: string[]
  style?: 'default' | 'casual' | 'short'
}): Promise<{ text: string }> {
  return request({ url: '/community/drafts/ai-text', method: 'POST', data: b, timeout: 30000 })
}

export function report(postId: number, reason: string): Promise<unknown> {
  return request({ url: '/community/reports', method: 'POST', data: { postId, reason } })
}

/** 1234 → 1.2k；12000 → 1.2w */
export function countText(n: number): string {
  if (n >= 10000) return `${(n / 10000).toFixed(1).replace(/\.0$/, '')}w`
  if (n >= 1000) return `${(n / 1000).toFixed(1).replace(/\.0$/, '')}k`
  return String(n || 0)
}

/** 乐观更新：点赞切换（失败时调用方回滚）。 */
export function toggleLikeLocal<T extends { liked: boolean; likeCount: number }>(p: T): T {
  p.liked = !p.liked
  p.likeCount = Math.max(0, p.likeCount + (p.liked ? 1 : -1))
  return p
}

/** 非公开帖子给作者的提示：有处置原因（如「图片未通过审核」）优先，其次审核中 / 已隐藏。 */
export function postReviewText(p: Pick<PostDetail, 'status' | 'statusReason'>): string {
  if (p.statusReason) return `${p.statusReason} · 仅自己可见`
  return p.status === 'REVIEWING' ? '审核中 · 仅自己可见，通过后大家就能看到' : '已被隐藏'
}
