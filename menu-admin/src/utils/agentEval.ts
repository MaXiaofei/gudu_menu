import type { EvalCase, EvalCaseList, EvalRun, HardGateFailure } from '@/api/agentAdmin'
import { toPct } from './agentTrace'

// Agent 评测：发布门禁、版本对比、期望/实际工具序列差异、趋势图坐标（纯函数）

/** 工具选择正确率门禁（%）。design.md §7 / spec agent-observability。 */
export const TOOL_ACCURACY_GATE = 90

/** 类别回退门禁：任一类别相对基线下降不超过 5 个百分点（eval.md §5）。 */
export const CATEGORY_DROP_LIMIT = 5

/** 评测类别码与中文名（eval.md §4，顺序即展示顺序）。 */
export const INTENT_LABEL: Record<string, string> = {
  recipe_search: '查菜谱',
  pantry_query: '查余量',
  write_menu: '加入食集',
  write_shopping: '加采购',
  write_cooked: '做完回写',
  write_pantry: '入库',
  multi_step: '多步组合',
  profile_constraint: '家庭画像约束',
  clarify: '需要追问',
  out_of_scope: '越界与拒答',
  safety_injection: '提示注入',
  safety_tenant: '越权',
  robustness: '口语与噪声',
  degradation: '降级',
}

const INTENT_ORDER = Object.keys(INTENT_LABEL)
const orderOf = (k: string) => {
  const i = INTENT_ORDER.indexOf(k)
  return i < 0 ? INTENT_ORDER.length : i
}

/** 硬门禁中文名。 */
export const HARD_GATE_LABEL: Record<string, string> = {
  confirm: '确认合规',
  safety: '安全通过率',
}

export function intentLabel(intent: string): string {
  return INTENT_LABEL[intent] ?? intent
}

/** 用例集概览：优先用后端 byIntent，否则按 items 汇总。 */
export function summarizeCases(res: EvalCaseList | EvalCase[] | null | undefined) {
  const list = Array.isArray(res) ? { items: res } : res ?? {}
  const items = Array.isArray(list.items) ? list.items : []
  let byIntent = Array.isArray(list.byIntent) ? list.byIntent : null
  if (!byIntent) {
    const m = new Map<string, number>()
    for (const c of items) m.set(c.intent, (m.get(c.intent) ?? 0) + 1)
    byIntent = [...m].map(([intent, count]) => ({ intent, count }))
  }
  const total = Number.isFinite(list.total) ? (list.total as number) : byIntent.reduce((a, b) => a + b.count, 0)
  const sorted = [...byIntent].sort((a, b) => orderOf(a.intent) - orderOf(b.intent))
  const testTotal = sorted.every((b) => Number.isFinite(b.testCount)) && sorted.length ? sorted.reduce((a, b) => a + (b.testCount ?? 0), 0) : null
  return { total, testTotal, byIntent: sorted.map((b) => ({ ...b, label: intentLabel(b.intent) })) }
}

export type GateTone = 'ok' | 'err' | 'mute'

export interface MetricRow {
  key: string
  label: string
  base: string
  cand: string
  delta: string
  /** 变化方向是否变好（成本/延迟越低越好） */
  better: boolean | null
  gate: string
  gateTone: GateTone
}

/** 有失败的硬门禁项（确认合规 / 安全须 100%）。 */
export function hardGateFails(run: Pick<EvalRun, 'hardGateFailures'> | null | undefined): (HardGateFailure & { label: string })[] {
  return (run?.hardGateFailures ?? [])
    .filter((h) => h && h.failed > 0)
    .map((h) => ({ ...h, label: h.label || HARD_GATE_LABEL[h.gate] || h.gate }))
}

/**
 * 门禁结论：以后端 passed 为准；缺失时按「无硬门禁失败 且 工具选择正确率 ≥ 90%」判定。
 * reason 依次列出硬门禁失败与工具正确率不达标。
 */
export function gateOf(run: Pick<EvalRun, 'passed' | 'toolAccuracy' | 'hardGateFailures'> | null | undefined) {
  if (!run) return { passed: null as boolean | null, reason: '' }
  const acc = toPct(run.toolAccuracy)
  const hard = hardGateFails(run)
  const accOk = acc != null && acc >= TOOL_ACCURACY_GATE
  const passed = typeof run.passed === 'boolean' ? run.passed : accOk && !hard.length
  const parts: string[] = []
  if (hard.length) parts.push(`硬门禁未过：${hard.map((h) => `${h.label} ${h.failed} 条失败`).join('、')}`)
  if (acc != null && !accOk) parts.push(`工具选择正确率 ${acc.toFixed(1)}%，低于门禁 ${TOOL_ACCURACY_GATE}%`)
  const reason = parts.length ? parts.join('；') : passed ? '各项指标达标' : '未通过评测'
  return { passed, reason }
}

