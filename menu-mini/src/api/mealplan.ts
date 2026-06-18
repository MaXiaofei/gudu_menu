import { request } from '@/utils/request'

// 周计划
export interface MealPlan {
  id: number
  weekStart: string
  name?: string
  [k: string]: any
}

export interface MealPlanItem {
  id?: number
  planId?: number
  date: string
  meal: string // 早餐/午餐/晚餐/加餐（关联 sys_dict group=meal）
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

// 创建周计划
export const createPlan = (weekStart: string, name?: string) =>
  request<number>({ url: '/mealplan', method: 'POST', data: { weekStart, name } })

// 查周计划详情
export const getPlan = (planId: number) =>
  request<PlanDetail>({ url: `/mealplan/${planId}`, method: 'GET' })

// 添加排菜项
export const addItem = (planId: number, item: MealPlanItem) =>
  request<AddItemResult>({ url: `/mealplan/${planId}/item`, method: 'POST', data: item })

// 删除排菜项
export const delItem = (itemId: number) =>
  request<any>({ url: `/mealplan/item/${itemId}`, method: 'DELETE' })
