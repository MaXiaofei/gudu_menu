/** 日期工具（对齐 APP 的相对日期/今天零点逻辑）。 */

/** 解析后端时间字符串（'yyyy-MM-dd HH:mm:ss' 或 ISO）为 Date；iOS 兼容。 */
export function parseDate(s?: string | null): Date | null {
  if (!s) return null
  const d = new Date(s.replace(/-/g, '/').replace('T', ' '))
  return isNaN(d.getTime()) ? null : d
}

/** 今天 0 点（本地时区）。 */
export function todayStart(): Date {
  const now = new Date()
  return new Date(now.getFullYear(), now.getMonth(), now.getDate())
}

/** 是否今天 0 点及以后创建（「加到食集」只列近期食集用）。 */
export function isFromToday(s?: string | null): boolean {
  const d = parseDate(s)
  return !!d && d.getTime() >= todayStart().getTime()
}

/** 相对日期：今天 / 昨天 / N 天前 / M/D。 */
export function relativeDate(s?: string | null): string {
  const d = parseDate(s)
  if (!d) return ''
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const that = new Date(d.getFullYear(), d.getMonth(), d.getDate())
  const diff = Math.round((today.getTime() - that.getTime()) / 86400000)
  if (diff <= 0) return '今天'
  if (diff === 1) return '昨天'
  if (diff < 7) return `${diff} 天前`
  return `${d.getMonth() + 1}/${d.getDate()}`
}

/** M/D HH:mm（食记时间轴等）。 */
export function mdHm(s?: string | null): string {
  const d = parseDate(s)
  if (!d) return ''
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${d.getMonth() + 1}/${d.getDate()} ${hh}:${mm}`
}

/** M/D HH:mm / 今天 HH:mm / 昨天 HH:mm（草稿箱等）。 */
export function smartTime(s?: string | null): string {
  const d = parseDate(s)
  if (!d) return ''
  const hm = `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  const rel = relativeDate(s)
  if (rel === '今天' || rel === '昨天') return `${rel} ${hm}`
  return `${d.getMonth() + 1}/${d.getDate()} ${hm}`
}
