import { request } from '@/utils/request'
import type { DishCard } from './dish'
import type { ExpiringItem } from './pantry'

/** 首页智荐聚合：GET /recommend/home（任一分区无数据为空数组，前端隐藏）。 */
export interface RecommendHome {
  expiring: ExpiringItem[]
  expiringPicks: DishCard[]
  frequent: DishCard[]
  seasonal: DishCard[]
  /** 大家常做（全站热度，不针对个人；关闭个性化推荐时代替「你家常做」） */
  popular: DishCard[]
  /** false = 用户在「我的 → 设置」关闭了个性化推荐，本次只有通用推荐（A.8） */
  personalized: boolean
}

export async function recommendHome(): Promise<RecommendHome> {
  const r = await request<Partial<RecommendHome> | null>({ url: '/recommend/home', method: 'GET' })
  return {
    expiring: r?.expiring ?? [],
    expiringPicks: r?.expiringPicks ?? [],
    frequent: r?.frequent ?? [],
    seasonal: r?.seasonal ?? [],
    popular: r?.popular ?? [],
    personalized: r?.personalized !== false,
  }
}

/** 首推候选：个性化时 临期主推 → 你家常做；关闭个性化时 大家常做 → 应季。 */
export function heroCandidates(
  h: Pick<RecommendHome, 'expiringPicks' | 'frequent' | 'seasonal' | 'popular' | 'personalized'>,
): DishCard[] {
  if (!h.personalized) return h.popular.length ? h.popular : h.seasonal
  return h.expiringPicks.length ? h.expiringPicks : h.frequent
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
