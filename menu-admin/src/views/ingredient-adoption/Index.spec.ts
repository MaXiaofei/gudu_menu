import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const api = vi.hoisted(() => ({
  listAdoptionGroups: vi.fn(),
  listAliases: vi.fn(),
  adoptIngredient: vi.fn(),
  mergeIngredient: vi.fn(),
  addAlias: vi.fn(),
  deleteAlias: vi.fn(),
}))
vi.mock('@/api/ingredientAdoption', () => api)
vi.mock('@/api/ingredient', () => ({ listIngredients: vi.fn().mockResolvedValue([{ id: 3, name: '马铃薯' }]) }))
vi.mock('@/api/dict', () => ({ listByGroup: vi.fn().mockResolvedValue([{ id: 2, name: '调味料' }, { id: 1, name: '蔬菜' }]) }))
vi.mock('element-plus', async (orig) => {
  const mod: any = await orig()
  return { ...mod, ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() } }
})

import Index from './Index.vue'

const groups = [
  {
    name: '青花椒油', familyCount: 3, ingredientCount: 3, earliest: '2026-10-18T10:00:00',
    categories: [{ categoryId: 2, categoryName: '调味料', count: 2 }, { categoryId: 1, categoryName: '蔬菜', count: 1 }],
    suggestedCategoryId: 2, ingredientIds: [11, 12, 13],
  },
  {
    name: '家传酱', familyCount: 1, ingredientCount: 1, earliest: null,
    categories: [{ categoryId: 1, categoryName: '蔬菜', count: 1 }], suggestedCategoryId: 1, ingredientIds: [14],
  },
]

function mountPage() {
  return mount(Index, { global: { plugins: [ElementPlus] }, attachTo: document.body })
}

describe('家庭新建食材（运营收编页）', () => {
  beforeEach(() => {
    Object.values(api).forEach((f) => f.mockReset())
    api.listAdoptionGroups.mockResolvedValue(groups)
    api.listAliases.mockResolvedValue({ records: [{ id: 5, alias: '洋芋', ingredientId: 3, ingredientName: '马铃薯', createTime: null }], total: 1 })
  })

  it('按家庭数列出名称、各家分类分布', async () => {
    const w = mountPage()
    await flushPromises()
    const text = w.get('[data-testid="group-table"]').text()
    expect(text).toContain('青花椒油')
    expect(text).toContain('调味料 ×2 · 蔬菜 ×1')
    expect(text).toContain('2026-10-18 10:00')
    expect(w.findAll('[data-testid="btn-adopt"]').length).toBe(2)
  })

  it('收编：默认带出多数家庭所选分类，确认后调用收编接口', async () => {
    api.adoptIngredient.mockResolvedValue({ targetId: 9, targetName: '青花椒油', mergedIngredients: 3, movedRows: 4, dedupedRows: 0, aliasesAdded: [] })
    const w = mountPage()
    await flushPromises()
    await w.findAll('[data-testid="btn-adopt"]')[0].trigger('click')
    await flushPromises()
    const confirm = document.body.querySelector('[data-testid="adopt-confirm"]') as HTMLElement
    expect(confirm).toBeTruthy()
    confirm.click()
    await flushPromises()
    expect(api.adoptIngredient).toHaveBeenCalledWith({
      groupName: '青花椒油', name: '青花椒油', purchaseCategoryId: 2, reason: undefined,
    })
  })

  it('未选公共食材就合并 -> 不调用接口', async () => {
    const w = mountPage()
    await flushPromises()
    await w.findAll('[data-testid="btn-merge"]')[0].trigger('click')
    await flushPromises()
    ;(document.body.querySelector('[data-testid="merge-confirm"]') as HTMLElement).click()
    await flushPromises()
    expect(api.mergeIngredient).not.toHaveBeenCalled()
  })

  it('别名页签：列表展示，缺信息时不新增', async () => {
    const w = mountPage()
    await flushPromises()
    await w.get('[data-testid="tab-aliases"]').trigger('click')
    await flushPromises()
    expect(w.get('[data-testid="alias-table"]').text()).toContain('洋芋')
    expect(w.get('[data-testid="alias-table"]').text()).toContain('马铃薯')
    await w.get('[data-testid="alias-add"]').trigger('click')
    expect(api.addAlias).not.toHaveBeenCalled()
  })
})
