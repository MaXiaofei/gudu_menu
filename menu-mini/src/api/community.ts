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
  /** 宽 / 高 */
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
  fromCookRecordId?: number
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
  return request({
    url: '/community/posts',
    method: 'POST',
    data: { ...b, title: b.title.trim(), text: (b.text || '').trim(), imageUrls: b.imageUrls.slice(0, 9) },
  })
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
