import { describe, it, expect } from 'vitest'
import { mondayOf, defaultPlanWeekStart, isoDate, weekDays, weekRangeLabel, weekdayLabel } from '@/utils/week'

const d = (s: string) => {
  const [y, m, dd] = s.split('-').map(Number)
  return new Date(y, m - 1, dd, 18, 40)
}

describe('utils/week 一周从周一开始', () => {
  it('mondayOf：周五 / 周日 / 周一', () => {
    expect(isoDate(mondayOf(d('2026-10-16')))).toBe('2026-10-12') // 周五
    expect(isoDate(mondayOf(d('2026-10-18')))).toBe('2026-10-12') // 周日仍属本周
    expect(isoDate(mondayOf(d('2026-10-19')))).toBe('2026-10-19') // 周一
  })

  it('默认餐程：周五生成 → 下周一 10.19（spec 场景）', () => {
    expect(isoDate(defaultPlanWeekStart(d('2026-10-16')))).toBe('2026-10-19')
  })
  it('默认餐程：周日 → 次日周一；周一当天 → 本周', () => {
    expect(isoDate(defaultPlanWeekStart(d('2026-10-18')))).toBe('2026-10-19')
    expect(isoDate(defaultPlanWeekStart(d('2026-10-19')))).toBe('2026-10-19')
  })
  it('跨月', () => {
    expect(isoDate(defaultPlanWeekStart(d('2026-10-30')))).toBe('2026-11-02')
  })

  it('weekDays：周一起 7 天', () => {
    const w = weekDays(d('2026-10-21'))
    expect(w.map((x) => x.label)).toEqual(['周一', '周二', '周三', '周四', '周五', '周六', '周日'])
    expect(w[0]).toEqual({ date: '2026-10-19', label: '周一', short: '10.19' })
    expect(w[6].date).toBe('2026-10-25')
  })

  it('weekRangeLabel / weekdayLabel', () => {
    expect(weekRangeLabel('2026-10-19')).toBe('10.19 周一 – 10.25 周日')
    expect(weekdayLabel('2026-10-25')).toBe('周日')
    expect(weekdayLabel('2026-10-19')).toBe('周一')
  })
})
