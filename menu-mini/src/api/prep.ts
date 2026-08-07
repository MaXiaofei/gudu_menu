import { request } from '@/utils/request'

/** 备料状态（后端 PrepStatus enum 名）。 */
export type PrepStatus = 'PENDING' | 'READY' | 'THAWING' | 'MARINATING'

/** 备菜列表一行（后端 PrepItemVO record）。 */
export interface PrepItem {
  ingredientId: number
  ingredientName: string
  /** 聚合总克数（已 ×servingFactor，不减库存）。 */
  totalGrams: number
  /** 被几道菜用到。 */
  dishCount: number
  /** 用到该食材的菜名列表（共用高亮用）。 */
  dishNames: string[]
  status: PrepStatus
  /** 是否共用项（dishCount >= 2），前端 🔥 高亮便利字段。 */
  shared: boolean
}

/** 备菜聚合（后端 MenuPrepVO record）。 */
export interface MenuPrepVO {
  /** 全部用料（含调味料，需备料、计入进度）。 */
  items: PrepItem[]
  /** 后端已不再折叠调料，恒为空（保留字段兼容）。 */
  condiments: PrepItem[]
  /** 已备数（items 中 status=READY 的数量）。 */
  readyCount: number
  /** 共需备料数（= items.length，含调料）。 */
  totalCount: number
}

/** 备菜聚合：GET /menu/{id}/prep。 */
export const getMenuPrep = (menuId: number) =>
  request<MenuPrepVO>({ url: `/menu/${menuId}/prep`, method: 'GET' })

/** 更新备料状态：PUT /menu/{id}/prep/{ingredientId}?status=READY（query 拼接，参照 markDone 范式）。 */
export const updatePrepStatus = (
  menuId: number,
  ingredientId: number,
  status: PrepStatus,
) => request({ url: `/menu/${menuId}/prep/${ingredientId}?status=${status}`, method: 'PUT' })
