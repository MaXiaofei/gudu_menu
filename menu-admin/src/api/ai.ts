import { request } from './request'

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
