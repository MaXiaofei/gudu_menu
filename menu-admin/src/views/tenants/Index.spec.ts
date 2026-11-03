import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const api = vi.hoisted(() => ({
  listAccounts: vi.fn(),
  banAccount: vi.fn(),
  unbanAccount: vi.fn(),
  accountConsents: vi.fn(),
}))
vi.mock('@/api/backoffice', () => api)
vi.mock('@/api/ops', () => ({ getOpsStats: vi.fn().mockResolvedValue(null) }))

import Index from './Index.vue'

const acc = { id: 5, nickname: '小王', status: 'ACTIVE', platformRole: 'USER', familyCount: 1, banned: false }

describe('账号与家庭', () => {
  beforeEach(() => {
    Object.values(api).forEach((f) => f.mockReset())
    api.listAccounts.mockResolvedValue({ records: [acc], total: 1 })
  })

  it('封禁对话框有「立即下线」醒目提示', async () => {
    const w = mount(Index, { global: { plugins: [ElementPlus] }, attachTo: document.body })
    await flushPromises()
    await w.get('[data-testid="acc-ban"]').trigger('click')
    await flushPromises()
    const warn = document.body.querySelector('[data-testid="ban-warning"]')
    expect(warn?.textContent).toContain('封禁后该账号会立即下线')
  })

  it('列表接口失败 -> 加载失败 + 重试', async () => {
    api.listAccounts.mockReset().mockRejectedValueOnce(new Error('断网')).mockResolvedValue({ records: [acc], total: 1 })
    const w = mount(Index, { global: { plugins: [ElementPlus] }, attachTo: document.body })
    await flushPromises()
    expect(w.get('[data-testid="load-error"]').text()).toContain('断网')
    await w.get('[data-testid="load-error-retry"]').trigger('click')
    await flushPromises()
    expect(w.find('[data-testid="acc-table"]').exists()).toBe(true)
  })
})
