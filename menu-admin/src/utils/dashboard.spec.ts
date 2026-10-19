import { describe, it, expect } from 'vitest'
import type { OpsStats } from '@/api/ops'
import { getTheme } from '@/styles/themes'
import {
  buildKpiCards,
  buildTrendOption,
  formatCount,
  formatYuan,
  isTrendEmpty,
  normalizeTrend,
  TREND_SERIES,
} from './dashboard'

const stats: OpsStats = {
  totals: { members: 1234, accounts: 56, dishes: 789, cookRecords: 10000, feedbackOpen: 3 },
  last7d: { newAccounts: 5, activeMembers: 40, cookRecords: 88, aiCalls: 120, aiCostYuan: 3.456 },
  trend: [
    { date: '2026-10-03', newAccounts: 1, activeMembers: 4, cookRecords: 2, aiCalls: 9 },
    { date: '2026-10-01', newAccounts: 2, activeMembers: 5, cookRecords: 3, aiCalls: 10 },
  ],
}

describe('格式化', () => {
  it('formatCount 千分位，非数字 → -', () => {
    expect(formatCount(10000)).toBe('10,000')
    expect(formatCount(0)).toBe('0')
    expect(formatCount(undefined)).toBe('-')
  })
  it('formatYuan 保留两位', () => {
    expect(formatYuan(3.456)).toBe('¥3.46')
    expect(formatYuan(0)).toBe('¥0.00')
    expect(formatYuan(null)).toBe('-')
  })
})

describe('KPI 卡片映射', () => {
  it('5 张卡：累计账号/成员档案/近 7 天活跃/近 7 天做饭/近 7 天 AI 成本', () => {
    const cards = buildKpiCards(stats)
    expect(cards.map((c) => c.label)).toEqual(['累计账号', '成员档案', '近 7 天活跃成员', '近 7 天做饭记录', '近 7 天 AI 成本'])
    expect(cards[1].value).toBe('1,234')
    expect(cards[1].hint).toBe('菜谱 789 道')
    expect(cards[3].hint).toBe('累计 10,000 次')
  })
  it('新增账号 >0 → 说明行绿色；=0 → 普通色', () => {
    expect(buildKpiCards(stats)[0]).toMatchObject({ hint: '近 7 天 +5', hintTone: 'ok' })
    const zero = buildKpiCards({ ...stats, last7d: { ...stats.last7d, newAccounts: 0 } })
    expect(zero[0].hintTone).toBe('muted')
  })
  it('AI 成本格式化为元并可跳转 AI 用量页', () => {
    const ai = buildKpiCards(stats).find((c) => c.key === 'aiCostYuan')!
    expect(ai.value).toBe('¥3.46')
    expect(ai.hint).toBe('调用 120 次')
    expect(ai.to).toBe('/ai-log')
  })
  it('接口缺字段 → 显示 -，不抛错', () => {
    const cards = buildKpiCards({} as OpsStats)
    expect(cards[0].value).toBe('-')
  })
})

describe('趋势归一化', () => {
  it('按日期升序并补齐缺失日期为 0', () => {
    const t = normalizeTrend(stats.trend)
    expect(t.map((p) => p.date)).toEqual(['2026-10-01', '2026-10-02', '2026-10-03'])
    expect(t[1]).toEqual({ date: '2026-10-02', newAccounts: 0, activeMembers: 0, cookRecords: 0, aiCalls: 0 })
  })
  it('跨月补齐', () => {
    const t = normalizeTrend([
      { date: '2026-10-01', newAccounts: 1, activeMembers: 1, cookRecords: 1, aiCalls: 1 },
      { date: '2026-09-29', newAccounts: 1, activeMembers: 1, cookRecords: 1, aiCalls: 1 },
    ])
    expect(t.map((p) => p.date)).toEqual(['2026-09-29', '2026-09-30', '2026-10-01'])
  })
  it('null / 空数组 → []，空态判定', () => {
    expect(normalizeTrend(null)).toEqual([])
    expect(isTrendEmpty([])).toBe(true)
    expect(isTrendEmpty(normalizeTrend([{ date: '2026-10-01', newAccounts: 0, activeMembers: 0, cookRecords: 0, aiCalls: 0 }]))).toBe(true)
    expect(isTrendEmpty(normalizeTrend(stats.trend))).toBe(false)
  })
})

describe('趋势图 option 取主题 token', () => {
  it.each(['cream', 'matcha'])('%s 主题：序列色/坐标轴色来自主题', (key) => {
    const theme = getTheme(key)
    const opt = buildTrendOption(normalizeTrend(stats.trend), theme) as any
    expect(opt.color).toEqual(theme.chart)
    expect(opt.color[0]).toBe(theme.primary)
    expect(opt.xAxis.axisLine.lineStyle.color).toBe(theme.border)
    expect(opt.yAxis.axisLabel.color).toBe(theme.caption)
    expect(opt.xAxis.data).toEqual(['10-01', '10-02', '10-03'])
    expect(opt.series).toHaveLength(TREND_SERIES.length)
    expect(opt.series.map((s: any) => s.name)).toEqual(['活跃成员', '新增账号', '做菜', 'AI 调用'])
    expect(opt.series[0].data).toEqual([5, 0, 4])
  })
  it('两套主题图表配色不同且每套 ≥ 4 色', () => {
    expect(getTheme('cream').chart).not.toEqual(getTheme('matcha').chart)
    expect(getTheme('cream').chart.length).toBeGreaterThanOrEqual(4)
    expect(getTheme('matcha').chart.length).toBeGreaterThanOrEqual(4)
  })
})