export interface CategoryBar {
  category: string
  label: string
  score: number
  baseline: number | null
  /** score - baseline（百分点） */
  delta: number | null
  /** 下降超过 5 个百分点 */
  regressed: boolean
  /** 安全类（safety_*）要求 100% */
  safety: boolean
}

/** 分类得分 vs 基线：按 eval.md §4 类别顺序；分数统一为百分数。 */
export function categoryBars(run: Pick<EvalRun, 'byCategory'> | null | undefined): CategoryBar[] {
  return [...(run?.byCategory ?? [])]
    .sort((a, b) => orderOf(a.category) - orderOf(b.category))
    .map((c) => {
      const score = toPct(c.score) ?? 0
      const baseline = c.baseline == null ? null : toPct(c.baseline)
      const delta = baseline == null ? null : Math.round((score - baseline) * 10) / 10
      const safety = c.category.startsWith('safety_')
      return {
        category: c.category,
        label: intentLabel(c.category),
        score,
        baseline,
        delta,
        regressed: (delta != null && delta < -CATEGORY_DROP_LIMIT) || (safety && score < 100),
        safety,
      }
    })
}

function fmtPct(v: number | null) {
  return v == null ? '—' : `${v.toFixed(1)}%`
}

/** 版本对比表：候选（cand）对基线（base）。 */
export function compareRuns(base: EvalRun | null, cand: EvalRun | null): MetricRow[] {
  const pct = (r: EvalRun | null, k: 'toolAccuracy' | 'argAccuracy') => (r ? toPct(r[k]) : null)
  const num = (r: EvalRun | null, k: 'pointCoverage' | 'avgCostFen' | 'p95Ms') => (r && Number.isFinite(r[k]) ? r[k] : null)
  const diff = (a: number | null, b: number | null) => (a == null || b == null ? null : b - a)
  const sign = (d: number, s: string) => (d > 0 ? `+${s}` : d < 0 ? `−${s.replace('-', '')}` : '0')

  const tool = [pct(base, 'toolAccuracy'), pct(cand, 'toolAccuracy')] as const
  const arg = [pct(base, 'argAccuracy'), pct(cand, 'argAccuracy')] as const
  // 要点覆盖可能是 0–1 小数或 0–100 分，先统一成百分数再比较
  const ptsOf = (r: EvalRun | null) => {
    const v = num(r, 'pointCoverage')
    return v == null ? null : toPct(v)
  }
  const pts = [ptsOf(base), ptsOf(cand)] as const
  const cost = [num(base, 'avgCostFen'), num(cand, 'avgCostFen')] as const
  const p95 = [num(base, 'p95Ms'), num(cand, 'p95Ms')] as const

  const rows: MetricRow[] = []
  const dTool = diff(...tool)
  rows.push({
    key: 'tool', label: '工具选择正确率', base: fmtPct(tool[0]), cand: fmtPct(tool[1]),
    delta: dTool == null ? '—' : sign(dTool, Math.abs(dTool).toFixed(1)), better: dTool == null ? null : dTool >= 0,
    gate: tool[1] == null ? '—' : tool[1] < TOOL_ACCURACY_GATE ? `低于 ${TOOL_ACCURACY_GATE}%` : `≥ ${TOOL_ACCURACY_GATE}%`,
    gateTone: tool[1] == null ? 'mute' : tool[1] < TOOL_ACCURACY_GATE ? 'err' : 'ok',
  })
  const dArg = diff(...arg)
  rows.push({
    key: 'arg', label: '参数正确率', base: fmtPct(arg[0]), cand: fmtPct(arg[1]),
    delta: dArg == null ? '—' : sign(dArg, Math.abs(dArg).toFixed(1)), better: dArg == null ? null : dArg >= 0,
    gate: '≥ 85%', gateTone: arg[1] == null ? 'mute' : arg[1] < 85 ? 'err' : 'ok',
  })
  const dPts = diff(...pts)
  rows.push({
    key: 'points', label: '要点覆盖（LLM 裁判）',
    base: pts[0] == null ? '—' : pts[0].toFixed(1), cand: pts[1] == null ? '—' : pts[1].toFixed(1),
    delta: dPts == null ? '—' : sign(dPts, Math.abs(dPts).toFixed(1)),
    better: dPts == null ? null : dPts >= 0,
    gate: '≥ 80', gateTone: pts[1] == null ? 'mute' : pts[1] < 80 ? 'err' : 'ok',
  })
  const dCost = diff(...cost)
  rows.push({
    key: 'cost', label: '平均成本 / 轮',
    base: cost[0] == null ? '—' : `¥${(cost[0] / 100).toFixed(3)}`, cand: cost[1] == null ? '—' : `¥${(cost[1] / 100).toFixed(3)}`,
    delta: dCost == null || !cost[0] ? '—' : sign(dCost, `${Math.round((Math.abs(dCost) / cost[0]) * 100)}%`),
    better: dCost == null ? null : dCost <= 0, gate: '观察', gateTone: 'mute',
  })
  const dP95 = diff(...p95)
  rows.push({
    key: 'p95', label: 'P95 耗时',
    base: p95[0] == null ? '—' : `${(p95[0] / 1000).toFixed(1)} s`, cand: p95[1] == null ? '—' : `${(p95[1] / 1000).toFixed(1)} s`,
    delta: dP95 == null ? '—' : sign(dP95, `${(Math.abs(dP95) / 1000).toFixed(1)} s`),
    better: dP95 == null ? null : dP95 <= 0,
    gate: '≤ 1.5 s', gateTone: p95[1] == null ? 'mute' : p95[1] > 1500 ? 'err' : 'ok',
  })
  return rows
}

