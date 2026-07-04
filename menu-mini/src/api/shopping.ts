import { request } from '@/utils/request'

// 采购清单（redesign：三数据源 menu/dish/plan + 用户填采购量+采购单位 斤/把/个）
export interface ShoppingItemVO {
  id: number
  listId?: number
  ingredientId: number | null
  ingredientName?: string
  /** V30：手动添加未命中食材时存的自由食材名，展示时 ingredientName 已 fallback 到它。 */
  customName?: string | null
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
  /** Plan B：家中现有克数（customName 手动加项为 null） */
  pantryGrams?: number | null
  /** Plan B：三色状态 RED_NONE 没有 / YELLOW_SHORT 差 X / GREEN_ENOUGH 够；customName 项或无用量为 null */
  stockStatus?: 'RED_NONE' | 'YELLOW_SHORT' | 'GREEN_ENOUGH' | null
  /** Plan B：差多少克（RED=needGrams、YELLOW=need-have、GREEN=0） */
  shortageGrams?: number | null
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

// 建空采购单（自定义采购入口），返回新 shopping_list.id
export const createShopping = () =>
  request<number>({ url: '/shopping/create', method: 'POST' })

// 采购清单详情（含 items 中文 + 品类分区 + 采购单位中文）
export const getDetail = (listId: number) =>
  request<ShoppingListVO>({ url: `/shopping/${listId}`, method: 'GET' })

// 按食集查采购清单（含 items + 三色）；未生成返回 null（前端据此显"生成"按钮）。Plan E。
export const getMenuShopping = (menuId: number) =>
  request<ShoppingListVO | null>({ url: `/shopping/by-menu/${menuId}`, method: 'GET' })

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

// 手动添加自定义采购项（V30）：采购清单不强绑菜单/菜品
// name 命中已有 ingredient → 关联；未命中 → ingredientId=null + name 存 custom_name
export const addCustomItem = (listId: number, name: string, amount: number | null, unitId: number | null, purchaseCategoryId: number | null) =>
  request<number>({
    url: '/shopping/item/custom',
    method: 'POST',
    data: { listId, name, amount, unitId, purchaseCategoryId }
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
