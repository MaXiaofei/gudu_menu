import { request } from '@/utils/request'

/** 后端反馈类型：general 一般 / ai AI 内容 / complaint 投诉举报。 */
export type FeedbackType = 'general' | 'ai' | 'complaint'
/** 处理状态：NEW 待处理 / REPLIED 已回复 / DONE 已处理。 */
export type FeedbackStatus = 'NEW' | 'REPLIED' | 'DONE'

export interface FeedbackPayload {
  content: string
  contact?: string
  images?: string[]
  clientInfo?: string
  /** 反馈类型（空按 general） */
  type?: FeedbackType
  /** 来源引用：AI 内容反馈为「agent:消息id」之类，最多 128 字 */
  sourceRef?: string
}

/** 反馈页上的分类（出错了/想要功能/菜谱有误/其他 都是一般反馈）→ 后端类型。 */
export function apiTypeOf(kind?: string | null): FeedbackType {
  if (kind === 'ai') return 'ai'
  if (kind === 'complaint') return 'complaint'
  return 'general'
}

/** 提交意见反馈：POST /feedback → id（每账号每日 ≤ 10 条，超限后端 429）。 */
export function submitFeedback(p: FeedbackPayload): Promise<number> {
  const data: Record<string, unknown> = { content: p.content.trim() }
  if (p.contact?.trim()) data.contact = p.contact.trim()
  if (p.images?.length) data.images = p.images.slice(0, 3)
  if (p.clientInfo) data.clientInfo = p.clientInfo.slice(0, 200)
  if (p.type) data.type = p.type
  if (p.sourceRef?.trim()) data.sourceRef = p.sourceRef.trim().slice(0, 128)
  return request<number>({ url: '/feedback', method: 'POST', data, contentScene: 'feedback' })
}

/** 我的反馈（本人可见；不含联系方式与设备信息）。 */
export interface MyFeedback {
  id: number
  type?: FeedbackType | null
  sourceRef?: string | null
  content: string
  images?: string[] | null
  status: FeedbackStatus
  reply?: string | null
  repliedAt?: string | null
  createTime: string
}

export interface MyFeedbackPage {
  records: MyFeedback[]
  total: number
}

/** GET /feedback/mine（分页，最近的在前）。 */
export async function listMyFeedback(pageNum = 1, pageSize = 20): Promise<MyFeedbackPage> {
  const r = await request<Partial<MyFeedbackPage> | null>({
    url: '/feedback/mine',
    method: 'GET',
    data: { pageNum, pageSize },
  })
  return { records: Array.isArray(r?.records) ? r!.records! : [], total: Number(r?.total) || 0 }
}

/** GET /feedback/mine/{id}（非本人 404）。 */
export function getMyFeedback(id: number | string): Promise<MyFeedback> {
  return request<MyFeedback>({ url: `/feedback/mine/${id}`, method: 'GET' })
}

/** 状态文案（用户视角：已回复 / 已处理都算有结果）。 */
export function feedbackStatusText(s?: string | null): string {
  if (s === 'REPLIED') return '已回复'
  if (s === 'DONE') return '已处理'
  return '处理中'
}

/** 状态胶囊颜色（CSS 变量，不硬编码色值）。 */
export function feedbackStatusColor(s?: string | null): string {
  if (s === 'REPLIED') return 'var(--info)'
  if (s === 'DONE') return 'var(--success)'
  return 'var(--warning)'
}

export function feedbackTypeText(t?: string | null): string {
  if (t === 'ai') return 'AI 内容'
  if (t === 'complaint') return '投诉举报'
  return '意见建议'
}
