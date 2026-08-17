import { request } from '@/utils/request'

/** 组合推荐里的一道菜（servingFactor 份数系数）。 */
export interface RecommendDish {
  dishId: number
  name: string
  servingFactor?: number | null
}

/** 一组推荐组合（菜 + 中文推荐理由；后端规则引擎纯函数，无 LLM）。 */
export interface RecommendGroup {
  dishes: RecommendDish[]
  totalNutrition?: Record<string, number>
  score?: number
  reasons: string[]
  source?: string
}

/** 组合推荐：POST /ai/menu/recommend（成员口味画像 + 偏好文本 → 向量召回 → 打分组合）。 */
export function recommendMenu(memberId: number, preference?: string): Promise<RecommendGroup[]> {
  return request<RecommendGroup[]>({
    url: '/ai/menu/recommend',
    method: 'POST',
    data: {
      memberId,
      // 偏好仅在非空时传：空则后端按「家常菜」兜底
      ...(preference?.trim() ? { preference: preference.trim() } : {}),
    },
  })
}
