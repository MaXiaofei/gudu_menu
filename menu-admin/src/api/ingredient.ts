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

export function listIngredients() {
  return request<Ingredient[]>({ url: '/ingredient', method: 'get' })
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
