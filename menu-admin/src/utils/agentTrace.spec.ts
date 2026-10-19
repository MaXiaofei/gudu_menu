import { describe, it, expect } from 'vitest'
import type { Span } from '@/api/agentAdmin'
import { cardSummary, countMaskedPhones, fenToYuan, formatMs, formatPct, isPendingAction, layoutWaterfall, toPct, traceTime } from './agentTrace'

const sp = (o: Partial<Span>): Span => ({
  spanId: o.spanId ?? 's', parentId: null, type: 'llm', name: 'x', startMs: 0, ms: 0,
  tokens: null, costFen: null, ok: true, input: null, output: null, ...o,
})

describe('layoutWaterfall', () => {
  const spans = [
    sp({ spanId: 'b', type: 'llm', name: 'llm.plan', startMs: 12, ms: 820 }),
    sp({ spanId: 'a', type: 'guard', name: 'guard.input', startMs: 0, ms: 12 }),
    sp({ spanId: 'c', type: 'llm', name: 'llm.respond', startMs: 1000, ms: 1400 }),
    sp({ spanId: 'd', type: 'action', name: 'action.add_to_menu', startMs: 2400, ms: 0, output: { status: 'PENDING' } }),
  ]

  it('总长 = 最晚结束时间，按 startMs 排序，比例正确', () => {
    const l = layoutWaterfall(spans)
    expect(l.totalMs).toBe(2400)
    expect(l.rows.map((r) => r.span.spanId)).toEqual(['a', 'b', 'c', 'd'])
    const plan = l.rows[1]
    expect(plan.leftPct).toBeCloseTo(0.5, 2)
    expect(plan.widthPct).toBeCloseTo(34.17, 1)
    expect(l.ticks).toEqual([0, 600, 1200, 1800, 2400])
  })

  it('过短 span 保底宽度，越界时左移贴右', () => {
    const l = layoutWaterfall(spans, 1)
    const guard = l.rows[0]
    expect(guard.widthPct).toBe(1)
    const last = l.rows[3]
    expect(last.widthPct).toBe(6) // 待确认写操作保底 6%
    expect(last.leftPct + last.widthPct).toBeLessThanOrEqual(100)
    expect(last.leftPct).toBe(94)
  })

  it('待确认写操作识别为 pending', () => {
    const l = layoutWaterfall(spans)
    expect(l.rows.filter((r) => r.pending).map((r) => r.span.spanId)).toEqual(['d'])
    expect(isPendingAction({ type: 'action', output: { status: 'DONE' } })).toBe(false)
    expect(isPendingAction({ type: 'tool', output: { status: 'PENDING' } })).toBe(false)
  })

  it('空数组 / 非法数据不抛错', () => {
    const l = layoutWaterfall([])
    expect(l.totalMs).toBe(1)
    expect(l.rows).toEqual([])
    expect(layoutWaterfall([sp({ startMs: NaN })]).rows).toEqual([])
  })
})

describe('格式化', () => {
  it('耗时 / 成本 / 比率', () => {
    expect(formatMs(12)).toBe('12 ms')
    expect(formatMs(1400)).toBe('1.40 s')
    expect(formatMs(null)).toBe('—')
    expect(fenToYuan(1840)).toBe('¥18.40')
    expect(fenToYuan(1.4)).toBe('¥0.014')
    expect(fenToYuan(0)).toBe('¥0.00')
    expect(toPct(0.972)).toBeCloseTo(97.2)
    expect(toPct(97.2)).toBe(97.2)
    expect(formatPct(0.972)).toBe('97.2%')
  })

  it('掩码手机号计数（字符串与对象）', () => {
    expect(countMaskedPhones({ phone: '138****6621', other: '13800001111' }, '联系 139****0000')).toBe(2)
    expect(countMaskedPhones(null)).toBe(0)
  })

  it('traceTime：今天只显示时分秒', () => {
    const now = new Date(2026, 9, 16, 22, 0, 0)
    expect(traceTime('2026-10-16T21:42:17', now)).toBe('21:42:17')
    expect(traceTime('2026-10-15T08:01:00', now)).toBe('10-15 08:01')
  })
})

describe('cardSummary', () => {
  it('四类卡片与未知类型', () => {
    expect(cardSummary({ type: 'recipes', items: [{ name: '清蒸鲈鱼' }, { name: '凉拌菠菜' }] })).toBe('菜谱卡 · 清蒸鲈鱼、凉拌菠菜')
    expect(cardSummary({ type: 'pantry', items: [{}, {}, {}] })).toBe('余量卡 · 3 样')
    expect(cardSummary({ type: 'menu', name: '今晚的饭' })).toBe('食集卡 · 今晚的饭')
    expect(cardSummary({ type: 'plan', range: '10.19–10.25' })).toBe('餐程卡 · 10.19–10.25')
    expect(cardSummary({ type: 'weird' })).toBe('weird 卡')
  })
})
