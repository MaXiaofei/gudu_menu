import { request } from '@/utils/request'
import type { Page } from './common'

/** 食集。 */
export interface Menu {
  id: number
  name: string
  status?: string | null // ACTIVE / DONE
  servingCount?: number | null
  createTime?: string | null
  dishCount?: number | null
}

export interface MenuDish {
  dishId: number
  dishName?: string | null
  servingFactor?: number | null
  note?: string | null
  coverUrl?: string | null
}

export interface MenuDetail {
  menu: Menu
  dishes: MenuDish[]
  totalMinutes?: number | null
}

/** 食集列表：GET /menu（status 过滤可选）。 */
export function listMenus(pageNum = 1, pageSize = 10, status?: string): Promise<Page<Menu>> {
  return request<Page<Menu>>({
    url: '/menu',
    method: 'GET',
    data: { pageNum, pageSize, status: status || undefined },
  })
}

/** 食集详情：GET /menu/{id}。 */
export function menuDetail(id: number): Promise<MenuDetail> {
  return request<MenuDetail>({ url: `/menu/${id}`, method: 'GET' })
}

/** 新建食集：POST /menu（可带菜建集）。 */
export function createMenu(name: string, dishIds: number[] = []): Promise<number> {
  return request<number>({
    url: '/menu',
    method: 'POST',
    data: {
      menu: { name, servingCount: 1, status: 'ACTIVE' },
      dishes: dishIds.map((id) => ({ dishId: id, servingFactor: 1 })),
    },
  })
}

/**
 * 加菜到已有食集（对齐 Flutter MenuService.addDishToMenu）：
 * 拉详情 → 查重 → 自动拼接名判断（原名为已有菜名「_」拼接时空名时更新）→ PUT /menu 整体更新。
 */
export async function addDishToMenu(menuId: number, dishId: number, dishName?: string): Promise<void> {
  const detail = await menuDetail(menuId)
  const exists = detail.dishes.some((d) => d.dishId === dishId)
  if (exists) return

  const existingNames = detail.dishes
    .map((d) => d.dishName || '')
    .filter((n) => n.length > 0)
  const autoName = existingNames.join('_')
  const shouldRename = detail.menu.name === autoName || !detail.menu.name
  const newName =
    shouldRename && dishName
      ? [...existingNames, dishName].join('_')
      : detail.menu.name

  await request({
    url: '/menu',
    method: 'PUT',
    data: {
      menu: {
        id: detail.menu.id,
        name: newName,
        servingCount: detail.menu.servingCount ?? 1,
        status: detail.menu.status ?? 'ACTIVE',
      },
      dishes: [
        ...detail.dishes.map((d) => ({ dishId: d.dishId, servingFactor: d.servingFactor ?? 1 })),
        { dishId, servingFactor: 1 },
      ],
    },
  })
}

/** 删除食集。 */
export function deleteMenu(id: number): Promise<void> {
  return request({ url: `/menu/${id}`, method: 'DELETE' })
}

/** ===== 食集详情内操作（阶段 3） ===== */

export interface CookMaterial {
  ingredientId: number
  ingredientName: string
  usageTexts?: string[]
  level?: string | null // ENOUGH / LOW / NONE
  isCondiment?: boolean
}

/** 做菜用材聚合（不落库）：GET /menu/{id}/cook-materials。 */
export function cookMaterials(menuId: number): Promise<CookMaterial[]> {
  return request<any>({ url: `/menu/${menuId}/cook-materials`, method: 'GET' })
    .then((d) => (Array.isArray(d) ? d : (d?.items ?? [])))
}

export interface CookResult {
  menuId: number
  cookingRecordIds?: number[]
}

/** 整集做菜确认：POST /menu/{id}/cook（扣档位 + 写 cooking_record + 食集标 DONE）。 */
export function cookMenu(menuId: number, usedUp: number[], partiallyUsed: number[]): Promise<CookResult> {
  return request<CookResult>({
    url: `/menu/${menuId}/cook`,
    method: 'POST',
    data: { usedUp, partiallyUsed },
  })
}

/** 改/删菜备注（空串=删）：PUT /menu/{menuId}/dish/{dishId}/note。 */
export function updateDishNote(menuId: number, dishId: number, note: string): Promise<void> {
  return request({
    url: `/menu/${menuId}/dish/${dishId}/note`,
    method: 'PUT',
    data: { note },
  })
}

/** 移出食集：DELETE /menu/{menuId}/dish/{dishId}。 */
export function removeDishFromMenu(menuId: number, dishId: number): Promise<void> {
  return request({ url: `/menu/${menuId}/dish/${dishId}`, method: 'DELETE' })
}

/** 聚餐 Tab 角标（占位接口）。 */
export function togetherCount(menuId: number): Promise<number> {
  return request<number>({ url: `/menu/${menuId}/together-count`, method: 'GET' })
}

/** 备菜一键加采购：POST /shopping/from-prep → 采购清单 id。 */
export function shoppingFromPrep(menuId: number, ingredientIds: number[]): Promise<number> {
  return request<number>({
    url: '/shopping/from-prep',
    method: 'POST',
    data: { menuId, ingredientIds },
  })
}
