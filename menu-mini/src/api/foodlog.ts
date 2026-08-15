import { request } from '@/utils/request'

/** 月/年时间轴记录（一顿饭）。 */
export interface FoodLogMeal {
  menuId?: number | null
  name?: string | null
  cookedAt?: string | null
  dishCount?: number
  servingCount?: number | null
  dishNames?: string[]
  usedUpCount?: number
  partialCount?: number
  reviewed?: boolean
}

export interface FoodLogSummary {
  meals: number
  dishes: number
  cookDays: number
  topDishes?: string[]
}

export interface FoodLogMonth {
  summary: FoodLogSummary
  records: FoodLogMeal[]
  total: number
}

/** 按菜汇总行。 */
export interface FoodLogByDish {
  dishId: number
  dishName?: string | null
  count: number
  lastCookedAt?: string | null
  avgStar?: number | null
}

/** 月/年时间轴 + 统计：GET /food-log/month?month=yyyy-MM | yyyy。 */
export function foodLogMonth(month: string, pageNum = 1, pageSize = 10): Promise<FoodLogMonth> {
  return request<FoodLogMonth>({
    url: '/food-log/month',
    method: 'GET',
    data: { month, pageNum, pageSize },
  })
}

/** 按菜汇总：GET /food-log/by-dish?month=（全量不分页）。 */
export function foodLogByDish(month: string): Promise<{ totalKinds: number; items: FoodLogByDish[] }> {
  return request({ url: '/food-log/by-dish', method: 'GET', data: { month } })
}

/** 食记详情。 */
export interface FoodLogDetail {
  menuId?: number | null
  name?: string | null
  cookedAt?: string | null
  servingCount?: number | null
  dishes: { dishId?: number | null; dishName?: string | null }[]
  usedUp: string[]
  partial: string[]
  reviewed?: boolean
}

export function foodLogDetail(menuId: number): Promise<FoodLogDetail> {
  return request<FoodLogDetail>({ url: '/food-log/detail', method: 'GET', data: { menuId } })
}

/** 再做一次：POST /menu/{menuId}/copy → 新食集 id。 */
export function copyMenu(menuId: number): Promise<number> {
  return request<number>({ url: `/menu/${menuId}/copy`, method: 'POST' })
}
