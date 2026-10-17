import type { Span, SpanType, TraceStatus } from '@/api/agentAdmin'

// Agent 调用链：瀑布图布局、类型着色、脱敏识别与数值格式化（纯函数，便于单测）

export interface WaterfallRow {
  span: Span
  /** 条形起点（占时间轴宽度的百分比） */
  leftPct: number
  /** 条形宽度百分比（过短的 span 保底可见宽度） */
  widthPct: number
  /** 写操作待用户确认：虚线斜纹条 */
  pending: boolean
}

export interface WaterfallLayout {
  totalMs: number
  rows: WaterfallRow[]
  /** 刻度（ms），含 0 与 totalMs，共 5 个 */
  ticks: number[]
}

/** 待确认写操作条形的最小宽度（%）。 */
export const PENDING_MIN_PCT = 6

/** 待确认的写操作：action span 且输出状态为 PENDING。 */
export function isPendingAction(s: Pick<Span, 'type' | 'output'>): boolean {
  if (s.type !== 'action') return false
  const out = s.output as { status?: unknown } | null | undefined
  return !!out && typeof out === 'object' && out.status === 'PENDING'
}

/**
 * 按 startMs / ms 计算瀑布图。时间轴总长 = 所有 span 的最晚结束时间；
 * 宽度不足 minWidthPct 的条形补到最小可见宽度，补宽后越界则左移贴齐右端。
 */
export function layoutWaterfall(spans: Span[], minWidthPct = 0.6): WaterfallLayout {
  const valid = (spans ?? []).filter((s) => s && Number.isFinite(s.startMs) && Number.isFinite(s.ms))
  const totalMs = Math.max(1, ...valid.map((s) => Math.max(0, s.startMs) + Math.max(0, s.ms)))
  const rows = valid
    .map((span, i) => ({ span, i }))
    .sort((a, b) => a.span.startMs - b.span.startMs || a.i - b.i)
    .map(({ span }) => {
      const start = Math.max(0, span.startMs)
      const pending = isPendingAction(span)
      // 待确认的写操作耗时为 0，给足宽度让虚线斜纹块可辨认
      const minW = pending ? Math.max(minWidthPct, PENDING_MIN_PCT) : minWidthPct
      const widthPct = Math.min(100, Math.max(minW, (Math.max(0, span.ms) / totalMs) * 100))
      let leftPct = (start / totalMs) * 100
      if (leftPct + widthPct > 100) leftPct = 100 - widthPct
      return { span, leftPct: round2(leftPct), widthPct: round2(widthPct), pending }
    })
  const ticks = [0, 1, 2, 3, 4].map((k) => Math.round((totalMs * k) / 4))
  return { totalMs, rows, ticks }
}

function round2(n: number) {
  return Math.round(n * 100) / 100
}

export const SPAN_TYPE_LABEL: Record<SpanType, string> = {
  llm: '模型',
  retrieval: '检索',
  tool: '工具',
  guard: '护栏',
  action: '写操作（需确认）',
}

export const TRACE_STATUS: Record<TraceStatus, { label: string; tone: 'ok' | 'err' | 'warn' | 'info' }> = {
  OK: { label: '完成', tone: 'ok' },
  DOWNVOTED: { label: '差评', tone: 'err' },
  GUARDED: { label: '护栏拦截', tone: 'warn' },
  DEGRADED: { label: '已降级', tone: 'info' },
}

/** 统计输入输出里已掩码的手机号（如 138****6621）。 */
export function countMaskedPhones(...values: unknown[]): number {
  let n = 0
  for (const v of values) {
    const s = typeof v === 'string' ? v : safeJson(v)
    n += (s.match(/1\d{2}\*{4}\d{4}/g) ?? []).length
  }
  return n
}

/** JSON 美化（展示用）；循环引用等异常回退 String。 */
export function safeJson(v: unknown, space?: number): string {
  if (v === undefined) return ''
  try {
    return JSON.stringify(v, null, space) ?? String(v)
  } catch {
    return String(v)
  }
}

/** 12 → "12 ms"；1400 → "1.40 s"。 */
export function formatMs(ms: number | null | undefined): string {
  if (ms == null || !Number.isFinite(ms)) return '—'
  return ms < 1000 ? `${Math.round(ms)} ms` : `${(ms / 1000).toFixed(2)} s`
}

/** 刻度标签：0 / 0.65 s / 1.30 s。 */
export function formatTick(ms: number): string {
  if (ms === 0) return '0'
  return ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(2)} s`
}

/** 分 → 元；小额（< 1 元）保留 3 位，避免每轮成本显示成 ¥0.01。 */
export function fenToYuan(fen: number | null | undefined, digits?: number): string {
  if (fen == null || !Number.isFinite(fen)) return '—'
  const yuan = fen / 100
  const d = digits ?? (Math.abs(yuan) < 1 && yuan !== 0 ? 3 : 2)
  return `¥${yuan.toFixed(d)}`
}

/** 比率：0–1 视为小数，>1 视为已是百分数。 */
export function toPct(v: number | null | undefined): number | null {
  if (v == null || !Number.isFinite(v)) return null
  return v <= 1 ? v * 100 : v
}

export function formatPct(v: number | null | undefined, digits = 1): string {
  const p = toPct(v)
  return p == null ? '—' : `${p.toFixed(digits)}%`
}

export function formatInt(n: number | null | undefined): string {
  return n == null || !Number.isFinite(n) ? '—' : n.toLocaleString('en-US')
}

/** 2026-10-16T21:42:17 → 21:42:17（今天）或 10-15 21:42。 */
export function traceTime(iso: string, now = new Date()): string {
  const s = (iso || '').replace('T', ' ')
  const day = s.slice(0, 10)
  const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
  return day === today ? s.slice(11, 19) : s.slice(5, 16)
}

/** 完整对话里的卡片摘要：菜谱卡列菜名，余量卡计数，食集 / 餐程卡取名称或范围。 */
export function cardSummary(c: { type: string; [k: string]: unknown }): string {
  const items = Array.isArray(c.items) ? (c.items as { name?: unknown }[]) : []
  const str = (v: unknown) => (typeof v === 'string' ? v : '')
  if (c.type === 'recipes') return `菜谱卡 · ${items.map((i) => str(i?.name)).filter(Boolean).join('、')}`
  if (c.type === 'pantry') return `余量卡 · ${items.length} 样`
  if (c.type === 'menu') return `食集卡 · ${str(c.name)}`
  if (c.type === 'plan') return `餐程卡 · ${str(c.range)}`
  return `${c.type} 卡`
}
