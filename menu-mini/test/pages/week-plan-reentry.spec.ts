import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createApp, nextTick, type App } from 'vue'

// pkg-ai/WeekPlan.vue：排餐一次要二三十秒，加载中不能再触发新请求（每个请求都消耗每日 10 次额度）

let loadHook: ((opts?: Record<string, string>) => Promise<void>) | null = null
vi.mock('@dcloudio/uni-app', () => ({
  onLoad: (fn: any) => {
    loadHook = fn
  },
}))

const api = vi.hoisted(() => ({ createDraft: vi.fn() }))
vi.mock('@/api/mealplan', async (orig) => ({ ...(await orig<any>()), createDraft: api.createDraft }))
vi.mock('@/api/member', () => ({ listMembers: vi.fn().mockResolvedValue([]) }))
vi.mock('@/utils/featureSwitch', () => ({ loadFeatureSwitches: vi.fn(), entryOn: () => true }))

import WeekPlan from '@/pkg-ai/WeekPlan.vue'

const flush = async () => {
  for (let i = 0; i < 5; i++) {
    await Promise.resolve()
    await nextTick()
  }
}

describe('pkg-ai/WeekPlan.vue 防重入', () => {
  let app: App
  let root: HTMLElement
  let finish: (v: any) => void

  beforeEach(async () => {
    api.createDraft.mockReset()
    api.createDraft.mockImplementation(() => new Promise((resolve) => (finish = resolve)))
    root = document.createElement('div')
    document.body.appendChild(root)
    app = createApp(WeekPlan)
    app.config.warnHandler = () => {}
    // 头部组件未全局注册：用桩把 action 插槽渲染出来
    app.component('ui-back-header', { template: '<div><slot name="action" /></div>' })
    app.mount(root)
    await loadHook!({})
    await flush()
  })

  afterEach(() => {
    app.unmount()
    root.remove()
  })

  it('加载中连点「重新排」不会再发请求，按钮置灰', async () => {
    expect(api.createDraft).toHaveBeenCalledTimes(1)
    const redo = root.querySelector('[data-testid="plan-redo"]') as HTMLElement
    expect(redo.className).toContain('disabled')
    redo.click()
    redo.click()
    await flush()
    expect(api.createDraft).toHaveBeenCalledTimes(1)
  })

  it('请求结束后可再次点「重新排」', async () => {
    finish(null)
    await flush()
    const redo = root.querySelector('[data-testid="plan-redo"]') as HTMLElement
    expect(redo.className).not.toContain('disabled')
    redo.click()
    await flush()
    expect(api.createDraft).toHaveBeenCalledTimes(2)
  })
})
