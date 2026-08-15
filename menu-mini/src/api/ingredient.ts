import { request } from '@/utils/request'

/** 食材（入库页搜索库用）。 */
export interface IngredientItem {
  id: number
  name: string
}

/** 全部食材（入库页建搜索库；全量理由见 DESIGN.md §12，与 Flutter listAll 一致）。 */
export async function listAllIngredients(): Promise<IngredientItem[]> {
  const data = await request<any>({
    url: '/ingredient',
    method: 'GET',
    data: { pageNum: 1, pageSize: 1000 },
  })
  return Array.isArray(data) ? data : (data?.records ?? [])
}

/** 新建食材（写菜谱「新建食材」用）→ id。 */
export function createIngredient(name: string): Promise<number> {
  return request<number>({
    url: '/ingredient',
    method: 'POST',
    data: { ingredient: { name }, nutritions: [] },
  })
}

export interface IngredientRow {
  id: number
  name: string
  purchaseCategoryId?: number | null
  categoryName?: string | null
  edible?: number | null
}

/** 食材分页列表（keyword 回车触发 / purchaseCategoryId 分类过滤）。 */
export function listIngredients(p: {
  keyword?: string
  purchaseCategoryId?: number
  pageNum: number
  pageSize?: number
}): Promise<{ records: IngredientRow[]; total: number }> {
  return request({
    url: '/ingredient',
    method: 'GET',
    data: {
      keyword: p.keyword || undefined,
      purchaseCategoryId: p.purchaseCategoryId || undefined,
      pageNum: p.pageNum,
      pageSize: p.pageSize ?? 10,
    },
  })
}

/** 食材详情。 */
export function ingredientDetail(id: number): Promise<IngredientRow> {
  return request({ url: `/ingredient/${id}`, method: 'GET' })
}

/** 更新食用属性：PUT /ingredient {id, edible}（1 食用 / 2 饮料零食 / 3 生活用品）。 */
export function updateIngredient(id: number, edible: number): Promise<void> {
  return request({ url: '/ingredient', method: 'PUT', data: { id, edible } })
}

/** 删除食材（关联用量记录保留）。 */
export function deleteIngredient(id: number): Promise<void> {
  return request({ url: `/ingredient/${id}`, method: 'DELETE' })
}

/** upsert 字典（自定义分类用）→ id。 */
export function upsertDict(name: string, group: string): Promise<number> {
  return request<number>({
    url: '/dict',
    method: 'POST',
    data: { name, dictGroup: group },
  })
}
