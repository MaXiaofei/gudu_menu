import { request } from './request'

export interface MenuDish {
  dishId: number
  servingFactor: number
}

export interface Menu {
  id: number
  name: string
  typeId?: number
  targetMemberId?: number
  servingCount: number
  [key: string]: unknown
}

export interface MenuDetail {
  menu: Menu
  dishes: MenuDish[]
}

export interface MenuSaveDTO {
  menu: Partial<Menu> & { name: string; servingCount: number }
  dishes: MenuDish[]
}

export interface MenuSummary {
  totalPrice: number
  totalNutrition: Record<string, number>
}

export function listMenus() {
  return request<Menu[]>({ url: '/menu', method: 'get' })
}

export function getMenuDetail(id: number) {
  return request<MenuDetail>({ url: `/menu/${id}`, method: 'get' })
}

export function getMenuSummary(id: number) {
  return request<MenuSummary>({ url: `/menu/${id}/summary`, method: 'get' })
}

export function createMenu(data: MenuSaveDTO) {
  return request<number>({ url: '/menu', method: 'post', data })
}

export function updateMenu(data: MenuSaveDTO & { menu: { id: number } & MenuSaveDTO['menu'] }) {
  return request<void>({ url: '/menu', method: 'put', data })
}

export function deleteMenu(id: number) {
  return request<void>({ url: `/menu/${id}`, method: 'delete' })
}
