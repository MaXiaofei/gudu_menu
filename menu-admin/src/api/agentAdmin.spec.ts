import { describe, it, expect, vi, beforeEach } from 'vitest'

const requestFn = vi.fn()
vi.mock('./request', () => ({
  request: (...args: any[]) => requestFn(...args),
}))

const api = await import('./agentAdmin')

describe('api/agentAdmin 调用链隐私', () => {
  beforeEach(() => {
    requestFn.mockReset()
    requestFn.mockResolvedValue({})
  })

  it('详情 / 对话默认走脱敏摘要接口（GET，不带原因）', async () => {
    await api.getTrace('t 1')
    expect(requestFn).toHaveBeenLastCalledWith({ url: '/admin/agent/traces/t%201', method: 'get' })
    await api.getTraceMessages('t1')
    expect(requestFn).toHaveBeenLastCalledWith({ url: '/admin/agent/traces/t1/messages', method: 'get' })
  })

  it('查看全文：POST /full，原因放请求体（不进 URL）', async () => {
    await api.getTraceFull('t1', '用户投诉回答异常')
    expect(requestFn).toHaveBeenLastCalledWith({
      url: '/admin/agent/traces/t1/full', method: 'post', data: { reason: '用户投诉回答异常' },
    })
  })
})
