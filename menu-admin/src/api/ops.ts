import { request } from './request'

// 运营仪表盘（api-contract §8，超管）
export interface OpsTotals {
  members: number
  accounts: number
  dishes: number
  cookRecords: number
  feedbackOpen: number
}
export interface OpsLast7d {
  newAccounts: number
  activeMembers: number
  cookRecords: number
  aiCalls: number
  aiCostYuan: number
}
export interface OpsTrendPoint {
  date: string // YYYY-MM-DD
  newAccounts: number
  activeMembers: number
  cookRecords: number
  aiCalls: number
}
export interface OpsStats {
  totals: OpsTotals
  last7d: OpsLast7d
  trend: OpsTrendPoint[]
}

export function getOpsStats(days = 14) {
  return request<OpsStats>({ url: '/ops/stats', method: 'get', params: { days } })
}

// 系统健康摘要（运营可读，后端 GET /ops/health）：各项探测单项超时 2 秒、互不影响
export interface HealthItem {
  key: 'app' | 'mysql' | 'redis' | 'vector' | 'ai' | string
  label: string
  status: 'ok' | 'err'
  latencyMs: number
  detail: string | null
  error: string | null
}
export interface HealthReport {
  status: 'ok' | 'err'
  items: HealthItem[]
}

export function getOpsHealth() {
  return request<HealthReport>({ url: '/ops/health', method: 'get' })
}

// 重建语义检索向量（仅超管，POST /dish/vector/rebuild；全量 embedding 耗时较长，放宽超时到 10 分钟）
export interface VectorRebuildResult {
  total: number
  ok: number
  error: string | null
}

export function rebuildDishVectors() {
  return request<VectorRebuildResult>({ url: '/dish/vector/rebuild', method: 'post', timeout: 600000 })
}
