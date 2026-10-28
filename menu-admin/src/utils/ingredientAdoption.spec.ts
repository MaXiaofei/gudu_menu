import { describe, it, expect } from 'vitest'
import { categorySummary, defaultCategoryId, filterGroups, formatTime, resultMessage } from './ingredientAdoption'
import type { AdoptionGroup } from '@/api/ingredientAdoption'

const g = (over: Partial<AdoptionGroup> = {}): AdoptionGroup => ({
  name: '青花椒油', familyCount: 3, ingredientCount: 3, earliest: '2026-10-18T10:00:00',
  categories: [{ categoryId: 2, categoryName: '调味料', count: 2 }, { categoryId: 1, categoryName: '蔬菜', count: 1 }],
  suggestedCategoryId: 2, ingredientIds: [11, 12, 13], ...over,
})

describe('utils/ingredientAdoption', () => {
  it('分类分布文案', () => {
    expect(categorySummary(g().categories)).toBe('调味料 ×2 · 蔬菜 ×1')
    expect(categorySummary([])).toBe('—')
  })

  it('默认分类：优先多数家庭所选，缺失取分布里第一个有效分类', () => {
    expect(defaultCategoryId(g())).toBe(2)
    expect(defaultCategoryId(g({ suggestedCategoryId: null }))).toBe(2)
    expect(defaultCategoryId(g({ suggestedCategoryId: null, categories: [{ categoryId: null, categoryName: '未分类', count: 1 }] }))).toBeNull()
  })

  it('按名称过滤', () => {
    const list = [g(), g({ name: '家传酱' })]
    expect(filterGroups(list, '  ').length).toBe(2)
    expect(filterGroups(list, '家传').map((x) => x.name)).toEqual(['家传酱'])
  })

  it('时间与结果文案', () => {
    expect(formatTime('2026-10-18T10:00:00')).toBe('2026-10-18 10:00')
    expect(formatTime(null)).toBe('—')
    expect(resultMessage({ targetName: '花椒油', mergedIngredients: 3, movedRows: 5, dedupedRows: 1, aliasesAdded: ['青花椒油'] }))
      .toBe('已合并 3 个家庭食材到「花椒油」（改指向 5 条记录，去重 1 条），新增别名 青花椒油')
  })
})
