import { request } from '@/utils/request'

/** 食集（后端 Menu 实体）。 */
export interface Menu {
  id: number
  name: string
  typeId?: number
  targetMemberId?: number
  servingCount?: number
  /** 状态：ACTIVE 进行中 / DONE 已完成。V36 加。 */
  status?: string
  finishedAt?: string
  createTime?: string
}

/** 食集→菜关联（后端 MenuDish + 冗余菜名/封面）。 */
export interface MenuDish {
  id: number
  menuId: number
  dishId: number
  /** 该菜在食集的份数。 */
  servingFactor?: number
  /** 菜名（后端 detail 冗余返回，避免前端逐菜 GET /dish/{id}）。 */
  dishName?: string
  /** 菜封面图。 */
  coverUrl?: string
}

/** 食集详情聚合（后端 MenuService.MenuDetail record：{ menu, dishes }）。 */
export interface MenuDetailVO {
  menu: Menu
  dishes: MenuDish[]
}

/** 分页：GET /menu?pageNum=&pageSize= → IPage<Menu>：{ records, total, current, size }。 */
export const listMenus = (params: Record<string, any>) =>
  request<{ records: Menu[]; total: number; current: number; size: number }>({
    url: '/menu',
    method: 'GET',
    data: params,
  })

/** 详情：GET /menu/{id} → { menu, dishes:[{dishId, servingFactor, ...}] }。 */
export const getMenuDetail = (id: number) =>
  request<MenuDetailVO>({ url: `/menu/${id}`, method: 'GET' })
