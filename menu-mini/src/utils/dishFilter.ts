/**
 * 菜谱页「筛选 + 排序」状态的纯逻辑（便于单测）。
 * - 筛选组（分类标签 tag / 菜系 cuisine）：互相叠加，「全部」= 两者都清空；
 * - 排序组（家里够的优先 / 做得最多 / 最新）：与筛选叠加，先按筛选条件过滤再排序；
 * - 请求竞态：只认最新一次请求的结果（createLatestGuard）。
 */
import type { DishSort } from '@/api/dish'

export interface DishFilterState {
  tagId: number // 0 = 未选
  cuisineId: number // 0 = 未选
  sort: DishSort
  keyword: string
}

/** 「全部」高亮条件：没有选任何标签、也没有选任何菜系（否则会出现高亮「全部」却只显示某菜系的错乱）。 */
export function isAllActive(s: Pick<DishFilterState, 'tagId' | 'cuisineId'>): boolean {
  return !s.tagId && !s.cuisineId
}

/** 点标签 chip：id=0（全部）同时清空菜系；再次点已选标签 = 取消选择。 */
export function pickTag(s: DishFilterState, id: number): DishFilterState {
  if (id === 0) return { ...s, tagId: 0, cuisineId: 0 }
  return { ...s, tagId: s.tagId === id ? 0 : id }
}

/** 点菜系 chip：再次点已选菜系 = 取消选择（此前无法取消）。 */
export function pickCuisine(s: DishFilterState, id: number): DishFilterState {
  return { ...s, cuisineId: s.cuisineId === id ? 0 : id }
}

/** 状态 → /dish/search 参数（筛选与排序一起带上）。 */
export function toSearchParams(s: DishFilterState, pageNum: number) {
  return {
    keyword: s.keyword || undefined,
    tagIds: s.tagId ? String(s.tagId) : undefined,
    cuisineIds: s.cuisineId ? String(s.cuisineId) : undefined,
    sort: s.sort,
    pageNum,
  }
}

/** 竞态守卫：next() 发新号，isLatest(n) 判断 n 是否仍是最新一次请求。 */
export function createLatestGuard() {
  let cur = 0
  return {
    next: () => ++cur,
    isLatest: (n: number) => n === cur,
  }
}
