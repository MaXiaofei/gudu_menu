import { request } from '@/utils/request'

// V2 AI 入口（后端 mock，待接 GLM）
// memberId 后端从 session 取，前端不传（需登录 + 设当前就餐成员 @MpPerm("ai.use")）

export interface AiNutritionItem {
  metricId: number
  value: number
}
export interface AiNutritionFillResult {
  nutrition: AiNutritionItem[]
  source: string // "mock"
}

/** AI 补全营养：输入食材名 → 返回 6 项 per100g（metricId+value），source=mock 待接 GLM */
export const aiFillNutrition = (name: string) =>
  request<AiNutritionFillResult>({ url: '/ai/nutrition/fill', method: 'POST', data: { name } })

export interface AiRecommendDish {
  dishId: number
  name: string
  servingFactor?: number
  price?: number
}
export interface AiRecommendGroup {
  dishes: AiRecommendDish[]
  totalPrice: number
  totalNutrition: Record<string, number> // metricId -> value
  score?: number
  reasons?: string[]
  source?: string
}

/** AI 推荐菜单：budget/scope(DAY|WEEK)/筛选条件 → 候选组（memberId 后端 session 取） */
export const aiRecommendMenu = (params: {
  budget?: number
  scope?: 'DAY' | 'WEEK'
  cuisineIds?: number[]
  tagIds?: number[]
  categoryIds?: number[]
  maxMinutes?: number
  maxDifficulty?: number
}) => request<AiRecommendGroup[]>({ url: '/ai/menu/recommend', method: 'POST', data: { ...params } })
