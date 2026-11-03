import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const api = vi.hoisted(() => ({ listOperationLogs: vi.fn() }))
vi.mock('@/api/backoffice', () => api)

import Index from './Index.vue'

function mountPage() {
  return mount(Index, { global: { plugins: [ElementPlus] }, attachTo: document.body })
}

describe('操作日志', () => {
  beforeEach(() => {
    api.listOperationLogs.mockReset().mockResolvedValue({ records: [], total: 0 })
  })

  it('操作人、对象类型进入查询参数；非法操作人不发请求', async () => {
    const w = mountPage()
    await flushPromises()
    expect(api.listOperationLogs).toHaveBeenLastCalledWith({ pageNum: 1, pageSize: 15 })
    await w.get('input[data-testid="oplog-operator"]').setValue('abc')
    await w.get('[data-testid="oplog-filters"]').trigger('submit')
    await flushPromises()
    expect(api.listOperationLogs).toHaveBeenCalledTimes(1)
    await w.get('input[data-testid="oplog-operator"]').setValue('7')
    await w.get('[data-testid="oplog-filters"]').trigger('submit')
    await flushPromises()
    expect(api.listOperationLogs).toHaveBeenLastCalledWith({ operatorId: 7, pageNum: 1, pageSize: 15 })
    await w.findAll('.yh-seg button')[1].trigger('click')
    await flushPromises()
    expect(api.listOperationLogs).toHaveBeenLastCalledWith({ targetType: 'ACCOUNT', operatorId: 7, pageNum: 1, pageSize: 15 })
  })

  it('接口失败 -> 加载失败 + 重试', async () => {
    api.listOperationLogs.mockReset().mockRejectedValueOnce(new Error('超时')).mockResolvedValue({ records: [], total: 0 })
    const w = mountPage()
    await flushPromises()
    expect(w.get('[data-testid="load-error"]').text()).toContain('超时')
    await w.get('[data-testid="load-error-retry"]').trigger('click')
    await flushPromises()
    expect(w.find('[data-testid="load-error"]').exists()).toBe(false)
  })
})
