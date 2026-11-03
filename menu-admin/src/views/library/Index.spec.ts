import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const api = vi.hoisted(() => ({ searchDishes: vi.fn(), getDishDetail: vi.fn() }))
vi.mock('@/api/dish', () => api)
vi.mock('@/api/ingredient', () => ({ listIngredients: vi.fn().mockResolvedValue([]) }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))

import Index from './Index.vue'

const row = { id: 1, name: '滑蛋虾仁', cookedCount: 3, createTime: '2026-10-01T10:00:00' }
const steps = Array.from({ length: 6 }, (_, i) => ({ text: `步骤${i + 1}` }))

function mountPage() {
  return mount(Index, { global: { plugins: [ElementPlus] }, attachTo: document.body })
}

describe('公共菜谱库', () => {
  beforeEach(() => {
    api.searchDishes.mockReset().mockResolvedValue({ records: [row], total: 1 })
    api.getDishDetail.mockReset().mockResolvedValue({ ingredients: [], steps })
  })

  it('不再显示投稿审核占位标签、状态列与下架按钮', async () => {
    const w = mountPage()
    await flushPromises()
    const text = w.text()
    for (const t of ['待审核', '已驳回', '已下架', '已上架', '下架']) expect(text).not.toContain(t)
    expect(w.find('[data-testid="lib-tabs"]').exists()).toBe(false)
    expect(text).toContain('编辑菜谱')
  })

  it('步骤默认只显示前 4 步，可展开全部再收起', async () => {
    const w = mountPage()
    await flushPromises()
    expect(w.findAll('.steps li')).toHaveLength(4)
    const toggle = w.get('[data-testid="lib-steps-toggle"]')
    expect(toggle.text()).toContain('展开全部')
    await toggle.trigger('click')
    expect(w.findAll('.steps li')).toHaveLength(6)
    expect(w.get('[data-testid="lib-steps-toggle"]').text()).toContain('收起')
  })

  it('列表接口失败 -> 显示加载失败与重试，点重试重新请求', async () => {
    api.searchDishes.mockReset().mockRejectedValueOnce(new Error('服务异常')).mockResolvedValue({ records: [row], total: 1 })
    const w = mountPage()
    await flushPromises()
    expect(w.get('[data-testid="load-error"]').text()).toContain('服务异常')
    await w.get('[data-testid="load-error-retry"]').trigger('click')
    await flushPromises()
    expect(w.find('[data-testid="load-error"]').exists()).toBe(false)
    expect(w.get('[data-testid="lib-table"]').text()).toContain('滑蛋虾仁')
  })
})
