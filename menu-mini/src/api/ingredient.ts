import { request } from '@/utils/request'

/** 食材（入库页搜索库用）。 */
export interface IngredientItem {
  id: number
  name: string
  /** 本家私有食材（显示「我家」小标签）；公共食材为 false / 缺省。 */
  familyOwned?: boolean
  /** 搜索时命中的别名（搜「土豆」命中「马铃薯」→ 土豆）。 */
  matchedAlias?: string | null
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

/** 新建结果：existed = public 已有公共食材（含别名命中）/ family 本家已有 / created 新建。 */
export interface ResolvedIngredient {
  id: number
  name: string
  existed: 'public' | 'family' | 'created'
  familyOwned: boolean
}

/**
 * 新建食材（不再带营养）：后端规整名称后先对公共食材名 / 别名、再对本家已有去重，
 * 命中则不新建、返回已有食材（existed=public 时前端提示「已有：马铃薯」并直接选中）。
 */
export function resolveIngredient(name: string, purchaseCategoryId?: number, edible?: number): Promise<ResolvedIngredient> {
  return request<ResolvedIngredient>({
    url: '/ingredient/resolve',
    method: 'POST',
    data: { name, purchaseCategoryId, edible },
  })
}

/** 新建食材（写菜谱「新建食材」用）→ 去重后的结果。 */
export function createIngredient(name: string): Promise<ResolvedIngredient> {
  return resolveIngredient(name)
}

export interface IngredientRow {
  id: number
  name: string
  purchaseCategoryId?: number | null
  categoryName?: string | null
  edible?: number | null
  /** 本家私有食材（可改可删）；公共食材只读。 */
  familyOwned?: boolean
  matchedAlias?: string | null
}

/** 服务端搜索（匹配名称与别名），选食材面板「本地没搜到」时兜底。 */
export async function searchIngredients(keyword: string, pageSize = 20): Promise<IngredientItem[]> {
  const data = await request<any>({
    url: '/ingredient',
    method: 'GET',
    data: { keyword, pageNum: 1, pageSize },
  })
  return Array.isArray(data) ? data : (data?.records ?? [])
}

/** 食材分页列表（keyword 回车触发 / purchaseCategoryId 分类过滤）。 */
export function listIngredients(p: {
  keyword?: string
  purchaseCategoryId?: number
  /** 只看「我家」食材（本家私有）。 */
  familyOnly?: boolean
  pageNum: number
  pageSize?: number
}): Promise<{ records: IngredientRow[]; total: number }> {
  return request({
    url: '/ingredient',
    method: 'GET',
    data: {
      keyword: p.keyword || undefined,
      purchaseCategoryId: p.purchaseCategoryId || undefined,
      familyOnly: p.familyOnly || undefined,
      pageNum: p.pageNum,
      pageSize: p.pageSize ?? 10,
    },
  })
}

/** 食材详情。 */
export function ingredientDetail(id: number): Promise<IngredientRow> {
  return request({ url: `/ingredient/${id}`, method: 'GET' })
}

/** 修改本家食材：PUT /ingredient {id, name?, purchaseCategoryId?, edible?}（edible：1 食用 / 2 饮料零食 / 3 生活用品）。 */
export function updateIngredient(
  id: number,
  patch: { name?: string; purchaseCategoryId?: number; edible?: number },
): Promise<void> {
  return request({ url: '/ingredient', method: 'PUT', data: { id, ...patch } })
}

/** 删除本家食材（被库存 / 采购 / 菜谱等使用时后端拒绝并提示）。 */
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
