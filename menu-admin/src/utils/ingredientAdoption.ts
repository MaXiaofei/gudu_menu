// 家庭新建食材收编页的展示规则（纯函数，便于单测）。
import type { AdoptionGroup, CategoryShare } from '@/api/ingredientAdoption'

/** 各家所选分类分布的一行文案：「调味料 ×2 · 蔬菜 ×1」。 */
export function categorySummary(categories: CategoryShare[]): string {
  if (!categories.length) return '—'
  return categories.map((c) => `${c.categoryName} ×${c.count}`).join(' · ')
}

/** 收编默认分类：取多数家庭所选（后端已算好 suggestedCategoryId），缺失时取分布里第一个有效分类。 */
export function defaultCategoryId(g: AdoptionGroup): number | null {
  if (g.suggestedCategoryId != null) return g.suggestedCategoryId
  const first = g.categories.find((c) => c.categoryId != null)
  return first?.categoryId ?? null
}

/** 按名称关键字过滤汇总行（大小写不敏感）。 */
export function filterGroups(groups: AdoptionGroup[], keyword: string): AdoptionGroup[] {
  const kw = keyword.trim().toLowerCase()
  if (!kw) return groups
  return groups.filter((g) => g.name.toLowerCase().includes(kw))
}

/** 日期显示到分钟；空值显示「—」。 */
export function formatTime(t: string | null | undefined): string {
  return t ? t.replace('T', ' ').slice(0, 16) : '—'
}

/** 收编 / 合并结果的提示文案。 */
export function resultMessage(r: {
  targetName: string
  mergedIngredients: number
  movedRows: number
  dedupedRows: number
  aliasesAdded: string[]
}): string {
  const alias = r.aliasesAdded.length ? `，新增别名 ${r.aliasesAdded.join('、')}` : ''
  return `已合并 ${r.mergedIngredients} 个家庭食材到「${r.targetName}」（改指向 ${r.movedRows} 条记录，去重 ${r.dedupedRows} 条）${alias}`
}
