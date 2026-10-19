/**
 * showcase-ai-community 共用类型（openspec/changes/showcase-ai-community/contracts.md「共用类型」）。
 * 与旧接口的 DishCard（api/dish.ts，字段 dishId/cookMinutes）不同名，避免混用。
 */
export interface CDishCard {
  id: number
  name: string
  coverUrl: string | null
  minutes: number | null
  difficulty: string | null
  coverage: { have: number; total: number; missing: string[] }
  tags: string[]
}

export interface IngredientLine {
  ingredientId: number | null
  name: string
  iconKey: string | null
  amount: string
  unit: string | null
}

export interface MemberLite {
  memberId: number
  name: string
  avatarUrl: string | null
}

export interface UserLite {
  userId: number
  nickname: string
  avatarUrl: string | null
}

/** 「家里够 / 缺 1 样 · 小葱」 */
export function coverageLabel(c?: CDishCard['coverage'] | null): string {
  if (!c || !c.total) return ''
  const miss = c.total - c.have
  if (miss <= 0) return '家里够'
  return `缺 ${miss} 样${c.missing?.length ? ' · ' + c.missing.slice(0, 2).join('、') : ''}`
}

export function coverageTone(c?: CDishCard['coverage'] | null): 'full' | 'partial' | 'none' | '' {
  if (!c || !c.total) return ''
  if (c.have >= c.total) return 'full'
  return c.have > 0 ? 'partial' : 'none'
}

/** 分 → 「¥326」（整元不带小数）。 */
export function yuan(fen: number | null | undefined): string {
  const v = (fen || 0) / 100
  return `¥${Number.isInteger(v) ? v : v.toFixed(2)}`
}
