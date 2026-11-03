import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'

const api = vi.hoisted(() => ({
  listDictPaged: vi.fn(),
  listByGroup: vi.fn(),
  createDict: vi.fn(),
  updateDict: vi.fn(),
  deleteDict: vi.fn(),
  listNutritionMetrics: vi.fn(),
  createNutritionMetric: vi.fn(),
  updateNutritionMetric: vi.fn(),
}))
vi.mock('@/api/dict', () => api)
vi.mock('element-plus', async (orig) => {
  const mod: any = await orig()
  return { ...mod, ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() } }
})

import Index from './Index.vue'
import { ElMessage } from 'element-plus'

function mountPage(role: 'SUPER_ADMIN' | 'OPERATOR') {
  localStorage.setItem('gudu-is-admin', 'true')
  localStorage.setItem('gudu-role', role)
  setActivePinia(createPinia())
  return mount(Index, { global: { plugins: [ElementPlus] }, attachTo: document.body })
}

describe('配置中心', () => {
  beforeEach(() => {
    Object.values(api).forEach((f) => f.mockReset())
    api.listDictPaged.mockResolvedValue({ records: [{ id: 1, dictGroup: 'cuisine', name: '川菜', sort: 0 }], total: 1 })
    api.listByGroup.mockResolvedValue([{ id: 1, dictGroup: 'cuisine', name: '川菜', sort: 0 }])
    api.listNutritionMetrics.mockResolvedValue([{ id: 1, name: 'protein', unit: 'g', metricGroup: 'macro', sort: 0 }])
    vi.mocked(ElMessage.warning).mockClear()
  })

  it('新增同名字典项 -> 提示已存在，不调用新增接口', async () => {
    const w = mountPage('SUPER_ADMIN')
    await flushPromises()
    await w.get('.toolbar .el-button--primary:last-child').trigger('click')
    await flushPromises()
    const input = document.body.querySelector('input[data-testid="dict-name"]') as HTMLInputElement
    input.value = '川菜'
    input.dispatchEvent(new Event('input'))
    ;(document.body.querySelector('[data-testid="dict-save"]') as HTMLElement).click()
    await flushPromises()
    expect(api.createDict).not.toHaveBeenCalled()
    expect(vi.mocked(ElMessage.warning).mock.calls[0][0]).toContain('已存在')
  })

  it('超管能看到营养指标编辑入口，运营只读', async () => {
    const su = mountPage('SUPER_ADMIN')
    await flushPromises()
    expect(su.find('[data-testid="metric-add"]').exists()).toBe(true)
    expect(su.find('[data-testid="metric-edit"]').exists()).toBe(true)
    su.unmount()
    const op = mountPage('OPERATOR')
    await flushPromises()
    expect(op.find('[data-testid="metric-add"]').exists()).toBe(false)
    expect(op.find('[data-testid="metric-edit"]').exists()).toBe(false)
    expect(op.text()).toContain('仅查看')
  })

  it('字典列表接口失败 -> 加载失败 + 重试', async () => {
    api.listDictPaged.mockReset().mockRejectedValueOnce(new Error('挂了')).mockResolvedValue({ records: [], total: 0 })
    const w = mountPage('SUPER_ADMIN')
    await flushPromises()
    expect(w.get('[data-testid="load-error"]').text()).toContain('挂了')
    await w.get('[data-testid="load-error-retry"]').trigger('click')
    await flushPromises()
    expect(w.find('[data-testid="load-error"]').exists()).toBe(false)
  })
})
