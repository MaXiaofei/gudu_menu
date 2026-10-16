/** 保质期 / 临期展示工具（我家余量列表徽标、录入预填、智荐临期卡）。 */

/** Date → YYYY-MM-DD（本地时区）。 */
export function ymd(d: Date): string {
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

/** 今天 + n 天 → YYYY-MM-DD（录入时按 suggestedDays 预填 bestBefore）。 */
export function addDays(n: number, from: Date = new Date()): string {
  const d = new Date(from.getFullYear(), from.getMonth(), from.getDate() + n)
  return ymd(d)
}

/** 到期文案：负=已过期 / 0=今天到期 / 正=X 天后到期；null 返回空串（不展示）。 */
export function expiryText(daysLeft?: number | null): string {
  if (daysLeft == null || Number.isNaN(daysLeft)) return ''
  if (daysLeft < 0) return '已过期'
  if (daysLeft === 0) return '今天到期'
  return `${daysLeft} 天后到期`
}

/** 到期紧急程度：expired 已过期 / urgent ≤2 天 / soon ≤7 天 / ok 更久 / '' 无保质期。 */
export function expiryLevel(daysLeft?: number | null): 'expired' | 'urgent' | 'soon' | 'ok' | '' {
  if (daysLeft == null || Number.isNaN(daysLeft)) return ''
  if (daysLeft < 0) return 'expired'
  if (daysLeft <= 2) return 'urgent'
  if (daysLeft <= 7) return 'soon'
  return 'ok'
}

/** 列表徽标是否展示（> 7 天的不打扰，只提示一周内到期与已过期）。 */
export function showExpiryBadge(daysLeft?: number | null): boolean {
  const lv = expiryLevel(daysLeft)
  return lv === 'expired' || lv === 'urgent' || lv === 'soon'
}

/** 「YYYY-MM-DD」→「M月D日」（日期选择器展示）。 */
export function mdLabel(s?: string | null): string {
  if (!s) return ''
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(s)
  if (!m) return ''
  return `${Number(m[2])}月${Number(m[3])}日`
}
