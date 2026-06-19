import { request } from './request'

export interface Ingredient {
  id: number
  name: string
  unitId: number
  purchaseCategoryId: number
  /** metric name -> value(per 100g)：{calorie, protein, fat, carb, sugar, gi} */
  nutrition?: Record<string, number>
  [key: string]: unknown
}

export interface IngredientNutrition {
  metricId: number
  value: number
}

export interface IngredientSaveDTO {
  ingredient: Partial<Ingredient> & { name: string; unitId: number; purchaseCategoryId: number }
  nutritions: IngredientNutrition[]
}

export interface IngredientPage {
  records: Ingredient[]
  total: number
}

/** 拉取食材列表（全量，下拉选项 / 后台前端筛排用，内部取大页）。 */
export function listIngredients(params?: { purchaseCategoryId?: number }) {
  return request<IngredientPage>({
    url: '/ingredient',
    method: 'get',
    params: { pageNum: 1, pageSize: 1000, ...params },
  }).then((p) => p.records)
}

export function getIngredientNutrition(id: number) {
  return request<Record<string, number>>({ url: `/ingredient/${id}/nutrition`, method: 'get' })
}

export function createIngredient(data: IngredientSaveDTO) {
  return request<number>({ url: '/ingredient', method: 'post', data })
}

export function updateIngredient(data: IngredientSaveDTO & { ingredient: { id: number } & IngredientSaveDTO['ingredient'] }) {
  return request<void>({ url: '/ingredient', method: 'put', data })
}

export function deleteIngredient(id: number) {
  return request<void>({ url: `/ingredient/${id}`, method: 'delete' })
}
