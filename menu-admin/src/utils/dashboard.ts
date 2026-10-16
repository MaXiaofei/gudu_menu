// 运营仪表盘数据映射：接口 OpsStats → KPI 卡片 / 趋势图 option（纯函数，便于单测）
import type { OpsStats, OpsTrendPoint } from '@/api/ops'
import type { Theme } from '@/styles/themes'
import type { ECOption } from './echarts'

export interface KpiTile {
  key: string
  label: string
  value: string
  /** 需要提醒（如待处理反馈 > 0） */
  alert?: boolean
  /** 点击跳转路由 */
  to?: string
}

export function formatCount(n: number | null | undefined): string {
  if (n === null || n === undefined) return '-'
  const v = Number(n)
  return Number.isFinite(v) ? v.toLocaleString('zh-CN') : '-'
}

export function formatYuan(n: number | null | undefined): string {
  if (n === null || n === undefined) return '-'
  const v = Number(n)
  return Number.isFinite(v) ? `¥${v.toFixed(2)}` : '-'
}

export function buildTotalTiles(stats: OpsStats): KpiTile[] {
  const t = stats.totals
  return [
    { key: 'members', label: '成员数', value: formatCount(t?.members) },
    { key: 'accounts', label: '账号数', value: formatCount(t?.accounts) },
    { key: 'dishes', label: '菜谱数', value: formatCount(t?.dishes) },
    { key: 'cookRecords', label: '做菜记录', value: formatCount(t?.cookRecords) },
    {
      key: 'feedbackOpen',
      label: '待处理反馈',
      value: formatCount(t?.feedbackOpen),
      alert: (t?.feedbackOpen ?? 0) > 0,
      to: '/feedback',
    },
  ]
}

export function buildLast7dTiles(stats: OpsStats): KpiTile[] {
  const l = stats.last7d
  return [
    { key: 'newAccounts', label: '新增账号', value: formatCount(l?.newAccounts) },
    { key: 'activeMembers', label: '活跃成员', value: formatCount(l?.activeMembers) },
    { key: 'cookRecords', label: '做菜', value: formatCount(l?.cookRecords) },
    { key: 'aiCalls', label: 'AI 调用', value: formatCount(l?.aiCalls), to: '/ai-log' },
    { key: 'aiCostYuan', label: 'AI 费用', value: formatYuan(l?.aiCostYuan), to: '/ai-log' },
  ]
}

function addDays(date: string, n: number): string {
  const d = new Date(`${date}T00:00:00Z`)
  d.setUTCDate(d.getUTCDate() + n)
  return d.toISOString().slice(0, 10)
}

/** 趋势按日期升序，并把首尾之间缺失的日期补 0（后端可能只返回有数据的天）。 */
export function normalizeTrend(trend: OpsTrendPoint[] | null | undefined): OpsTrendPoint[] {
  const list = [...(trend ?? [])].filter((p) => p && p.date).sort((a, b) => a.date.localeCompare(b.date))
  if (list.length === 0) return []
  const byDate = new Map(list.map((p) => [p.date, p]))
  const out: OpsTrendPoint[] = []
  const last = list[list.length - 1].date
  for (let d = list[0].date, guard = 0; d <= last && guard < 400; d = addDays(d, 1), guard++) {
    const p = byDate.get(d)
    out.push({
      date: d,
      newAccounts: Number(p?.newAccounts ?? 0),
      activeMembers: Number(p?.activeMembers ?? 0),
      cookRecords: Number(p?.cookRecords ?? 0),
      aiCalls: Number(p?.aiCalls ?? 0),
    })
  }
  return out
}

/** 全部为 0 或无数据 → 空态 */
export function isTrendEmpty(trend: OpsTrendPoint[]): boolean {
  return trend.every((p) => !p.newAccounts && !p.activeMembers && !p.cookRecords && !p.aiCalls)
}

export const TREND_SERIES: { key: keyof Omit<OpsTrendPoint, 'date'>; name: string }[] = [
  { key: 'activeMembers', name: '活跃成员' },
  { key: 'newAccounts', name: '新增账号' },
  { key: 'cookRecords', name: '做菜' },
  { key: 'aiCalls', name: 'AI 调用' },
]

/** 趋势折线图 option：所有颜色取自主题 token，切主题后重新 setOption 即可。 */
export function buildTrendOption(trend: OpsTrendPoint[], theme: Theme): ECOption {
  const axisLine = { lineStyle: { color: theme.border } }
  const axisLabel = { color: theme.caption }
  return {
    color: theme.chart,
    textStyle: { color: theme.body },
    tooltip: {
      trigger: 'axis',
      backgroundColor: theme.card,
      borderColor: theme.border,
      textStyle: { color: theme.title },
    },
    legend: { top: 0, textStyle: { color: theme.body }, icon: 'roundRect' },
    grid: { left: 40, right: 16, top: 36, bottom: 28 },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: trend.map((p) => p.date.slice(5)),
      axisLine,
      axisTick: { show: false },
      axisLabel,
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel,
      splitLine: { lineStyle: { color: theme.border, type: 'dashed' } },
    },
    series: TREND_SERIES.map((s, i) => ({
      name: s.name,
      type: 'line' as const,
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      showSymbol: trend.length <= 14,
      lineStyle: { width: i === 0 ? 3 : 2 },
      data: trend.map((p) => p[s.key]),
    })),
  }
}
