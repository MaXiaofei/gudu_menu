/**
 * 跟做模式：从步骤文字解析时长 → 计时器（cook-mode spec「步骤计时器自动生成且可并行」）。
 * 支持：「8 分钟」「3:00」「半小时」「1 小时 20 分钟」「30 秒」「十分钟」「两分钟」「3-5 分钟」（取上限）。
 */

export interface StepTimer {
  /** 原文片段，如「小火煮 3 分钟」里的「3 分钟」 */
  text: string
  seconds: number
}

const CN: Record<string, number> = { 零: 0, 一: 1, 二: 2, 两: 2, 三: 3, 四: 4, 五: 5, 六: 6, 七: 7, 八: 8, 九: 9, 十: 10 }

/** 中文数字（≤ 99）或阿拉伯数字 → number；无法识别返回 NaN。 */
export function cnNumber(s: string): number {
  const t = s.trim()
  if (/^\d+(\.\d+)?$/.test(t)) return Number(t)
  if (t === '半') return 0.5
  if (!t || [...t].some((c) => !(c in CN))) return NaN
  if (t.includes('十')) {
    const [a, b] = t.split('十')
    return (a ? CN[a] : 1) * 10 + (b ? CN[b] : 0)
  }
  return [...t].reduce((n, c) => n * 10 + CN[c], 0)
}

const NUM = '(?:\\d+(?:\\.\\d+)?|[零一二两三四五六七八九十]+|半)'
const RANGE = `${NUM}(?:\\s*[-~～到至]\\s*${NUM})?`
// 复合：「1 小时 20 分钟」「一个半小时」
const RE = new RegExp(
  [
    `(\\d{1,2}):(\\d{2})(?!\\d)`, // 3:00 / 12:40
    `(${RANGE})\\s*个?\\s*(半)?\\s*(小时|钟头)(?:\\s*(${RANGE})\\s*分(?:钟)?)?`,
    `(${RANGE})\\s*分(?:钟)?(?:\\s*(${NUM})\\s*秒)?`,
    `(${RANGE})\\s*秒(?:钟)?`,
  ].join('|'),
  'g',
)

function upper(range: string): number {
  const parts = range.split(/\s*[-~～到至]\s*/)
  return cnNumber(parts[parts.length - 1])
}

export function parseStepTimers(text: string): StepTimer[] {
  const out: StepTimer[] = []
  if (!text) return out
  for (const m of text.matchAll(RE)) {
    let sec = NaN
    if (m[1] !== undefined) sec = Number(m[1]) * 60 + Number(m[2])
    else if (m[3] !== undefined) {
      sec = upper(m[3]) * 3600 + (m[4] ? 1800 : 0) + (m[6] ? upper(m[6]) * 60 : 0)
    } else if (m[7] !== undefined) sec = upper(m[7]) * 60 + (m[8] ? cnNumber(m[8]) : 0)
    else if (m[9] !== undefined) sec = upper(m[9])
    if (Number.isFinite(sec) && sec > 0) out.push({ text: m[0].trim(), seconds: Math.round(sec) })
  }
  return out
}

/** 秒 → 「3:00」「12:40」「1:02:03」。 */
export function formatClock(seconds: number): string {
  const s = Math.max(0, Math.round(seconds))
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const ss = String(s % 60).padStart(2, '0')
  return h ? `${h}:${String(m).padStart(2, '0')}:${ss}` : `${m}:${ss}`
}

export interface RunningTimer {
  id: string
  label: string
  total: number
  /** 结束时间戳（ms） */
  endAt: number
}

/** 剩余秒数（到点为 0）。 */
export function remaining(t: RunningTimer, now = Date.now()): number {
  return Math.max(0, Math.ceil((t.endAt - now) / 1000))
}

/** 「快到点」：剩余 ≤ 60 秒且未结束。 */
export function isHot(t: RunningTimer, now = Date.now()): boolean {
  const r = remaining(t, now)
  return r > 0 && r <= 60
}

/** 进度 0~1（已过去比例）。 */
export function progress(t: RunningTimer, now = Date.now()): number {
  return t.total ? Math.min(1, 1 - remaining(t, now) / t.total) : 1
}
