import { describe, it, expect } from 'vitest'
import { installHttpMock } from '../helpers/http'
import { searchDishes, amountText, listDrafts, saveDish } from '@/api/dish'

// api/dish：搜索参数构造（空值剔除 + sort 语义）、用量文本、草稿/发布端点。
describe('api/dish', () => {
  it('searchDishes：默认每页 10，空筛选项不传，sort=cooked 才传', async () => {
    const http = installHttpMock({ 'GET /dish/search': { records: [], total: 0 } })
    await searchDishes({ pageNum: 1 })
    expect(http.calls[0].data).toEqual({ pageNum: 1, pageSize: 10 })

    await searchDishes({ keyword: '蛋', tagIds: '3', cuisineIds: '', sort: 'latest', pageNum: 2 })
    expect(http.calls[1].data).toEqual({ keyword: '蛋', tagIds: '3', pageNum: 2, pageSize: 10 })

    await searchDishes({ sort: 'cooked', pageNum: 1 })
    expect(http.calls[2].data).toEqual({ sort: 'cooked', pageNum: 1, pageSize: 10 })
  })

  it('amountText：数字+单位自然拼接（整数去小数）', () => {
    expect(amountText({ ingredientId: 1, amount: 2, unitName: '个' })).toBe('2 个')
    expect(amountText({ ingredientId: 1, amount: 2.5, unitName: '斤' })).toBe('2.5 斤')
    expect(amountText({ ingredientId: 1, unitName: '适量' })).toBe('适量')
    expect(amountText({ ingredientId: 1 })).toBe('')
  })

  it('listDrafts：分页参数', async () => {
    const http = installHttpMock({ 'GET /dish/draft/list': { records: [], total: 0 } })
    await listDrafts(3)
    expect(http.calls[0].url).toBe('/dish/draft/list')
    expect(http.calls[0].data).toEqual({ pageNum: 3, pageSize: 10 })
  })

  it('saveDish：POST /dish 带完整 payload', async () => {
    const http = installHttpMock({ 'POST /dish': 99 })
    const id = await saveDish({
      dish: { name: '番茄炒蛋' },
      steps: [{ seq: 1, sortOrder: 1, text: '打蛋' }],
      ingredients: [{ ingredientId: 1, amount: 2 }],
      tagIds: [3],
      cuisineIds: [],
    })
    expect(id).toBe(99)
    expect(http.calls[0].method).toBe('POST')
    expect(http.calls[0].data.dish.name).toBe('番茄炒蛋')
  })
})
