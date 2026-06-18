import { request } from './request'

export interface Pantry {
  id?: number
  ingredientId: number
  amount: number
  unitId?: number
  expireDate?: string
  lowThreshold?: number
  updateTime?: string
  [key: string]: unknown
}

export interface PantryVO extends Pantry {
  ingredientName?: string
  unitName?: string
}

export interface PantryPage {
  records: PantryVO[]
  total: number
}

export function listPantry(params: { pageNum: number; pageSize: number }) {
  return request<PantryPage>({ url: '/pantry', method: 'get', params })
}

export function listExpiring(days = 3) {
  return request<PantryVO[]>({ url: '/pantry/expiring', method: 'get', params: { days } })
}

export function listLow() {
  return request<PantryVO[]>({ url: '/pantry/low', method: 'get' })
}

export function createPantry(data: Pantry) {
  return request<number>({ url: '/pantry', method: 'post', data })
}

export function updatePantry(data: Pantry) {
  return request<void>({ url: '/pantry', method: 'put', data })
}

export function deletePantry(id: number) {
  return request<void>({ url: `/pantry/${id}`, method: 'delete' })
}
