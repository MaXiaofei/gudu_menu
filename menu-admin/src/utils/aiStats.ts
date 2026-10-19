// AI 用量页的聚合（纯函数）。/ai/usage 只有按场景的次数/token/均延迟；
// 成本与 P95 由最近一批调用明细（/ai/call-log）在前端按时间窗口计算，页面上注明「基于最近 N 条」。

export interface UsageRow {
  scene: string
  totalCalls: number
  failCalls: number
  tokensIn: number
  tokensOut: number
  latencyAvgMs: number
}

export interface CallLogLite {
  scene: string
  cost?: number | string | null
  latencyMs?: number | null
  status?: string | null
  createTime: string
}

export const SCENE_LABEL: Record<string, string> = {
  nutrition_fill: '营养补全',
  menu_recommend: '首页智荐',
  dish_estimate: '菜品估营养',
}

export function sceneLabel(scene: string): string {
  return SCENE_LABEL[scene] ?? scene
}

/** 第 p 百分位（最近秩法）；空数组返回 null。 */
export function percentile(values: number[], p: number): number | null {
  const v = values.filter((x) => Number.isFinite(x)).sort((a, b) => a - b)
  if (v.length === 0) return null
  const rank = Math.ceil((p / 100) * v.length)
  return v[Math.min(Math.max(rank, 1), v.length) - 1]
}

export interface UsageSummary {
  totalCalls: number
  failCalls: number
  successRate: number | null
  tokens: number
}

export function summarizeUsage(rows: UsageRow[] | null | undefined): UsageSummary {
  const list = Array.isArray(rows) ? rows : []
  const totalCalls = list.reduce((s, r) => s + (Number(r.totalCalls) || 0), 0)
  const failCalls = list.reduce((s, r) => s + (Number(r.failCalls) || 0), 0)
  const tokens = list.reduce((s, r) => s + (Number(r.tokensIn) || 0) + (Number(r.tokensOut) || 0), 0)
  return {
    totalCalls,
    failCalls,
    successRate: totalCalls > 0 ? (totalCalls - failCalls) / totalCalls : null,
    tokens,
  }
}

/** 时间窗口内的调用：最近 days 天（含今天）。 */
export function withinDays<T extends { createTime: string }>(logs: T[], days: number, now = new Date()): T[] {
  const since = now.getTime() - days * 86400000
  return logs.filter((l) => {
    const t = new Date(l.createTime.includes('T') ? l.createTime : l.createTime.replace(' ', 'T')).getTime()
    return Number.isFinite(t) && t >= since
  })
}

export interface SampleStats {
  sampleSize: number
  costYuan: number
  p95Ms: number | null
  /** P95 最慢的场景 */
  slowestScene: string | null
  costByScene: Record<string, number>
}

export function sampleStats(logs: CallLogLite[]): SampleStats {
  const costByScene: Record<string, number> = {}
  const latencyByScene: Record<string, number[]> = {}
  let costYuan = 0
  const lat: number[] = []
  for (const l of logs) {
    const c = Number(l.cost ?? 0)
    if (Number.isFinite(c)) {
      costYuan += c
      costByScene[l.scene] = (costByScene[l.scene] ?? 0) + c
    }
    if (l.latencyMs != null && l.status !== 'fail') {
      lat.push(l.latencyMs)
      ;(latencyByScene[l.scene] ??= []).push(l.latencyMs)
    }
  }
  let slowestScene: string | null = null
  let slowest = -1
  for (const [scene, v] of Object.entries(latencyByScene)) {
    const p = percentile(v, 95) ?? -1
    if (p > slowest) {
      slowest = p
      slowestScene = scene
    }
  }
  return { sampleSize: logs.length, costYuan, p95Ms: percentile(lat, 95), slowestScene, costByScene }
}

export function formatMs(ms: number | null | undefined): string {
  if (ms == null || !Number.isFinite(ms)) return '-'
  return ms >= 1000 ? `${(ms / 1000).toFixed(1)} 秒` : `${Math.round(ms)} ms`
}
