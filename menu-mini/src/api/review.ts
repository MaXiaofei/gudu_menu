import { request } from '@/utils/request'
import type { Page } from './common'

/** ===== 评价（阶段 6） ===== */

export interface ReviewDimension {
  id: number
  name: string
}

/** 评价维度字典（口味/难度/营养均衡/外观…）。 */
export async function reviewDimensions(): Promise<ReviewDimension[]> {
  const data = await request<any>({
    url: '/dict',
    method: 'GET',
    data: { group: 'review_dimension', pageNum: 1, pageSize: 100 },
  })
  return Array.isArray(data) ? data : (data?.records ?? [])
}

/** 提交点评：dishId / menuId 二选一。 */
export function submitReview(p: {
  dishId?: number
  menuId?: number
  starRating: number
  text?: string
  images?: string[]
  dimensionScores?: Record<string, number>
}): Promise<void> {
  return request({
    url: '/review',
    method: 'POST',
    data: {
      dishId: p.dishId,
      menuId: p.menuId,
      starRating: p.starRating,
      text: p.text,
      images: p.images ?? [],
      dimensionScores: p.dimensionScores ?? {},
    },
  })
}

/** 食集统一评价页数据。 */
export interface MenuReviewOverview {
  menuId: number
  menuName?: string | null
  finishedAt?: string | null
  dishCount?: number
  menuReview?: {
    reviewed: boolean
    starRating?: number
    dimensionScores?: Record<string, number>
    createTime?: string | null
  } | null
  dishes: { dishId: number; dishName?: string | null; coverUrl?: string | null; starRating?: number | null }[]
}

export function menuReviewOverview(menuId: number): Promise<MenuReviewOverview> {
  return request<MenuReviewOverview>({ url: `/review/menu-overview/${menuId}`, method: 'GET' })
}

/** 我的评价页数据。 */
export interface MyReviewsData {
  reviews: {
    id: number
    dishId?: number | null
    menuId?: number | null
    name?: string | null
    starRating: number
    createTime?: string | null
  }[]
  pendingMenus: {
    menuId: number
    menuName?: string | null
    finishedAt?: string | null
    dishCount: number
    reviewedDishCount: number
    menuReviewed: boolean
  }[]
}

export function myReviews(): Promise<MyReviewsData> {
  return request<MyReviewsData>({ url: '/review/mine', method: 'GET' })
}

/** 单菜均分（菜谱详情用）。 */
export function dishReviewAvg(dishId: number): Promise<{ star: string; count: number }> {
  return request({ url: `/review/dish/${dishId}/avg`, method: 'GET' })
}
