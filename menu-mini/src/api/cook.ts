import { request } from '@/utils/request'
import type { CookMaterial } from './menu'

/** 单菜直做确认弹窗数据：GET /cook/dish/{dishId}/materials?servings=（结构同食集 cook-materials）。 */
export function dishCookMaterials(dishId: number, servings = 1): Promise<CookMaterial[]> {
  return request<any>({
    url: `/cook/dish/${dishId}/materials`,
    method: 'GET',
    data: { servings },
  }).then((d) => (Array.isArray(d) ? d : (d?.items ?? [])))
}

export interface CookDishPayload {
  servings?: number
  usedUp?: number[]
  partiallyUsed?: number[]
  note?: string
}

/** 单菜直做：POST /cook/dish/{dishId} → { recordId, dishId }（写 cooking_record，按确认回写档位，食记可见）。 */
export function cookDish(dishId: number, p: CookDishPayload = {}): Promise<{ recordId: number; dishId: number }> {
  const data: Record<string, unknown> = {
    servings: p.servings ?? 1,
    usedUp: p.usedUp ?? [],
    partiallyUsed: p.partiallyUsed ?? [],
  }
  if (p.note?.trim()) data.note = p.note.trim().slice(0, 200)
  return request({ url: `/cook/dish/${dishId}`, method: 'POST', data })
}
