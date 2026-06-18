import { request } from '@/utils/request'

// 食材库存（家中现有食材：余量/过期日/阈值）
export interface PantryVO {
  id: number
  ingredientId: number
  ingredientName?: string
  amount: number
  unitId?: number
  unitName?: string
  expireDate?: string
  lowThreshold?: number
  updateTime?: string
  [k: string]: any
}

// 全部库存（小程序量小，取大页一次拉全量）
export const listPantry = () =>
  request<PantryVO[]>({ url: '/pantry', method: 'GET', data: { pageNum: 1, pageSize: 1000 } }).then((p: any) => p.records || [])

// 临期库存（默认 3 天内）
export const listExpiring = (days = 3) =>
  request<PantryVO[]>({ url: '/pantry/expiring', method: 'GET', data: { days } })

// 不足库存（余量低于阈值）
export const listLow = () =>
  request<PantryVO[]>({ url: '/pantry/low', method: 'GET' })
