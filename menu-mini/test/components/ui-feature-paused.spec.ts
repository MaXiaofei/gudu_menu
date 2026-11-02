import { describe, it, expect, afterEach, beforeEach } from 'vitest'
import { createApp, h, nextTick, type App, type Component } from 'vue'
import UiFeaturePaused from '@/components/ui-feature-paused.vue'
import { featureState, featureStatus, SUSPENDED_MESSAGE } from '@/utils/featureSwitch'

// 生成式 AI 暂停服务盖层：按类别显示「XX暂停服务，其他功能不受影响」
let app: App | null = null
let root: HTMLElement

async function mount(comp: Component, props: Record<string, unknown>) {
  root = document.createElement('div')
  document.body.appendChild(root)
  app = createApp({ render: () => h(comp, props) })
  app.config.warnHandler = () => {}
  // 内部子组件（ui-state / ui-icon）以全局占位渲染，便于断言 text
  app.component('ui-icon', { render: () => h('i') })
  app.component('ui-state', { props: ['text'], render() { return h('span', { class: 'state' }, (this as any).text) } })
  app.mount(root)
  await nextTick()
  return root
}
beforeEach(() => {
  featureState.known = true
  featureStatus.agentChat = true
  featureStatus.cookAsk = true
  featureStatus.vision = true
  featureStatus.mealPlan = true
})
afterEach(() => {
  app?.unmount()
  root?.remove()
  app = null
})

describe('ui-feature-paused', () => {
  it('开着不渲染', async () => {
    const el = await mount(UiFeaturePaused, { feature: 'vision' })
    expect(el.querySelector('[data-testid="vision-paused"]')).toBeNull()
  })

  it('拍照识别关闭：显示拍照识别暂停提示；一周餐程不受影响', async () => {
    featureStatus.vision = false
    const el = await mount(UiFeaturePaused, { feature: 'vision' })
    expect(el.querySelector('[data-testid="vision-paused"]')).not.toBeNull()
    expect(el.textContent).toContain('拍照识别暂停服务，其他功能不受影响')
    expect(SUSPENDED_MESSAGE.mealPlan).toBe('一周餐程暂停服务，其他功能不受影响')
  })

  it('一周餐程关闭：显示一周餐程暂停提示', async () => {
    featureStatus.mealPlan = false
    const el = await mount(UiFeaturePaused, { feature: 'mealPlan' })
    expect(el.textContent).toContain('一周餐程暂停服务，其他功能不受影响')
  })
})
