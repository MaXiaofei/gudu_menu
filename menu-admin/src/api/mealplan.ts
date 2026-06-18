import { request } from './request'

export interface MealPlan {
  id: number
  weekStart: string
  name?: string
  createTime?: string
  [key: string]: unknown
}

export interface MealPlanItem {
  id?: number
  planId?: number
  date: string
  meal: string // 早餐/午餐/晚餐/加餐（sys_dict group=meal）
  dishId: number
  servingFactor?: number
  sort?: number
}

export interface PlanDetail {
  plan: MealPlan
  items: MealPlanItem[]
}

export interface AddItemResult {
  itemId: number
  duplicates: { dishId: number; date: string; meal: string }[]
}

export interface MenuTemplate {
  id?: number
  name: string
  snapshot: MealPlanItem[]
  createTime?: string
}

export function listPlans() {
  return request<MealPlan[]>({ url: '/mealplan', method: 'get' })
}

export function createPlan(weekStart: string, name?: string) {
  return request<number>({ url: '/mealplan', method: 'post', data: { weekStart, name } })
}

export function getPlan(planId: number) {
  return request<PlanDetail>({ url: `/mealplan/${planId}`, method: 'get' })
}

export function addItem(planId: number, item: MealPlanItem) {
  return request<AddItemResult>({ url: `/mealplan/${planId}/item`, method: 'post', data: item })
}

export function delItem(itemId: number) {
  return request<void>({ url: `/mealplan/item/${itemId}`, method: 'delete' })
}

export function applyTemplate(planId: number, templateId: number) {
  return request<number>({ url: `/mealplan/${planId}/apply-template`, method: 'post', params: { templateId } })
}

export function listTemplates() {
  return request<MenuTemplate[]>({ url: '/mealplan/templates', method: 'get' })
}

export function saveTemplate(t: MenuTemplate) {
  return request<number>({ url: '/mealplan/templates', method: 'post', data: t })
}
