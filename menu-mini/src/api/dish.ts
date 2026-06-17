import { request } from '@/utils/request'

export const searchDishes = (params: Record<string, any>) =>
  request<any>({ url: '/dish/search', method: 'GET', data: params })

// 详情：后端 DishDetail record → { dish, steps, cuisineIds, tagIds, categoryIds, ingredients }
export const dishDetail = (id: number) =>
  request<any>({ url: `/dish/${id}`, method: 'GET' })

// 份数营养：后端返回 Map<Long指标id, BigDecimal值>（无指标名，名映射留 C1）
export const dishNutrition = (id: number, serving = 1) =>
  request<Record<string, any>>({ url: `/dish/${id}/nutrition?serving=${serving}`, method: 'GET' })

// 标记做过：memberId 必填（MVP cookbook 接口要求）
export const markDone = (dishId: number, memberId: number, note?: string) =>
  request({ url: `/cookbook/done/${dishId}?memberId=${memberId}&note=${encodeURIComponent(note || '')}`, method: 'POST' })

// 录入新菜：后端 POST /dish @RequestBody DishSaveDTO { dish, steps, cuisineIds, tagIds, categoryIds, ingredients }
// V1 第一批：仅 dish + steps（关联/食材 YAGNI 留第二批）
export const saveDish = (data: any) => request({ url: '/dish', method: 'POST', data })
