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

  it('所属家庭：显示「名称 · 角色」标签', async () => {
    api.listAccounts.mockResolvedValue({ records: [{ ...acc, familyCount: 2, families: [
      { familyId: 1, name: '我们家', role: 'OWNER', status: 'ACTIVE' },
      { familyId: 2, name: '爸妈家', role: 'MEMBER', status: 'ACTIVE' },
    ] }], total: 1 })
    const w = mount(Index, { global: { plugins: [ElementPlus] }, attachTo: document.body })
    await flushPromises()
    const tags = w.findAll('[data-testid="acc-fam-tag"]').map((t) => t.text())
    expect(tags).toEqual(['我们家 · 主理人', '爸妈家 · 成员'])
    expect(w.find('[data-testid="acc-fam-more"]').exists()).toBe(false)
  })

  it('所属家庭超过 3 个：只显示前 3 个 + 「等 N 个」', async () => {
    const families = [1, 2, 3, 4, 5].map((i) => ({ familyId: i, name: `家${i}`, role: i === 2 ? 'ADMIN' : 'MEMBER', status: 'ACTIVE' }))
    api.listAccounts.mockResolvedValue({ records: [{ ...acc, familyCount: 5, families }], total: 1 })
    const w = mount(Index, { global: { plugins: [ElementPlus] }, attachTo: document.body })
    await flushPromises()
    const tags = w.findAll('[data-testid="acc-fam-tag"]').map((t) => t.text())
    expect(tags).toEqual(['家1 · 成员', '家2 · 管理员', '家3 · 成员'])
    expect(w.get('[data-testid="acc-fam-more"]').text()).toBe('等 5 个')
  })

  it('没有家庭显示「—」', async () => {
    api.listAccounts.mockResolvedValue({ records: [{ ...acc, familyCount: 0, families: [] }], total: 1 })
    const w = mount(Index, { global: { plugins: [ElementPlus] }, attachTo: document.body })
    await flushPromises()
    expect(w.get('[data-testid="acc-fam-empty"]').text()).toBe('—')
    expect(w.find('[data-testid="acc-fam-tag"]').exists()).toBe(false)
  })
})
