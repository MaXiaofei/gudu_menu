import { request } from './request'

/**
 * 运营收编（后端 /admin/ingredient-adoption，仅平台运营）：
 * 家庭新建（私有）食材按规整名称汇总；收编为公共 / 合并到已有公共食材；别名维护。
 */

export interface CategoryShare {
  categoryId: number | null
  categoryName: string
  count: number
}

/** 同名家庭私有食材的汇总行。 */
export interface AdoptionGroup {
  name: string
  familyCount: number
  ingredientCount: number
  earliest: string | null
  categories: CategoryShare[]
  suggestedCategoryId: number | null
  ingredientIds: number[]
}

export interface AdoptResult {
  targetId: number
  targetName: string
  mergedIngredients: number
  movedRows: number
  dedupedRows: number
  aliasesAdded: string[]
}

export interface AliasItem {
  id: number
  alias: string
  ingredientId: number
  ingredientName: string | null
  createTime: string | null
}

export interface AliasPage {
  records: AliasItem[]
  total: number
}

const BASE = '/admin/ingredient-adoption'

export function listAdoptionGroups() {
  return request<AdoptionGroup[]>({ url: `${BASE}/groups`, method: 'get' })
}

/** 操作 A：收编为公共食材（运营确认名称与采购分类）。 */
export function adoptIngredient(data: { groupName: string; name: string; purchaseCategoryId?: number | null; reason?: string }) {
  return request<AdoptResult>({ url: `${BASE}/adopt`, method: 'post', data })
}

/** 操作 B：合并到已有公共食材（各家写法记为别名）。 */
export function mergeIngredient(data: { groupName: string; targetId: number; reason?: string }) {
  return request<AdoptResult>({ url: `${BASE}/merge`, method: 'post', data })
}

export function listAliases(params: { keyword?: string; pageNum?: number; pageSize?: number }) {
  return request<AliasPage>({ url: `${BASE}/aliases`, method: 'get', params })
}

export function addAlias(data: { alias: string; ingredientId: number }) {
  return request<AliasItem>({ url: `${BASE}/aliases`, method: 'post', data })
}

export function deleteAlias(id: number) {
  return request<void>({ url: `${BASE}/aliases/${id}`, method: 'delete' })
}
