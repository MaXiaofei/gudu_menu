import { request } from '@/utils/request'
import type { DishCard } from './dish'
import type { ExpiringItem } from './pantry'

/** 首页智荐聚合：GET /recommend/home（任一分区无数据为空数组，前端隐藏）。 */
export interface RecommendHome {
  expiring: ExpiringItem[]
  expiringPicks: DishCard[]
  frequent: DishCard[]
  seasonal: DishCard[]
}

export async function recommendHome(): Promise<RecommendHome> {
  const r = await request<Partial<RecommendHome> | null>({ url: '/recommend/home', method: 'GET' })
  return {
    expiring: r?.expiring ?? [],
    expiringPicks: r?.expiringPicks ?? [],
    frequent: r?.frequent ?? [],
    seasonal: r?.seasonal ?? [],
  }
}

/** 最近做过（按菜去重，最近优先）。 */
export interface RecentCooked {
  dishId: number
  name: string
  coverImage: string | null
  lastCookedAt: string
  cookedCount: number
}

/** GET /cookbook/recent?limit=（limit ≤ 30）。 */
export function recentCooked(limit = 10): Promise<RecentCooked[]> {
  return request<RecentCooked[]>({
    url: '/cookbook/recent',
    method: 'GET',
    data: { limit: Math.min(Math.max(1, limit), 30) },
  }).then((d) => (Array.isArray(d) ? d : []))
}
