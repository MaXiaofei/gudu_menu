import { request } from '@/utils/request'
import type { Page } from './common'

/** 采购清单摘要（列表行）。 */
export interface ShoppingListSummary {
  id: number
  name?: string | null
  sourceType?: string | null // menu / dish / plan / custom / custom_text
  startDate?: string | null
  endDate?: string | null
  createTime?: string | null
}

/** 采购项。 */
export interface ShoppingItem {
  id: number
  ingredientName?: string | null
  customName?: string | null
  purchaseAmount?: number | null
  purchaseUnitName?: string | null
  stockStatus?: string | null // RED_NONE / YELLOW_SHORT / GREEN_ENOUGH / null=手动加
  purchased?: number
}

/** 采购清单详情。 */
export interface ShoppingDetail {
  id: number
  name?: string | null
  sourceType?: string | null
  startDate?: string | null
  endDate?: string | null
  items: ShoppingItem[]
  grouped?: Record<string, ShoppingItem[]>
  categoryNames?: Record<string, string>
}

export interface RestockResult {
  restocked: number
  markedOnly: number
}

/** 清单分页。 */
export function listShopping(pageNum = 1, pageSize = 10): Promise<Page<ShoppingListSummary>> {
  return request<Page<ShoppingListSummary>>({
    url: '/shopping',
    method: 'GET',
    data: { pageNum, pageSize },
  })
}

/** 清单详情（items + 分类分区）。 */
export function shoppingDetail(id: number): Promise<ShoppingDetail> {
  return request<ShoppingDetail>({ url: `/shopping/${id}`, method: 'GET' })
}

/** 建空采购单 → id。 */
export function createEmptyList(): Promise<number> {
  return request<number>({ url: '/shopping/create', method: 'POST' })
}

/** 批量保存入库（默认记充足）。 */
export function restockItems(itemIds: number[]): Promise<RestockResult> {
  return request<RestockResult>({
    url: '/shopping/restock',
    method: 'POST',
    data: { itemIds },
  })
}

/** 撤回入库（恢复入库前档位并删项）。 */
export function undoRestock(itemId: number): Promise<void> {
  return request({ url: `/shopping/item/${itemId}/undo-restock`, method: 'POST' })
}

/** 手动加项（名称+数量文本）。 */
export function addCustomItem(p: {
  listId: number
  name: string
  amount?: number
}): Promise<void> {
  return request({
    url: '/shopping/item/custom',
    method: 'POST',
    data: { listId: p.listId, name: p.name, amount: p.amount },
  })
}

/** 删单项。 */
export function removeShoppingItem(itemId: number): Promise<void> {
  return request({ url: `/shopping/item/${itemId}`, method: 'DELETE' })
}

/** 删整单。 */
export function deleteShoppingList(id: number): Promise<void> {
  return request({ url: `/shopping/${id}`, method: 'DELETE' })
}

/** 清单改名。 */
export function renameShoppingList(listId: number, name: string): Promise<void> {
  return request({ url: `/shopping/${listId}/name`, method: 'PUT', data: { name } })
}

/** 来源类型 → 中文。 */
export function sourceTypeLabel(t?: string | null): string {
  switch (t) {
    case 'menu': return '菜单'
    case 'dish': return '菜品'
    case 'plan': return '周计划'
    case 'custom': return '自定义'
    case 'custom_text': return '文本录入'
    default: return '采购'
  }
}

/** 数量展示（整数去小数，否则 1 位）。 */
export function amountText(it: ShoppingItem): string {
  const a = it.purchaseAmount
  if (a == null) return ''
  const s = Number.isInteger(a) ? String(a) : a.toFixed(1)
  return `${s} ${it.purchaseUnitName || ''}`.trim()
}

/** 库存徽章：{ 文案, 色键 }。 */
export function stockBadge(it: ShoppingItem): { text: string; color: string } | null {
  switch (it.stockStatus) {
    case 'RED_NONE': return { text: '家里：用完', color: 'var(--error)' }
    case 'YELLOW_SHORT': return { text: '家里：不足', color: 'var(--warning-text)' }
    case 'GREEN_ENOUGH': return { text: '家里：充足', color: 'var(--success)' }
    default: return { text: '手动加', color: 'var(--caption)' }
  }
}
