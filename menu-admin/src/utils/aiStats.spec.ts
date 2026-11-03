import { describe, it, expect } from 'vitest'
import { formatCost, logCostYuan, formatMs, percentile, sampleStats, sceneLabel, summarizeUsage, withinDays } from './aiStats'

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
    expect(s).toEqual({ totalCalls: 100, costYuan: 0, failCalls: 1, successRate: 0.99, tokens: 165 })
  })
  it('空 / 非数组 → 0，成功率 null', () => {
    expect(summarizeUsage(null)).toEqual({ totalCalls: 0, costYuan: 0, failCalls: 0, successRate: null, tokens: 0 })
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

describe('成本口径', () => {
  it('logCostYuan：costYuan > costFen/100 > cost', () => {
    expect(logCostYuan({ costYuan: 0.789, costFen: 1, cost: 5 })).toBeCloseTo(0.789)
    expect(logCostYuan({ costFen: 78.9, cost: 0 })).toBeCloseTo(0.789)
    expect(logCostYuan({ costFen: null, cost: '0.0009' })).toBeCloseTo(0.0009)
    expect(logCostYuan({})).toBe(0)
  })
  it('sampleStats 用 costFen 换算并按场景拆分（网关行 cost 为 0 也不丢）', () => {
    const s = sampleStats([
      { scene: 'mealplan', cost: 0, costFen: 78.9, status: 'ok', createTime: '' },
      { scene: 'agent', cost: 0, costFen: 2.95, status: 'ok', createTime: '' },
    ])
    expect(s.costYuan).toBeCloseTo(0.8185)
    expect(s.costByScene.mealplan).toBeCloseTo(0.789)
  })
  it('summarizeUsage 汇总 costYuan（含字符串）', () => {
    const s = summarizeUsage([
      { scene: 'mealplan', totalCalls: 31, failCalls: 0, tokensIn: 1, tokensOut: 1, latencyAvgMs: 1, costYuan: 0.7892 },
      { scene: 'agent', totalCalls: 14, failCalls: 0, tokensIn: 1, tokensOut: 1, latencyAvgMs: 1, costYuan: '0.0295' },
      { scene: 'menu_recommend', totalCalls: 8, failCalls: 0, tokensIn: 0, tokensOut: 0, latencyAvgMs: 0 },
    ])
    expect(s.costYuan).toBeCloseTo(0.8187)
  })
  it('formatCost 小额保留 4 位；新增场景中文名', () => {
    expect(formatCost(0.0009)).toBe('¥0.0009')
    expect(formatCost(0.789)).toBe('¥0.79')
    expect(formatCost(0)).toBe('¥0.00')
    expect(sceneLabel('mealplan')).toBe('餐程智排')
    expect(sceneLabel('community_text')).toBe('社区文本审核')
  })
})
