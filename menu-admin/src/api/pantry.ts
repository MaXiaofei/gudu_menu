import { request } from './request'

// ===================== V42 档位语义（ingredient_stock） =====================

export interface PantryLastChange {
  source?: string
  sourceNote?: string
  createTime?: string
}

export interface PantryGroupedItem {
  ingredientId: number
  ingredientName?: string
  /** ENOUGH 充足 / LOW 不足 / NONE 用完 */
  level: string
  lastChange?: PantryLastChange | null
}

export interface PantryGroupedVO {
  summary: { enough: number; low: number; none: number }
  items: PantryGroupedItem[]
}

/** 档位 → 中文文案（与 APP 一致） */
export function levelLabel(level?: string): string {
  switch (level) {
    case 'ENOUGH':
      return '充足'
    case 'LOW':
      return '不足'
    case 'NONE':
      return '用完'
    default:
      return '-'
  }
}

/** 档位 → tag 类型（element-plus） */
export function levelTagType(level?: string): 'success' | 'warning' | 'danger' {
  switch (level) {
    case 'ENOUGH':
      return 'success'
    case 'LOW':
      return 'warning'
    default:
      return 'danger'
  }
}

/** 分组列表（库存页 + 管理后台共用） */
export function listGrouped() {
  return request<PantryGroupedVO>({ url: '/pantry/grouped', method: 'get' })
}

/** 设档位（改档位/新增建档） */
export function setLevel(ingredientId: number, level: string, note?: string) {
  return request<void>({
    url: `/pantry/${ingredientId}/level`,
    method: 'put',
    data: { level, ...(note ? { note } : {}) },
  })
}

/** 删档位（删除 = 回到没建档） */
export function deleteLevel(ingredientId: number) {
  return request<void>({ url: `/pantry/${ingredientId}/level`, method: 'delete' })
}
