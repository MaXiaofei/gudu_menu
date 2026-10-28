// 食材选择 / 搜索 / 新建的纯逻辑（家庭私有食材 + 别名，便于单测）。
import type { IngredientItem, ResolvedIngredient } from '@/api/ingredient'

/** 本家私有食材的小标签文案（食材库、各处选食材组件统一用）。 */
export const OWN_TAG = '我家'
/** 食材库分类筛选里「我家」这一项的 id（不会与真实分类 id 冲突）。 */
export const OWN_CATEGORY_ID = -1

/** 同名（公共 + 本家）时本家的排前面；不同名保持原有顺序。 */
export function ownFirst<T extends { name: string; familyOwned?: boolean }>(list: T[]): T[] {
  const slots = new Map<string, number[]>()
  list.forEach((it, i) => slots.set(it.name, [...(slots.get(it.name) ?? []), i]))
  const out = [...list]
  slots.forEach((idx) => {
    if (idx.length < 2) return
    const group = idx.map((i) => list[i]).sort((a, b) => Number(!!b.familyOwned) - Number(!!a.familyOwned))
    idx.forEach((slot, k) => (out[slot] = group[k]))
  })
  return out
}

/** 本地列表按名称包含匹配（已全量缓存的食材库）。 */
export function matchLocal(all: IngredientItem[], kw: string): IngredientItem[] {
  const q = kw.trim()
  return q ? ownFirst(all.filter((i) => i.name.includes(q))) : []
}

/** 合并本地与服务端（含别名命中）的搜索结果：按 id 去重，本地在前。 */
export function mergeMatches(local: IngredientItem[], remote: IngredientItem[]): IngredientItem[] {
  const seen = new Set(local.map((i) => i.id))
  return ownFirst([...local, ...remote.filter((i) => !seen.has(i.id))])
}

/** 命中别名时的副标题：「土豆 = 马铃薯」；名称本身命中则不显示。 */
export function aliasHint(it: { name: string; matchedAlias?: string | null }): string {
  return it.matchedAlias && it.matchedAlias !== it.name ? `别名：${it.matchedAlias}` : ''
}

/** 新建食材后的提示：后端命中公共食材 / 本家已有时不新建，直接选中它。 */
export function resolveTip(r: ResolvedIngredient): string {
  if (r.existed === 'public') return `已有：${r.name}`
  if (r.existed === 'family') return `你家已有「${r.name}」`
  return `已建档「${r.name}」`
}

/** 编辑页权限：本家私有食材可改可删，公共食材只读（由平台维护）。 */
export function ingredientPermission(it: { familyOwned?: boolean } | null | undefined): {
  canEdit: boolean
  readonlyTip: string
} {
  const canEdit = !!it?.familyOwned
  return { canEdit, readonlyTip: canEdit ? '' : '平台食材，由平台维护' }
}
