// 意见反馈展示用的纯函数（便于单测）。

export type ClientPlatform = 'App' | '小程序' | 'H5' | '未知'

/**
 * 从 clientInfo 推断来源端：
 * - Flutter：`flutter/ios v1.0.0`
 * - 小程序：`v1.0.0 / mp-weixin / iPhone 15 / iOS 18.2 / wx8.0.50`（uniPlatform）
 * - H5：uniPlatform 为 web/h5
 */
export function platformOf(clientInfo: string | null | undefined): ClientPlatform {
  const s = (clientInfo ?? '').toLowerCase()
  if (!s) return '未知'
  if (s.includes('flutter')) return 'App'
  if (s.includes('mp-weixin') || /\bwx\d/.test(s) || s.includes('miniprogram')) return '小程序'
  if (/\b(web|h5)\b/.test(s)) return 'H5'
  return '未知'
}

/** 反馈标题：取内容首行，超长截断。 */
export function feedbackTitle(content: string | null | undefined, max = 28): string {
  const first = (content ?? '').split(/\r?\n/).find((l) => l.trim())?.trim() ?? ''
  return first.length > max ? `${first.slice(0, max)}…` : first
}

/** 相对时间：1 小时内「N 分钟前」，当天「N 小时前」，昨天「昨天」，其余 MM-DD HH:mm。 */
export function relativeTime(iso: string | null | undefined, now: Date = new Date()): string {
  if (!iso) return '-'
  const t = new Date(iso.includes('T') ? iso : iso.replace(' ', 'T'))
  if (Number.isNaN(t.getTime())) return iso
  const diffMin = Math.floor((now.getTime() - t.getTime()) / 60000)
  if (diffMin < 1) return '刚刚'
  if (diffMin < 60) return `${diffMin} 分钟前`
  const sameDay = t.toDateString() === now.toDateString()
  if (sameDay) return `${Math.floor(diffMin / 60)} 小时前`
  const y = new Date(now)
  y.setDate(now.getDate() - 1)
  if (t.toDateString() === y.toDateString()) return '昨天'
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(t.getMonth() + 1)}-${pad(t.getDate())} ${pad(t.getHours())}:${pad(t.getMinutes())}`
}

// ---------------------------------------------------------------- A.11 类型 / 状态 / 来源

export type FeedbackTypeKey = 'general' | 'ai' | 'complaint'

export const FEEDBACK_TYPE_LABEL: Record<FeedbackTypeKey, string> = {
  general: '一般反馈',
  ai: 'AI 内容',
  complaint: '投诉举报',
}

/** 类型标签（缺省 / 未知按一般反馈）。 */
export function feedbackTypeLabel(type: string | null | undefined): string {
  return FEEDBACK_TYPE_LABEL[(type || 'general') as FeedbackTypeKey] ?? FEEDBACK_TYPE_LABEL.general
}

/** 类型标签色（全局 .yh-tag 修饰类）：AI 内容 info、投诉 err、一般 mute。 */
export function feedbackTypeTone(type: string | null | undefined): 'info' | 'err' | 'mute' {
  if (type === 'ai') return 'info'
  if (type === 'complaint') return 'err'
  return 'mute'
}

export function feedbackStatusLabel(status: string | null | undefined): string {
  if (status === 'DONE') return '已处理'
  if (status === 'REPLIED') return '已回复'
  return '待处理'
}

export function feedbackStatusTone(status: string | null | undefined): 'ok' | 'info' | 'warn' {
  if (status === 'DONE') return 'ok'
  if (status === 'REPLIED') return 'info'
  return 'warn'
}

const AI_SOURCE_LABEL: Record<string, string> = { agent: '咕嘟助手', scan: '拍照识别', weekplan: '一周餐程' }

/** 来源引用展示：「agent:m123」→「咕嘟助手 · m123」；其他原样。 */
export function sourceRefLabel(ref: string | null | undefined): string {
  const s = (ref ?? '').trim()
  if (!s) return ''
  const i = s.indexOf(':')
  if (i <= 0) return AI_SOURCE_LABEL[s] ?? s
  const label = AI_SOURCE_LABEL[s.slice(0, i)]
  if (!label) return s
  const tail = s.slice(i + 1)
  return tail ? `${label} · ${tail}` : label
}

export const REPLY_MAX = 1000

/** 回复内容校验：返回错误文案，合法返回空串。 */
export function validateReply(reply: string): string {
  const s = reply.trim()
  if (!s) return '请填写回复内容'
  if (s.length > REPLY_MAX) return `回复最多 ${REPLY_MAX} 字`
  return ''
}
