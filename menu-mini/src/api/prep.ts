import { request } from '@/utils/request'

/** 备菜项（按食材聚合）。 */
export interface PrepItem {
  ingredientId: number
  ingredientName: string
  stockLevel?: string | null // ENOUGH / LOW / NONE
  usageTexts?: string[]
  dishCount?: number
  dishNames?: string[]
  status: string // PENDING / READY / THAWING / MARINATING
}

export interface MenuPrep {
  items: PrepItem[]
  condiments: PrepItem[]
  readyCount: number
  totalCount: number
}

/** 备菜聚合：GET /menu/{id}/prep。 */
export function getPrep(menuId: number): Promise<MenuPrep> {
  return request<MenuPrep>({ url: `/menu/${menuId}/prep`, method: 'GET' })
}

/** 更新备料状态（upsert）：PUT /menu/{id}/prep/{ingredientId}?status=READY。 */
export function updatePrepStatus(menuId: number, ingredientId: number, status: string): Promise<void> {
  return request({
    url: `/menu/${menuId}/prep/${ingredientId}?status=${status}`,
    method: 'PUT',
  })
}
