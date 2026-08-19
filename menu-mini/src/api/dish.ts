import { request } from '@/utils/request'
import type { Page } from './common'

/** 菜谱（列表/详情共用；菜系/分类/标签名后端回填）。 */
export interface Dish {
  id: number
  name: string
  coverUrl?: string | null
  prepTime?: number | null
  cookTime?: number | null
  difficulty?: number | null
  note?: string | null
  sourceName?: string | null
  cuisineNames?: string[]
  categoryNames?: string[]
  tagNames?: string[]
  cookedCount?: number
}

/** 步骤（images 为逗号分隔的相对路径串）。 */
export interface DishStep {
  seq?: number
  text: string
  images?: string | null
}

/** 用料行（自然单位「2 个」「适量」）。 */
export interface DishIngredient {
  ingredientId: number
  ingredientName?: string | null
  amount?: number | null
  unitName?: string | null
}

export interface DishDetail {
  dish: Dish
  steps: DishStep[]
  ingredients: DishIngredient[]
}

/** 用量展示文本：「2 个」/「适量」/「2」。 */
export function amountText(ing: DishIngredient): string {
  const amt = ing.amount
  const num = amt == null ? '' : Number.isInteger(amt) ? String(amt) : String(amt)
  const unit = ing.unitName || ''
  if (!num && !unit) return ''
  if (!num) return unit
  return unit ? `${num} ${unit}` : num
}

export interface DishSearchParams {
  keyword?: string
  tagIds?: string // 逗号分隔多选
  cuisineIds?: string
  categoryIds?: string // 逗号分隔多选（2026-08-19 新标签体系：分类替代 tag+cuisine）
  sort?: 'cooked' | 'latest'
  pageNum: number
  pageSize?: number
}

/** 语义找菜结果项（向量相似召回）。 */
export interface SemanticHit {
  dishId: number
  name: string
  score?: number
  difficulty?: number | null
  cookTime?: number | null
}

/** 语义找菜：自然语言（「清淡下饭」「酸甜口」）→ 向量相似召回 TopK 菜谱。 */
export function semanticSearch(query: string, topK = 8): Promise<SemanticHit[]> {
  return request<SemanticHit[]>({
    url: '/dish/semantic-search',
    method: 'POST',
    data: { query, topK },
  })
}

/** 搜索菜谱：GET /dish/search（sort=cooked 做过最多，缺省最新）。 */
export function searchDishes(p: DishSearchParams): Promise<Page<Dish>> {
  return request<Page<Dish>>({
    url: '/dish/search',
    method: 'GET',
    data: {
      keyword: p.keyword || undefined,
      tagIds: p.tagIds || undefined,
      cuisineIds: p.cuisineIds || undefined,
      categoryIds: p.categoryIds || undefined,
      sort: p.sort === 'cooked' ? 'cooked' : undefined,
      pageNum: p.pageNum,
      pageSize: p.pageSize ?? 10,
    },
  })
}

/** 菜谱详情：GET /dish/{id}。 */
export function dishDetail(id: number): Promise<DishDetail> {
  return request<DishDetail>({ url: `/dish/${id}`, method: 'GET' })
}

/** 删除菜谱（连带步骤/关联/用料/历史）。 */
export function deleteDish(id: number): Promise<void> {
  return request({ url: `/dish/${id}`, method: 'DELETE' })
}

/** ===== 写菜谱（阶段 6） ===== */

export interface DishSavePayload {
  dish: {
    name: string
    coverUrl?: string
    note?: string
    prepTime?: number
    cookTime?: number
    difficulty?: number
  }
  steps: { seq?: number; sortOrder?: number; text: string; images?: string }[]
  ingredients: { ingredientId: number; amount?: number; unitId?: number }[]
  tagIds: number[]
  cuisineIds: number[]
}

/** 发布菜谱：POST /dish → 新菜 id。 */
export function saveDish(p: DishSavePayload): Promise<number> {
  return request<number>({ url: '/dish', method: 'POST', data: p })
}

/** 导入链接：POST /dish/import-url?url= → 新菜 id。 */
export function importDishByUrl(url: string): Promise<number> {
  return request<number>({ url: `/dish/import-url?url=${encodeURIComponent(url)}`, method: 'POST' })
}

export interface DishDraftItem {
  id: number
  name?: string | null
  coverUrl?: string | null
  ingredientCount?: number
  stepCount?: number
  updateTime?: string | null
}

export interface DishDraftDetail {
  id: number
  name?: string | null
  coverUrl?: string | null
  prepTime?: number | null
  cookTime?: number | null
  difficulty?: number | null
  note?: string | null
  tagIds: number[]
  cuisineIds: number[]
  ingredients: { ingredientId: number; ingredientName?: string | null; amount?: string | null; unitText?: string | null }[]
  steps: { seq?: number; text: string; images?: string | null }[]
}

/** 草稿列表（本人，更新时间倒序）。 */
export function listDrafts(pageNum = 1, pageSize = 10): Promise<Page<DishDraftItem>> {
  return request<Page<DishDraftItem>>({
    url: '/dish/draft/list',
    method: 'GET',
    data: { pageNum, pageSize },
  })
}

/** 存草稿（body 带 id=更新，无 id=新建）→ 草稿 id。 */
export function saveDraft(body: Partial<DishSavePayload> & { id?: number }): Promise<number> {
  return request<number>({ url: '/dish/draft', method: 'POST', data: body })
}

/** 草稿详情（继续编辑回填）。 */
export function draftDetail(id: number): Promise<DishDraftDetail> {
  return request<DishDraftDetail>({ url: `/dish/draft/${id}`, method: 'GET' })
}

/** 删草稿。 */
export function deleteDraft(id: number): Promise<void> {
  return request({ url: `/dish/draft/${id}`, method: 'DELETE' })
}
