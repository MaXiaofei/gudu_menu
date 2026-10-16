import { describe, it, expect } from 'vitest'
import type { OpsStats } from '@/api/ops'
import { getTheme } from '@/styles/themes'
import {
  buildLast7dTiles,
  buildTotalTiles,
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
  it('累计 5 项：成员/账号/菜谱/做菜记录/待处理反馈，反馈 >0 标红并可跳转', () => {
    const tiles = buildTotalTiles(stats)
    expect(tiles.map((t) => t.label)).toEqual(['成员数', '账号数', '菜谱数', '做菜记录', '待处理反馈'])
    expect(tiles[0].value).toBe('1,234')
    const fb = tiles.find((t) => t.key === 'feedbackOpen')!
    expect(fb.alert).toBe(true)
    expect(fb.to).toBe('/feedback')
  })
  it('待处理反馈为 0 → 不标红', () => {
    const tiles = buildTotalTiles({ ...stats, totals: { ...stats.totals, feedbackOpen: 0 } })
    expect(tiles.find((t) => t.key === 'feedbackOpen')!.alert).toBe(false)
  })
  it('近 7 天 5 项，AI 费用格式化为元', () => {
    const tiles = buildLast7dTiles(stats)
    expect(tiles.map((t) => t.label)).toEqual(['新增账号', '活跃成员', '做菜', 'AI 调用', 'AI 费用'])
    expect(tiles.find((t) => t.key === 'aiCostYuan')!.value).toBe('¥3.46')
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
