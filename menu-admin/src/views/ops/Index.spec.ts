import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const api = vi.hoisted(() => ({ getOpsHealth: vi.fn(), rebuildDishVectors: vi.fn() }))
const ai = vi.hoisted(() => ({ listAiCallLogs: vi.fn() }))
const auth = vi.hoisted(() => ({ isSuperAdminStored: vi.fn() }))
const push = vi.hoisted(() => vi.fn())
const msg = vi.hoisted(() => ({ confirm: vi.fn() }))
vi.mock('@/api/ops', () => api)
vi.mock('@/api/ai', () => ai)
vi.mock('@/utils/auth-storage', () => auth)
vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))
vi.mock('element-plus', async (orig) => {
  const mod: any = await orig()
  return { ...mod, ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() }, ElMessageBox: { confirm: msg.confirm } }
})

import { ElMessage } from 'element-plus'
import Index from './Index.vue'

const report = {
  status: 'err',
  items: [
    { key: 'app', label: '应用服务', status: 'ok', latencyMs: 1, detail: '版本 开发版 · 已运行 3 分钟', error: null },
    { key: 'mysql', label: 'MySQL', status: 'ok', latencyMs: 4, detail: '查询正常', error: null },
    { key: 'redis', label: 'Redis', status: 'err', latencyMs: 2000, detail: null, error: '超时（超过 2000 毫秒）' },
  ],
}

function mountPage() {
  return mount(Index, { global: { plugins: [ElementPlus] }, attachTo: document.body })
}

describe('系统健康页', () => {
  beforeEach(() => {
    api.getOpsHealth.mockReset().mockResolvedValue(report)
    api.rebuildDishVectors.mockReset()
    ai.listAiCallLogs.mockReset().mockResolvedValue({ records: [] })
    msg.confirm.mockReset()
    vi.mocked(ElMessage.success).mockClear()
    push.mockReset()
  })

  it('运营：只调健康摘要，不发超管接口，也不显示重建按钮', async () => {
    auth.isSuperAdminStored.mockReturnValue(false)
    const w = mountPage()
    await flushPromises()
    expect(api.getOpsHealth).toHaveBeenCalledTimes(1)
    expect(ai.listAiCallLogs).not.toHaveBeenCalled()
    expect(w.find('[data-testid="btn-rebuild"]').exists()).toBe(false)
    expect(w.find('[data-testid="ops-failures"]').exists()).toBe(false)
    const text = w.get('[data-testid="ops-cards"]').text()
    expect(text).toContain('MySQL')
    expect(text).toContain('4 毫秒')
    expect(text).toContain('超时（超过 2000 毫秒）')
  })

  it('审计入口跳转操作日志页', async () => {
    auth.isSuperAdminStored.mockReturnValue(false)
    const w = mountPage()
    await flushPromises()
    await w.get('[data-testid="goto-oplog"]').trigger('click')
    expect(push).toHaveBeenCalledWith('/oplog')
  })

  it('超管：看到重建按钮，取消确认不调用接口', async () => {
    auth.isSuperAdminStored.mockReturnValue(true)
    msg.confirm.mockRejectedValue('cancel')
    const w = mountPage()
    await flushPromises()
    expect(ai.listAiCallLogs).toHaveBeenCalled()
    await w.get('[data-testid="btn-rebuild"]').trigger('click')
    await flushPromises()
    expect(api.rebuildDishVectors).not.toHaveBeenCalled()
  })

  it('超管：确认后重建并提示结果', async () => {
    auth.isSuperAdminStored.mockReturnValue(true)
    msg.confirm.mockResolvedValue('confirm')
    api.rebuildDishVectors.mockResolvedValue({ total: 10, ok: 10, error: null })
    const w = mountPage()
    await flushPromises()
    await w.get('[data-testid="btn-rebuild"]').trigger('click')
    await flushPromises()
    expect(api.rebuildDishVectors).toHaveBeenCalledTimes(1)
    expect(ElMessage.success).toHaveBeenCalled()
  })

  it('健康摘要读取失败显示错误条', async () => {
    auth.isSuperAdminStored.mockReturnValue(false)
    api.getOpsHealth.mockRejectedValue(new Error('x'))
    const w = mountPage()
    await flushPromises()
    expect(w.find('[data-testid="ops-health-failed"]').exists()).toBe(true)
  })
})
