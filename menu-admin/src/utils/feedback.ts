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
