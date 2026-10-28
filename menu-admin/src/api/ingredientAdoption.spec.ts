import { describe, it, expect, vi, beforeEach } from 'vitest'

const requestFn = vi.fn()
vi.mock('./request', () => ({
  request: (...args: any[]) => requestFn(...args),
}))

const api = await import('./ingredientAdoption')

describe('api/ingredientAdoption 运营收编', () => {
  beforeEach(() => {
    requestFn.mockReset()
    requestFn.mockResolvedValue([])
  })

  it('汇总列表', async () => {
    await api.listAdoptionGroups()
    expect(requestFn).toHaveBeenLastCalledWith({ url: '/admin/ingredient-adoption/groups', method: 'get' })
  })

  it('收编为公共食材 / 合并到已有公共食材', async () => {
    await api.adoptIngredient({ groupName: '青花椒油', name: '青花椒油', purchaseCategoryId: 2, reason: '常见' })
    expect(requestFn).toHaveBeenLastCalledWith({
      url: '/admin/ingredient-adoption/adopt', method: 'post',
      data: { groupName: '青花椒油', name: '青花椒油', purchaseCategoryId: 2, reason: '常见' },
    })
    await api.mergeIngredient({ groupName: '青花椒油', targetId: 50 })
    expect(requestFn).toHaveBeenLastCalledWith({
      url: '/admin/ingredient-adoption/merge', method: 'post', data: { groupName: '青花椒油', targetId: 50 },
    })
  })

  it('别名 列表 / 新增 / 删除', async () => {
    await api.listAliases({ keyword: '土豆', pageNum: 1, pageSize: 20 })
    expect(requestFn).toHaveBeenLastCalledWith({
      url: '/admin/ingredient-adoption/aliases', method: 'get', params: { keyword: '土豆', pageNum: 1, pageSize: 20 },
    })
    await api.addAlias({ alias: '洋芋', ingredientId: 3 })
    expect(requestFn).toHaveBeenLastCalledWith({
      url: '/admin/ingredient-adoption/aliases', method: 'post', data: { alias: '洋芋', ingredientId: 3 },
    })
    await api.deleteAlias(7)
    expect(requestFn).toHaveBeenLastCalledWith({ url: '/admin/ingredient-adoption/aliases/7', method: 'delete' })
  })
})
