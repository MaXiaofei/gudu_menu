import { describe, it, expect, vi, beforeEach } from 'vitest'

const requestFn = vi.fn()
vi.mock('./request', () => ({
  request: (...args: any[]) => requestFn(...args),
}))

const api = await import('./ingredient')

describe('api/ingredient', () => {
  beforeEach(() => {
    requestFn.mockReset()
    requestFn.mockResolvedValue(undefined)
  })

  it('编辑保存：PUT /ingredient 发 {ingredient(含id), nutritions}', async () => {
    await api.updateIngredient({
      ingredient: { id: 7, name: '番茄', purchaseCategoryId: 2 },
      nutritions: [{ metricId: 1, value: 18 }],
    })
    expect(requestFn).toHaveBeenLastCalledWith({
      url: '/ingredient',
      method: 'put',
      data: {
        ingredient: { id: 7, name: '番茄', purchaseCategoryId: 2 },
        nutritions: [{ metricId: 1, value: 18 }],
      },
    })
  })

  it('新建：POST /ingredient 发 {ingredient, nutritions}', async () => {
    await api.createIngredient({ ingredient: { name: '青花椒油', purchaseCategoryId: 3 }, nutritions: [] })
    expect(requestFn).toHaveBeenLastCalledWith({
      url: '/ingredient',
      method: 'post',
      data: { ingredient: { name: '青花椒油', purchaseCategoryId: 3 }, nutritions: [] },
    })
  })
})
