import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus, { ElMessageBox } from 'element-plus'

const api = vi.hoisted(() => ({ getDishDetail: vi.fn() }))
const lib = vi.hoisted(() => ({ listLibrary: vi.fn(), offlineDish: vi.fn(), onlineDish: vi.fn() }))
vi.mock('@/api/dish', () => api)
vi.mock('@/api/dishLibrary', () => lib)
vi.mock('@/api/ingredient', () => ({ listIngredients: vi.fn().mockResolvedValue([]) }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('element-plus', async (orig) => {
  const mod: any = await orig()
  return {
    ...mod,
    ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
    ElMessageBox: { confirm: vi.fn() },
  }
})

import Index from './Index.vue'

const row = { id: 1, name: '滑蛋虾仁', createTime: '2026-10-01T10:00:00', offline: false }
const offRow = { id: 2, name: '旧菜', createTime: '2026-09-01T10:00:00', offline: true, offlineAt: '2026-11-01T10:00:00', offlineReason: '图片侵权' }
const steps = Array.from({ length: 6 }, (_, i) => ({ text: `步骤${i + 1}` }))

function mountPage() {
  return mount(Index, { global: { plugins: [ElementPlus] }, attachTo: document.body })
}

describe('公共菜谱库', () => {
  beforeEach(() => {
    lib.listLibrary.mockReset().mockResolvedValue({ records: [row], total: 1 })
    lib.offlineDish.mockReset().mockResolvedValue({ ...row, offline: true })
    lib.onlineDish.mockReset().mockResolvedValue({ ...offRow, offline: false })
    api.getDishDetail.mockReset().mockResolvedValue({ ingredients: [], steps })
    ;(ElMessageBox.confirm as any).mockReset().mockResolvedValue('confirm')
    document.body.innerHTML = ''
  })

  it('有上架状态筛选标签与状态列，没有投稿审核占位标签', async () => {
    const w = mountPage()
    await flushPromises()
    const text = w.text()
    for (const t of ['待审核', '已驳回']) expect(text).not.toContain(t)
    expect(w.find('[data-testid="lib-tabs"]').exists()).toBe(true)
    expect(w.get('[data-testid="lib-tabs"]').text()).toContain('已上架')
    expect(w.get('[data-testid="lib-tabs"]').text()).toContain('已下架')
    expect(w.get('[data-testid="lib-table"]').text()).toContain('已上架')
    expect(text).toContain('编辑菜谱')
  })

  it('点「已下架」标签 -> 按 status=offline 重新查询并回到第 1 页', async () => {
    const w = mountPage()
    await flushPromises()
    lib.listLibrary.mockResolvedValue({ records: [offRow], total: 1 })
    await w.get('[data-testid="lib-tab-offline"]').trigger('click')
    await flushPromises()
    expect(lib.listLibrary).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'offline', pageNum: 1 }))
    expect(w.get('[data-testid="lib-status-offline"]').text()).toBe('已下架')
    expect(w.get('[data-testid="lib-offline-note"]').text()).toContain('图片侵权')
  })

  it('上架的菜显示「下架」按钮；填原因 + 二次确认后才调用下架接口', async () => {
    const w = mountPage()
    await flushPromises()
    expect(w.find('[data-testid="lib-online-btn"]').exists()).toBe(false)
    await w.get('[data-testid="lib-offline-btn"]').trigger('click')
    await flushPromises()

    // 不填原因 -> 不确认、不调用
    await w.get('[data-testid="lib-offline-submit"]').trigger('click')
    await flushPromises()
    expect(ElMessageBox.confirm).not.toHaveBeenCalled()
    expect(lib.offlineDish).not.toHaveBeenCalled()

    const input = document.body.querySelector('[data-testid="lib-offline-reason"]') as HTMLTextAreaElement
    input.value = '  图片侵权  '
    input.dispatchEvent(new Event('input'))
    await flushPromises()
    await w.get('[data-testid="lib-offline-submit"]').trigger('click')
    await flushPromises()
    expect(ElMessageBox.confirm).toHaveBeenCalledTimes(1)
    expect(lib.offlineDish).toHaveBeenCalledWith(1, '图片侵权')
  })

  it('二次确认点取消 -> 不调用下架接口', async () => {
    ;(ElMessageBox.confirm as any).mockRejectedValue('cancel')
    const w = mountPage()
    await flushPromises()
    await w.get('[data-testid="lib-offline-btn"]').trigger('click')
    await flushPromises()
    const input = document.body.querySelector('[data-testid="lib-offline-reason"]') as HTMLTextAreaElement
    input.value = '做法有误'
    input.dispatchEvent(new Event('input'))
    await flushPromises()
    await w.get('[data-testid="lib-offline-submit"]').trigger('click')
    await flushPromises()
    expect(lib.offlineDish).not.toHaveBeenCalled()
  })

  it('已下架的菜显示「重新上架」按钮，确认后调用上架接口', async () => {
    lib.listLibrary.mockResolvedValue({ records: [offRow], total: 1 })
    const w = mountPage()
    await flushPromises()
    expect(w.find('[data-testid="lib-offline-btn"]').exists()).toBe(false)
    await w.get('[data-testid="lib-online-btn"]').trigger('click')
    await flushPromises()
    expect(ElMessageBox.confirm).toHaveBeenCalledTimes(1)
    expect(lib.onlineDish).toHaveBeenCalledWith(2)
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
    lib.listLibrary.mockReset().mockRejectedValueOnce(new Error('服务异常')).mockResolvedValue({ records: [row], total: 1 })
    const w = mountPage()
    await flushPromises()
    expect(w.get('[data-testid="load-error"]').text()).toContain('服务异常')
    await w.get('[data-testid="load-error-retry"]').trigger('click')
    await flushPromises()
    expect(w.find('[data-testid="load-error"]').exists()).toBe(false)
    expect(w.get('[data-testid="lib-table"]').text()).toContain('滑蛋虾仁')
  })
})
