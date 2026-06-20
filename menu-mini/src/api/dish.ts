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

// URL 导入菜谱：后端 POST /dish/import-url?url=xxx 抓网页解析落库，返回新菜品 id
export const importDishByUrl = (url: string) =>
  request<number>({ url: `/dish/import-url?url=${encodeURIComponent(url)}`, method: 'POST' })

// 营养指标字典：[{id,name,unit,metricGroup,sort}]，把 nutrition 的 id(→值) 映射成「名字: 值(单位)」
export const nutritionMetrics = () => request<any[]>({ url: '/nutrition/metric', method: 'GET' })

// ============ 反向找菜（勾选食材 → 推荐能做的菜） ============

// 后端 CookbookService.DishMatch：{ dish:{id,name,...}, matchCount, totalCount, missingIngredients:[name], canMake }
export interface DishMatch {
  dish: { id: number; name: string; coverUrl?: string; prepTime?: number; cookTime?: number; difficulty?: number }
  matchCount: number
  totalCount: number
  missingIngredients: string[]
  canMake: boolean
}

/**
 * 反向找菜：传「我有的食材 id」逗号分隔，返回可做/部分可做的菜（全匹配优先）。
 * 后端 GET /cookbook/by-ingredients?ingredientIds=1,2,3 → R<List<DishMatch>>
 */
export const findDishesByIngredients = (ingredientIds: number[]) =>
  request<DishMatch[]>({
    url: `/cookbook/by-ingredients`,
    method: 'GET',
    data: { ingredientIds: ingredientIds.join(',') },
  })
