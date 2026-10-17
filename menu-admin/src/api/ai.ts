import { request } from './request'
import type { UsageRow } from '@/utils/aiStats'
import type { Page } from './dish'

// V2 AI 入口
// memberId 后端从 session 取，前端不传（需登录 + 设当前就餐成员）

export interface AiNutritionItem {
  metricId: number
  value: number
}
export interface AiNutritionFillResult {
  nutrition: AiNutritionItem[]
  source: string // 模型来源标识（如 glm）
}

/** AI 补全营养：输入食材名 → 返回 per100g 估算值（metricId+value），结果需人工核对后保存。
 *  后台辅助用途：批量录食材时一键填营养。菜单推荐小程序为主，后台不做。 */
export function aiFillNutrition(name: string) {
  return request<AiNutritionFillResult>({ url: '/ai/nutrition/fill', method: 'post', data: { name } })
}

// ---------------- 用量与调用日志（超管） ----------------

export interface AiCallLog {
  id: number
  scene: string
  memberId: number | null
  request: string | null
  response: string | null
  tokensIn: number | null
  tokensOut: number | null
  cost: number | null
  provider: string | null
  latencyMs: number | null
  status: string
  errorMsg: string | null
  createTime: string
}

export interface AiProviderInfo {
  current: string
  providers: string[]
  ready: { provider: string; ready: boolean }[]
}

/** 按场景汇总：调用次数 / 失败 / token / 均延迟（days 天内）。 */
export function getAiUsage(days: number) {
  return request<UsageRow[]>({ url: '/ai/usage', method: 'get', params: { days } })
}

export function listAiCallLogs(params: { pageNum: number; pageSize: number; scene?: string; status?: string }) {
  const p: Record<string, unknown> = { pageNum: params.pageNum, pageSize: params.pageSize }
  if (params.scene) p.scene = params.scene
  if (params.status) p.status = params.status
  return request<Page<AiCallLog>>({ url: '/ai/call-log', method: 'get', params: p })
}

export function getAiProvider() {
  return request<AiProviderInfo>({ url: '/ai/provider', method: 'get' })
}
