import { request } from './request'

export interface Dish {
  id: number
  name: string
  prepTime?: number
  cookTime?: number
  difficulty?: number
  coverUrl?: string
  [key: string]: unknown
}

export interface DishStep {
  text: string
  images?: string[]
}

/** 后端 detail 返回的步骤（images 为字符串：单 URL 或逗号分隔；历史版本类型） */
export interface DishStepResp {
  text: string
  images?: string | string[]
}

/** 提交给后端的步骤（后端 images 为字符串：单 URL 或逗号分隔） */
export interface DishStepWire {
  text: string
  images: string
}

export interface DishIngredient {
  ingredientId: number
  amount: number
}

export interface DishDetail {
  dish: Dish
  steps: DishStepResp[]
  cuisineIds: number[]
  tagIds: number[]
  ingredients: DishIngredient[]
}

export interface DishSaveDTO {
  dish: Partial<Dish> & { name: string }
  steps: DishStepWire[]
  cuisineIds: number[]
  tagIds: number[]
  ingredients: DishIngredient[]
}

export interface DishSearchRow {
  id: number
  name: string
  prepTime?: number
  cookTime?: number
  difficulty?: number
  coverUrl?: string
  [key: string]: unknown
}

export interface Page<T> {
  records: T[]
  total: number
}

export interface DishSearchParams {
  keyword?: string
  cuisineIds?: number[]
  tagIds?: number[]
  categoryIds?: number[]
  maxMinutes?: number
  maxDifficulty?: number
  pageNum?: number
  pageSize?: number
}

export function listDishes() {
  return request<Dish[]>({ url: '/dish', method: 'get' })
}

export function getDishDetail(id: number) {
  return request<DishDetail>({ url: `/dish/${id}`, method: 'get' })
}

export function searchDishes(params: DishSearchParams) {
  return request<Page<DishSearchRow>>({ url: '/dish/search', method: 'get', params })
}

export function updateDish(data: DishSaveDTO & { dish: { id: number } & DishSaveDTO['dish'] }) {
  return request<void>({ url: '/dish', method: 'put', data })
}

export function deleteDish(id: number) {
  return request<void>({ url: `/dish/${id}`, method: 'delete' })
}

export function getDishNutrition(id: number, serving = 1) {
  return request<Record<string, number>>({ url: `/dish/${id}/nutrition`, method: 'get', params: { serving } })
}

export interface DishHistory {
  id: number
  snapshot: unknown
  createTime: string
}

export function getDishHistory(id: number) {
  return request<DishHistory[]>({ url: `/dish/${id}/history`, method: 'get' })
}
