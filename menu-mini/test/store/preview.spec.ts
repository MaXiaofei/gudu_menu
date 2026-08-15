import { describe, it, expect } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { usePreviewStore } from '@/store/preview'
import { reviewDimensions, submitReview } from '@/api/review'
import { installHttpMock } from '../helpers/http'

beforeEach(() => setActivePinia(createPinia()))

// store/preview：写菜谱预览数据传递（替代 Flutter 路由 extra）。
describe('store/preview', () => {
  it('set / clear', () => {
    const s = usePreviewStore()
    expect(s.data).toBeNull()
    s.set({
      name: '番茄炒蛋', coverLocal: '', coverUrl: '', prepTime: '5', cookTime: '5',
      difficulty: 3, tags: ['家常菜'], note: '', ingredients: [], steps: [],
    })
    expect(s.data!.name).toBe('番茄炒蛋')
    s.clear()
    expect(s.data).toBeNull()
  })
})

// api/review：维度字典兼容 / 提交 payload。
describe('api/review', () => {
  it('reviewDimensions：兼容数组与 {records}', async () => {
    installHttpMock({ 'GET /dict': [{ id: 1, name: '口味' }] })
    expect(await reviewDimensions()).toEqual([{ id: 1, name: '口味' }])
  })

  it('submitReview：dishId/menuId 二选一 + 分项分值', async () => {
    const http = installHttpMock({ 'POST /review': null })
    await submitReview({
      dishId: 9,
      starRating: 4,
      text: '不错',
      images: ['/uploads/original/a.jpg'],
      dimensionScores: { '1': 4 },
    })
    expect(http.calls[0].data).toEqual({
      dishId: 9,
      menuId: undefined,
      starRating: 4,
      text: '不错',
      images: ['/uploads/original/a.jpg'],
      dimensionScores: { '1': 4 },
    })
  })
})
