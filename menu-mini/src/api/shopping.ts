import { request } from '@/utils/request'

// 采购清单（redesign：三数据源 menu/dish/plan + 用户填采购量+采购单位 斤/把/个）
export interface ShoppingItemVO {
  id: number
  listId?: number
  ingredientId: number
  ingredientName?: string
  referenceGrams?: number
  purchaseAmount?: number | null
  purchaseUnitId?: number | null
  purchaseUnitName?: string
  totalAmount?: number
  unitId?: number
  unitName?: string
  purchaseCategoryId?: number
  purchaseCategoryName?: string
  purchased?: number // 0 未买 / 1 已买
  [k: string]: any
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
  [k: string]: any
}

export interface ShoppingList {
  id: number
  sourcePlanId?: number
  timeRange?: string
  startDate?: string
  endDate?: string
  createdAt?: string
  [k: string]: any
}

export type ShoppingSourceType = 'menu' | 'dish' | 'plan'

export interface GenerateReq {
  sourceType: ShoppingSourceType
  sourceId?: number
  sourceIds?: number[]
}

// 生成采购草稿（三数据源 menu/dish/plan）
export const generate = (req: GenerateReq) =>
  request<number>({ url: '/shopping/generate', method: 'POST', data: req })

// 采购清单详情（含 items 中文 + 品类分区 + 采购单位中文）
export const getDetail = (listId: number) =>
  request<ShoppingListVO>({ url: `/shopping/${listId}`, method: 'GET' })

// 采购清单分页列表（小程序拉全量）
export const listShopping = () =>
  request<{ records: ShoppingList[]; total: number }>({
    url: '/shopping',
    method: 'GET',
    data: { pageNum: 1, pageSize: 1000 }
  }).then((p: any) => p.records || [])

// 用户填采购量 + 采购单位
export const updatePurchase = (itemId: number, purchaseAmount: number, purchaseUnitId: number) =>
  request<any>({
    url: `/shopping/item/${itemId}`,
    method: 'PUT',
    data: { purchaseAmount, purchaseUnitId }
  })

// 勾选/取消已买
export const togglePurchased = (itemId: number) =>
  request<any>({ url: `/shopping/item/${itemId}/purchased`, method: 'PUT' })

// 删除明细
export const deleteItem = (itemId: number) =>
  request<any>({ url: `/shopping/item/${itemId}`, method: 'DELETE' })

// 删除整张清单
export const deleteList = (listId: number) =>
  request<any>({ url: `/shopping/${listId}`, method: 'DELETE' })
