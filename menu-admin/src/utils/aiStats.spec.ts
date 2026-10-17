import { describe, it, expect } from 'vitest'
import { formatMs, percentile, sampleStats, sceneLabel, summarizeUsage, withinDays } from './aiStats'

describe('percentile', () => {
  it('最近秩法 P95 / 空数组 null', () => {
    const v = Array.from({ length: 100 }, (_, i) => i + 1)
    expect(percentile(v, 95)).toBe(95)
    expect(percentile([300, 100, 200], 50)).toBe(200)
    expect(percentile([], 95)).toBeNull()
  })
})

describe('summarizeUsage', () => {
  it('合计调用/失败/token 与成功率', () => {
    const s = summarizeUsage([
      { scene: 'a', totalCalls: 90, failCalls: 1, tokensIn: 100, tokensOut: 50, latencyAvgMs: 1 },
      { scene: 'b', totalCalls: 10, failCalls: 0, tokensIn: 10, tokensOut: 5, latencyAvgMs: 1 },
    ])
    expect(s).toEqual({ totalCalls: 100, failCalls: 1, successRate: 0.99, tokens: 165 })
  })
  it('空 / 非数组 → 0，成功率 null', () => {
    expect(summarizeUsage(null)).toEqual({ totalCalls: 0, failCalls: 0, successRate: null, tokens: 0 })
  })
})

describe('sampleStats', () => {
  it('成本求和、按场景拆分，P95 只算成功调用，找出最慢场景', () => {
    const s = sampleStats([
      { scene: 'menu_recommend', cost: 0.1, latencyMs: 1000, status: 'ok', createTime: '' },
      { scene: 'menu_recommend', cost: '0.2', latencyMs: 1200, status: 'ok', createTime: '' },
      { scene: 'nutrition_fill', cost: 0.05, latencyMs: 4000, status: 'ok', createTime: '' },
      { scene: 'nutrition_fill', cost: null, latencyMs: 30000, status: 'fail', createTime: '' },
    ])
    expect(s.costYuan).toBeCloseTo(0.35)
    expect(s.costByScene.menu_recommend).toBeCloseTo(0.3)
    expect(s.p95Ms).toBe(4000)
    expect(s.slowestScene).toBe('nutrition_fill')
    expect(s.sampleSize).toBe(4)
  })
})

describe('withinDays / 格式化', () => {
  it('只保留窗口内的调用（兼容空格分隔时间）', () => {
    const now = new Date('2026-10-16T12:00:00')
    const logs = [{ createTime: '2026-10-16 08:00:00' }, { createTime: '2026-10-10T08:00:00' }]
    expect(withinDays(logs, 1, now)).toHaveLength(1)
    expect(withinDays(logs, 7, now)).toHaveLength(2)
  })
  it('formatMs / sceneLabel', () => {
    expect(formatMs(3200)).toBe('3.2 秒')
    expect(formatMs(180)).toBe('180 ms')
    expect(formatMs(null)).toBe('-')
    expect(sceneLabel('menu_recommend')).toBe('首页智荐')
    expect(sceneLabel('x')).toBe('x')
  })
})
