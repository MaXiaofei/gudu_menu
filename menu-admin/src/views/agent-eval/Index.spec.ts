import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const api = vi.hoisted(() => ({
  createEvalCase: vi.fn(),
  listEvalCases: vi.fn(),
  listEvalFailures: vi.fn(),
  listEvalRuns: vi.fn(),
  listTraces: vi.fn(),
  setEvalBaseline: vi.fn(),
  startEvalRun: vi.fn(),
}))
vi.mock('@/api/agentAdmin', () => api)
const confirm = vi.hoisted(() => vi.fn())
vi.mock('element-plus', async (orig) => {
  const mod: any = await orig()
  return { ...mod, ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() }, ElMessageBox: { confirm } }
})

import Index from './Index.vue'

const run = (id: number, extra: Record<string, unknown> = {}) => ({
  runId: id, promptVersion: `v${id}`, toolAccuracy: 0.95, argAccuracy: 0.9, pointCoverage: 0.8,
  avgCostFen: 1, p95Ms: 1000, passed: true, status: 'DONE', baseline: false, createdAt: `2026-10-0${id}T10:00:00`, ...extra,
})

function mountPage() {
  return mount(Index, { global: { plugins: [ElementPlus] }, attachTo: document.body })
}

describe('Agent 评测：基线', () => {
  beforeEach(() => {
    Object.values(api).forEach((f) => f.mockReset())
    api.listEvalCases.mockResolvedValue({ total: 3, byIntent: [] })
    api.listEvalFailures.mockResolvedValue([])
    api.listTraces.mockResolvedValue({ items: [] })
    confirm.mockReset()
  })

  it('基线显示以后端 baseline 标记为准，当前基线无「设为基线」按钮', async () => {
    api.listEvalRuns.mockResolvedValue([run(1, { baseline: true }), run(2)])
    const w = mountPage()
    await flushPromises()
    expect(w.find('[data-testid="runs"]').text()).toContain('当前基线：#1')
    expect(w.findAll('[data-testid="baseline-tag"]')).toHaveLength(1)
    expect(w.find('[data-testid="set-baseline-1"]').exists()).toBe(false)
    expect(w.find('[data-testid="set-baseline-2"]').exists()).toBe(true)
  })

  it('没有基线时不再猜测，提示还没有基线', async () => {
    api.listEvalRuns.mockResolvedValue([run(1), run(2)])
    const w = mountPage()
    await flushPromises()
    expect(w.find('[data-testid="runs"]').text()).toContain('还没有基线')
  })

  it('设为基线：二次确认后调用接口并刷新', async () => {
    api.listEvalRuns.mockResolvedValueOnce([run(1, { baseline: true }), run(2)])
    api.listEvalRuns.mockResolvedValueOnce([run(1), run(2, { baseline: true })])
    confirm.mockResolvedValue('confirm')
    api.setEvalBaseline.mockResolvedValue({})
    const w = mountPage()
    await flushPromises()
    await w.find('[data-testid="set-baseline-2"]').trigger('click')
    await flushPromises()
    expect(confirm).toHaveBeenCalledOnce()
    expect(api.setEvalBaseline).toHaveBeenCalledWith(2)
    expect(api.listEvalRuns).toHaveBeenCalledTimes(2)
    expect(w.find('[data-testid="runs"]').text()).toContain('当前基线：#2')
  })

  it('取消确认不调用接口', async () => {
    api.listEvalRuns.mockResolvedValue([run(1, { baseline: true }), run(2)])
    confirm.mockRejectedValue('cancel')
    const w = mountPage()
    await flushPromises()
    await w.find('[data-testid="set-baseline-2"]').trigger('click')
    await flushPromises()
    expect(api.setEvalBaseline).not.toHaveBeenCalled()
  })

  it('未完成的运行不能设为基线', async () => {
    api.listEvalRuns.mockResolvedValue([run(1), run(2, { status: 'RUNNING', passed: false })])
    const w = mountPage()
    await flushPromises()
    expect(w.find('[data-testid="set-baseline-2"]').attributes('disabled')).toBeDefined()
  })

  it('运行列表加载失败：显示加载失败和重试', async () => {
    api.listEvalRuns.mockRejectedValueOnce(new Error('网络错误'))
    api.listEvalRuns.mockResolvedValueOnce([run(1)])
    const w = mountPage()
    await flushPromises()
    const err = w.find('[data-testid="eval-load-error"]')
    expect(err.text()).toContain('加载失败')
    await err.find('button').trigger('click')
    await flushPromises()
    expect(w.find('[data-testid="eval-load-error"]').exists()).toBe(false)
    expect(w.find('[data-testid="runs"]').exists()).toBe(true)
  })
})
