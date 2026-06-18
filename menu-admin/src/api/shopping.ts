import { request } from './request'

// 采购清单（redesign：三数据源 menu/dish/plan + 用户填采购量+采购单位 斤/把/个）
export interface ShoppingItemVO {
  id: number
  listId?: number
  ingredientId: number
  ingredientName?: string
  // 参考克数（菜谱用量合计，提示用）
  referenceGrams?: number
  // 用户填的采购量 + 采购单位
  purchaseAmount?: number | null
  purchaseUnitId?: number | null
  purchaseUnitName?: string
  // 参考分区/旧字段（保留）
  totalAmount?: number
  unitId?: number
  unitName?: string
  purchaseCategoryId?: number
  purchaseCategoryName?: string
  purchased?: number // 0 未买 / 1 已买
  [key: string]: unknown
}

export interface ShoppingListVO {
  id: number
  sourcePlanId?: number
  timeRange?: string
  startDate?: string
  endDate?: string
  createdAt?: string
  items: ShoppingItemVO[]
  grouped?: Record<string, ShoppingItemVO[]>
  categoryNames?: Record<string, string>
  [key: string]: unknown
}

export interface ShoppingList {
  id: number
  sourcePlanId?: number
  timeRange?: string
  startDate?: string
  endDate?: string
  createdAt?: string
  [key: string]: unknown
}

export interface ShoppingListPage {
  records: ShoppingList[]
  total: number
}

export type ShoppingSourceType = 'menu' | 'dish' | 'plan'

export interface GenerateReq {
  sourceType: ShoppingSourceType
  sourceId?: number
  sourceIds?: number[]
}

export function listShopping(params: { pageNum: number; pageSize: number }) {
  return request<ShoppingListPage>({ url: '/shopping', method: 'get', params })
}

export function getShoppingDetail(listId: number) {
  return request<ShoppingListVO>({ url: `/shopping/${listId}`, method: 'get' })
}

export function generateShopping(req: GenerateReq) {
  return request<number>({ url: '/shopping/generate', method: 'post', data: req })
}

/** 用户填采购量 + 采购单位。 */
export function updatePurchase(itemId: number, purchaseAmount: number, purchaseUnitId: number) {
  return request<void>({
    url: `/shopping/item/${itemId}`,
    method: 'put',
    data: { purchaseAmount, purchaseUnitId },
  })
}

export function togglePurchased(itemId: number) {
  return request<void>({ url: `/shopping/item/${itemId}/purchased`, method: 'put' })
}

export function deleteShoppingItem(itemId: number) {
  return request<void>({ url: `/shopping/item/${itemId}`, method: 'delete' })
}

export function deleteShoppingList(listId: number) {
  return request<void>({ url: `/shopping/${listId}`, method: 'delete' })
}
