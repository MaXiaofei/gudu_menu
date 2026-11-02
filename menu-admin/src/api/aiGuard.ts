import { request } from './request'

/**
 * AI 用量与额度：全站每日花费上限 + 各类每账号每日次数上限。
 * 后端 GET/PUT /admin/ai-guard（运营 / 超管，与功能开关同权限）。
 */
export type AiGuardLevel = 'NORMAL' | 'WARN' | 'PAUSED'
export type AiGuardCategoryKey = 'vision' | 'mealplan' | 'agent' | 'other'

export interface AiGuardCategory {
  key: AiGuardCategoryKey
  /** 拍照识别 / 一周餐程 / 咕嘟对话 / 其他 AI */
  label: string
  /** 今日全站被放行的次数 */
  todayCount: number
  /** 每账号每日上限，0 = 不限 */
  limitPerAccount: number
}

export interface AiGuardLog {
  /** budget.daily_yuan / budget.warn_percent / limit.vision … */
  key: string
  oldValue: string | null
  newValue: string
  operatorId: number
  operatorName: string | null
  at: string
}

export interface AiGuardOverview {
  /** 今日已花费（元） */
  spentYuan: number
  /** 每日上限（元），0 = 不限 */
  limitYuan: number
  percent: number
  level: AiGuardLevel
  warnPercent: number
  categories: AiGuardCategory[]
  /** Redis 读不到时为 false，次数显示 —— */
  countsAvailable: boolean
  logs: AiGuardLog[]
}

export interface AiGuardUpdate {
  dailyBudgetYuan?: number
  warnPercent?: number
  limits?: Partial<Record<AiGuardCategoryKey, number>>
}

export function getAiGuard() {
  return request<AiGuardOverview>({ url: '/admin/ai-guard', method: 'get' })
}

export function updateAiGuard(body: AiGuardUpdate) {
  return request<AiGuardOverview>({ url: '/admin/ai-guard', method: 'put', data: body })
}
