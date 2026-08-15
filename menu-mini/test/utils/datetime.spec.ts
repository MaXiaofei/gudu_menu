import { describe, it, expect } from 'vitest'
import { parseDate, todayStart, isFromToday, relativeDate, mdHm, smartTime } from '@/utils/datetime'

// utils/datetime：后端时间解析（iOS 兼容 - → /）、今天零点、相对日期。
describe('utils/datetime', () => {
  it('parseDate：yyyy-MM-dd HH:mm:ss 与 ISO 均可解析，非法返回 null', () => {
    expect(parseDate('2026-08-10 12:30:00')?.getDate()).toBe(10)
    expect(parseDate('2026-08-10T12:30:00')?.getHours()).toBe(12)
    expect(parseDate('')).toBeNull()
    expect(parseDate('abc')).toBeNull()
    expect(parseDate(null)).toBeNull()
  })

  it('todayStart：今天 0 点', () => {
    const t = todayStart()
    expect(t.getHours()).toBe(0)
    expect(t.getMinutes()).toBe(0)
  })

  it('isFromToday：今天 0 点及以后 true，昨天 false', () => {
    const now = new Date()
    const todayStr = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')} 08:00:00`
    expect(isFromToday(todayStr)).toBe(true)
    expect(isFromToday('2000-01-01 00:00:00')).toBe(false)
    expect(isFromToday(null)).toBe(false)
  })

  it('relativeDate：今天/昨天/N 天前/M/D', () => {
    const now = new Date()
    const fmt = (d: Date) =>
      `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    const y = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1)
    const three = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 3)
    expect(relativeDate(fmt(now))).toBe('今天')
    expect(relativeDate(fmt(y))).toBe('昨天')
    expect(relativeDate(fmt(three))).toBe('3 天前')
    expect(relativeDate('2026-01-05 00:00:00')).toBe('1/5')
    expect(relativeDate('')).toBe('')
  })

  it('mdHm / smartTime 格式', () => {
    expect(mdHm('2026-08-10 09:05:00')).toBe('8/10 09:05')
    const now = new Date()
    const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')} 14:30:00`
    expect(smartTime(today)).toBe('今天 14:30')
  })
})
