import { describe, it, expect } from 'vitest'
import { parseStepTimers, formatClock, cnNumber, remaining, isHot, progress } from '@/utils/cookTimer'

const secs = (t: string) => parseStepTimers(t).map((x) => x.seconds)

describe('utils/cookTimer 步骤时长解析', () => {
  it('阿拉伯数字分钟 / 秒', () => {
    expect(secs('大火蒸 8 分钟')).toEqual([480])
    expect(secs('焯水 30 秒')).toEqual([30])
    expect(secs('焯水30秒钟')).toEqual([30])
  })
  it('时钟写法 3:00', () => {
    expect(secs('小火煮 3:00')).toEqual([180])
    expect(secs('12:40 后关火')).toEqual([760])
  })
  it('中文数字：十分钟、两分钟、二十五分钟', () => {
    expect(secs('焖十分钟')).toEqual([600])
    expect(secs('炒两分钟')).toEqual([120])
    expect(secs('炖二十五分钟')).toEqual([1500])
  })
  it('半小时、一个半小时、1 小时 20 分钟', () => {
    expect(secs('腌制半小时')).toEqual([1800])
    expect(secs('炖一个半小时')).toEqual([5400])
    expect(secs('炖 1 小时 20 分钟')).toEqual([4800])
  })
  it('区间取上限；多个时长都出', () => {
    expect(secs('煮 3-5 分钟')).toEqual([300])
    expect(secs('先蒸 8 分钟，再焖 2 分钟')).toEqual([480, 120])
  })
  it('无时长 → 空', () => {
    expect(parseStepTimers('放入豆腐块，轻轻推开')).toEqual([])
    expect(parseStepTimers('')).toEqual([])
  })
  it('保留原文片段', () => {
    expect(parseStepTimers('转小火煮 3 分钟')[0].text).toBe('3 分钟')
  })
  it('cnNumber', () => {
    expect(cnNumber('十二')).toBe(12)
    expect(cnNumber('三十')).toBe(30)
    expect(cnNumber('abc')).toBeNaN()
  })
})

describe('utils/cookTimer 计时器', () => {
  it('formatClock', () => {
    expect(formatClock(180)).toBe('3:00')
    expect(formatClock(760)).toBe('12:40')
    expect(formatClock(3723)).toBe('1:02:03')
    expect(formatClock(-5)).toBe('0:00')
  })
  it('剩余 / 快到点 / 进度', () => {
    const now = 1_000_000
    const t = { id: 'a', label: '米饭', total: 600, endAt: now + 45_000 }
    expect(remaining(t, now)).toBe(45)
    expect(isHot(t, now)).toBe(true)
    expect(isHot({ ...t, endAt: now + 120_000 }, now)).toBe(false)
    expect(remaining({ ...t, endAt: now - 1 }, now)).toBe(0)
    expect(isHot({ ...t, endAt: now - 1 }, now)).toBe(false)
    expect(progress(t, now)).toBeCloseTo(0.925)
  })
})