export type ToolMark = 'ok' | 'missing' | 'extra' | 'arg'

const baseName = (t: string) => t.split('(')[0].trim()

/**
 * 期望 vs 实际工具序列：完全一致 ok；同名不同参 arg；期望有实际无 missing；实际多出 extra。
 */
export function diffTools(expected: string[], actual: string[]) {
  const exp = expected ?? []
  const act = actual ?? []
  const actBases = new Set(act.map(baseName))
  const expBases = new Set(exp.map(baseName))
  return {
    expected: exp.map((t) => ({ tool: t, mark: (act.includes(t) ? 'ok' : actBases.has(baseName(t)) ? 'arg' : 'missing') as ToolMark })),
    actual: act.map((t) => ({ tool: t, mark: (exp.includes(t) ? 'ok' : expBases.has(baseName(t)) ? 'arg' : 'extra') as ToolMark })),
  }
}

/** 近 N 次运行（按时间升序取最后 n 条）。 */
export function lastRuns(runs: EvalRun[], n = 10): EvalRun[] {
  return [...(runs ?? [])].sort((a, b) => (a.createdAt < b.createdAt ? -1 : a.createdAt > b.createdAt ? 1 : 0)).slice(-n)
}

export interface TrendGeom {
  min: number
  max: number
  /** 每个序列的折线点 */
  series: { x: number; y: number; v: number }[][]
  gateY: number
  yTicks: { v: number; y: number }[]
}

/** 趋势图坐标：values 为百分数序列，y 轴范围含门禁线，上下各留 2 个点余量。 */
export function trendGeometry(seriesValues: number[][], w: number, h: number, gate = TOOL_ACCURACY_GATE, pad = { l: 44, r: 16, t: 12, b: 28 }): TrendGeom {
  const all = seriesValues.flat().filter((v) => Number.isFinite(v))
  const min = Math.floor(Math.min(gate, ...(all.length ? all : [gate])) - 2)
  const max = Math.ceil(Math.max(gate, ...(all.length ? all : [gate])) + 2)
  const n = Math.max(1, ...seriesValues.map((s) => s.length))
  const iw = w - pad.l - pad.r
  const ih = h - pad.t - pad.b
  const xAt = (i: number) => pad.l + (n === 1 ? iw / 2 : (iw * i) / (n - 1))
  const yAt = (v: number) => pad.t + ih - ((v - min) / (max - min || 1)) * ih
  const step = max - min > 12 ? 5 : 2
  const yTicks: { v: number; y: number }[] = []
  for (let v = Math.ceil(min / step) * step; v <= max; v += step) yTicks.push({ v, y: yAt(v) })
  return {
    min,
    max,
    series: seriesValues.map((s) => s.map((v, i) => ({ x: xAt(i), y: yAt(v), v }))),
    gateY: yAt(gate),
    yTicks,
  }
}

export function polyline(points: { x: number; y: number }[]): string {
  return points.map((p) => `${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' ')
}
