import { describe, it, expect } from 'vitest'
import { addDays, expiryText, expiryLevel, showExpiryBadge, mdLabel, ymd } from '@/utils/freshness'

describe('utils/freshness', () => {
  it('expiryText：已过期 / 今天到期 / X 天后到期 / 空', () => {
    expect(expiryText(-3)).toBe('已过期')
    expect(expiryText(-1)).toBe('已过期')
    expect(expiryText(0)).toBe('今天到期')
    expect(expiryText(1)).toBe('1 天后到期')
    expect(expiryText(12)).toBe('12 天后到期')
    expect(expiryText(null)).toBe('')
    expect(expiryText(undefined)).toBe('')
  })

  it('expiryLevel / showExpiryBadge：>7 天不打扰', () => {
    expect(expiryLevel(-1)).toBe('expired')
    expect(expiryLevel(0)).toBe('urgent')
    expect(expiryLevel(2)).toBe('urgent')
    expect(expiryLevel(5)).toBe('soon')
    expect(expiryLevel(30)).toBe('ok')
    expect(expiryLevel(null)).toBe('')
    expect(showExpiryBadge(7)).toBe(true)
    expect(showExpiryBadge(8)).toBe(false)
    expect(showExpiryBadge(-2)).toBe(true)
    expect(showExpiryBadge(null)).toBe(false)
  })

  it('addDays / ymd：跨月补零', () => {
    const base = new Date(2026, 9, 30) // 2026-10-30
    expect(ymd(base)).toBe('2026-10-30')
    expect(addDays(0, base)).toBe('2026-10-30')
    expect(addDays(3, base)).toBe('2026-11-02')
    expect(addDays(-30, base)).toBe('2026-09-30')
  })

  it('mdLabel', () => {
    expect(mdLabel('2026-03-05')).toBe('3月5日')
    expect(mdLabel('')).toBe('')
    expect(mdLabel('bad')).toBe('')
  })
})
