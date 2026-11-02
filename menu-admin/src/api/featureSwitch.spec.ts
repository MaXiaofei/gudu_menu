import { describe, it, expect, vi, beforeEach } from 'vitest'

const requestFn = vi.fn()
vi.mock('./request', () => ({
  request: (...args: any[]) => requestFn(...args),
}))

const { listFeatureSwitches, setFeatureSwitch, setPlatformSwitches } = await import('./featureSwitch')

describe('api/featureSwitch 紧急关停开关', () => {
  beforeEach(() => {
    requestFn.mockReset()
    requestFn.mockResolvedValue(undefined)
  })

  it('listFeatureSwitches → GET /admin/feature-switches', async () => {
    await listFeatureSwitches()
    expect(requestFn).toHaveBeenCalledWith({ url: '/admin/feature-switches', method: 'get' })
  })

  it('setFeatureSwitch → PUT，带 key / enabled / reason', async () => {
    await setFeatureSwitch({ key: 'agent.chat.enabled.mp', enabled: false, reason: '出现违规内容' })
    expect(requestFn).toHaveBeenCalledWith({
      url: '/admin/feature-switches',
      method: 'put',
      data: { key: 'agent.chat.enabled.mp', enabled: false, reason: '出现违规内容' },
    })
  })
  it('setPlatformSwitches → PUT /batch，带 platform / enabled / reason', async () => {
    await setPlatformSwitches({ platform: 'mp', enabled: false, reason: '提审要求' })
    expect(requestFn).toHaveBeenCalledWith({
      url: '/admin/feature-switches/batch',
      method: 'put',
      data: { platform: 'mp', enabled: false, reason: '提审要求' },
    })
  })
})
