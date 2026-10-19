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
