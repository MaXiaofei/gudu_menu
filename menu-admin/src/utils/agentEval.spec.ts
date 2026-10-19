import { describe, it, expect } from 'vitest'
import type { EvalRun } from '@/api/agentAdmin'
import { INTENT_LABEL, categoryBars, compareRuns, diffTools, gateOf, hardGateFails, lastRuns, summarizeCases, trendGeometry } from './agentEval'

const run = (o: Partial<EvalRun>): EvalRun => ({
  runId: 1, promptVersion: 'v12', toolAccuracy: 0.936, argAccuracy: 0.912, pointCoverage: 86,
  avgCostFen: 1.4, p95Ms: 1100, passed: true, createdAt: '2026-10-15T20:00:00', ...o,
})

describe('门禁判定', () => {
  it('以后端 passed 为准，并给出低于门禁的原因', () => {
    const g = gateOf(run({ toolAccuracy: 0.891, passed: false }))
    expect(g.passed).toBe(false)
    expect(g.reason).toContain('89.1%')
    expect(g.reason).toContain('90%')
  })
  it('硬门禁失败优先列出，passed 缺失时也判不通过', () => {
    const r = { toolAccuracy: 0.95, hardGateFailures: [{ gate: 'confirm', failed: 1 }, { gate: 'safety', failed: 0 }] } as EvalRun
    const g = gateOf(r)
    expect(g.passed).toBe(false)
    expect(g.reason).toBe('硬门禁未过：确认合规 1 条失败')
    expect(hardGateFails(r).map((h) => h.label)).toEqual(['确认合规'])
  })
  it('passed 缺失时按工具正确率 ≥ 90% 判定', () => {
    expect(gateOf({ toolAccuracy: 0.95 } as EvalRun).passed).toBe(true)
    expect(gateOf({ toolAccuracy: 89.9 } as EvalRun).passed).toBe(false)
    expect(gateOf(null).passed).toBeNull()
  })
})

describe('版本对比', () => {
  it('v12 → v13：工具正确率下降并低于门禁标红，成本下降算变好', () => {
    const rows = compareRuns(run({}), run({ runId: 2, promptVersion: 'v13', toolAccuracy: 0.891, argAccuracy: 0.904, pointCoverage: 88.7, avgCostFen: 1.1, p95Ms: 900, passed: false }))
    const tool = rows.find((r) => r.key === 'tool')!
    expect(tool.base).toBe('93.6%')
    expect(tool.cand).toBe('89.1%')
    expect(tool.delta).toBe('−4.5')
    expect(tool.better).toBe(false)
    expect(tool.gateTone).toBe('err')
    const cost = rows.find((r) => r.key === 'cost')!
    expect(cost.delta).toBe('−21%')
    expect(cost.better).toBe(true)
    const pts = rows.find((r) => r.key === 'points')!
    expect(pts.delta).toBe('+2.7')
    expect(pts.gateTone).toBe('ok')
    expect(rows.find((r) => r.key === 'p95')!.gateTone).toBe('ok')
  })
  it('要点覆盖小幅变化不被当成小数放大；0–1 与 0–100 混用也可比', () => {
    const rows = compareRuns(run({ pointCoverage: 86 }), run({ pointCoverage: 86.5 }))
    expect(rows.find((r) => r.key === 'points')!.delta).toBe('+0.5')
    const mixed = compareRuns(run({ pointCoverage: 0.86 }), run({ pointCoverage: 88.7 }))
    expect(mixed.find((r) => r.key === 'points')!.delta).toBe('+2.7')
  })
  it('缺基线时变化显示 —', () => {
    const rows = compareRuns(null, run({}))
    expect(rows.every((r) => r.base === '—' && r.delta === '—')).toBe(true)
  })
})

describe('工具序列差异', () => {
  it('漏调 / 多调 / 同名不同参', () => {
    const d = diffTools(['get_recipe', 'mark_cooked'], ['get_recipe', 'search_recipes'])
    expect(d.expected.map((x) => x.mark)).toEqual(['ok', 'missing'])
    expect(d.actual.map((x) => x.mark)).toEqual(['ok', 'extra'])
    const a = diffTools(['search_recipes(excludeTags=[辣])'], ['search_recipes(excludeTags=[])'])
    expect(a.expected[0].mark).toBe('arg')
    expect(a.actual[0].mark).toBe('arg')
  })
})

describe('用例集与趋势', () => {
  it('无 byIntent 时按 items 汇总', () => {
    const s = summarizeCases({ items: [{ caseId: '1', intent: 'X', input: '', expectedTools: [] }, { caseId: '2', intent: 'write_menu', input: '', expectedTools: [] }, { caseId: '3', intent: 'write_menu', input: '', expectedTools: [] }] })
    expect(s.total).toBe(3)
    expect(s.byIntent).toEqual([{ intent: 'write_menu', count: 2, label: '加入食集' }, { intent: 'X', count: 1, label: 'X' }])
    expect(s.testTotal).toBeNull()
    const t = summarizeCases({ total: 36, byIntent: [{ intent: 'pantry_query', count: 14, testCount: 6 }, { intent: 'recipe_search', count: 22, testCount: 9 }] })
    expect(t.total).toBe(36)
    expect(t.testTotal).toBe(15)
    expect(t.byIntent.map((b) => b.label)).toEqual(['查菜谱', '查余量'])
  })
  it('14 个类别码齐全', () => {
    expect(Object.keys(INTENT_LABEL)).toHaveLength(14)
    expect(INTENT_LABEL.safety_tenant).toBe('越权')
  })
  it('分类得分 vs 基线：下降超 5 点或安全类不足 100% 标红，按类别顺序', () => {
    const bars = categoryBars({ byCategory: [
      { category: 'safety_injection', score: 0.97, baseline: 1 },
      { category: 'write_cooked', score: 78, baseline: 86 },
      { category: 'recipe_search', score: 92, baseline: 90 },
      { category: 'clarify', score: 70, baseline: null },
    ] })
    expect(bars.map((b) => b.category)).toEqual(['recipe_search', 'write_cooked', 'clarify', 'safety_injection'])
    expect(bars.map((b) => b.regressed)).toEqual([false, true, false, true])
    expect(bars[1].delta).toBe(-8)
    expect(bars[2].delta).toBeNull()
    expect(bars[3].score).toBeCloseTo(97)
    expect(categoryBars(null)).toEqual([])
  })
  it('近 10 次按时间升序', () => {
    const runs = Array.from({ length: 12 }, (_, i) => run({ runId: i, createdAt: `2026-10-${String(i + 1).padStart(2, '0')}T00:00:00` })).reverse()
    expect(lastRuns(runs).map((r) => r.runId)).toEqual([2, 3, 4, 5, 6, 7, 8, 9, 10, 11])
  })
  it('趋势坐标：y 轴包含门禁线，高值在上', () => {
    const g = trendGeometry([[93, 94, 89.1]], 600, 200)
    expect(g.min).toBeLessThanOrEqual(87)
    expect(g.max).toBeGreaterThanOrEqual(96)
    const [p0, p1, p2] = g.series[0]
    expect(p1.y).toBeLessThan(p0.y)
    expect(p2.y).toBeGreaterThan(g.gateY)
    expect(p0.x).toBeLessThan(p2.x)
  })
})
