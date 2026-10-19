/**
 * 周 = 周一至周日（meal-plan-nutrition spec「一周从周一开始」）。
 * 一周餐程默认生成下周一到周日；当天为周一时生成本周。
 */

export const WEEKDAYS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'] as const

function startOfDay(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate())
}

/** d 所在周的周一。 */
export function mondayOf(d: Date): Date {
  const x = startOfDay(d)
  const offset = (x.getDay() + 6) % 7 // 周一=0 … 周日=6
  x.setDate(x.getDate() - offset)
  return x
}

/** 默认餐程起始日：今天周一 → 今天；否则下周一。 */
export function defaultPlanWeekStart(today: Date = new Date()): Date {
  const m = mondayOf(today)
  if (m.getTime() === startOfDay(today).getTime()) return m
  m.setDate(m.getDate() + 7)
  return m
}

export function addDays(d: Date, n: number): Date {
  const x = startOfDay(d)
  x.setDate(x.getDate() + n)
  return x
}

/** 2026-10-19 */
export function isoDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/** 10.19 */
export function shortDate(d: Date | string): string {
  const x = typeof d === 'string' ? parseIsoDate(d) : d
  return `${x.getMonth() + 1}.${x.getDate()}`
}

export function parseIsoDate(s: string): Date {
  const [y, m, d] = s.slice(0, 10).split('-').map(Number)
  return new Date(y, m - 1, d)
}

/** 周一起 7 天：{ date: '2026-10-19', label: '周一', short: '10.19' }。 */
export function weekDays(start: Date) {
  const mon = mondayOf(start)
  return WEEKDAYS.map((label, i) => {
    const d = addDays(mon, i)
    return { date: isoDate(d), label, short: shortDate(d) }
  })
}

/** 「10.19 周一 – 10.25 周日」 */
export function weekRangeLabel(start: Date | string): string {
  const mon = mondayOf(typeof start === 'string' ? parseIsoDate(start) : start)
  return `${shortDate(mon)} 周一 – ${shortDate(addDays(mon, 6))} 周日`
}

/** ISO 日期 → 「周X」（周一起）。 */
export function weekdayLabel(iso: string): string {
  return WEEKDAYS[(parseIsoDate(iso).getDay() + 6) % 7]
}
