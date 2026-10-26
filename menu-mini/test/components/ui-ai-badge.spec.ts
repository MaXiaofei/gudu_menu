import { describe, it, expect, afterEach, vi, beforeEach } from 'vitest'
import { createApp, h, nextTick, type App, type Component } from 'vue'
import { __testUni } from '../setup'
import UiAiBadge from '@/components/ui-ai-badge.vue'
import AssistantMessage from '@/pkg-agent/parts/AssistantMessage.vue'
import type { AgentMessage } from '@/api/agent'
import { AI_LABEL } from '@/utils/aiLabel'

// mini-release-compliance A.4：AI 生成标识 / 免责提示 / 反馈入口。
let app: App | null = null
let root: HTMLElement

async function mount(comp: Component, props: Record<string, unknown> = {}, globals: Record<string, Component> = {}) {
  root = document.createElement('div')
  document.body.appendChild(root)
  app = createApp({ render: () => h(comp, props) })
  app.config.warnHandler = () => {}
  for (const [n, c] of Object.entries(globals)) app.component(n, c)
  app.mount(root)
  await nextTick()
  return root
}
beforeEach(() => vi.mocked(__testUni.mocks.navigateTo).mockClear())
afterEach(() => {
  app?.unmount()
  root?.remove()
  app = null
})

describe('ui-ai-badge', () => {
  it('默认只显示「AI 生成」胶囊，无免责、无反馈', async () => {
    const r = await mount(UiAiBadge)
    expect(r.querySelector('[data-testid="ai-badge-chip"]')?.textContent).toBe(AI_LABEL)
    expect(r.querySelector('[data-testid="ai-disclaimer"]')).toBeNull()
    expect(r.querySelector('[data-testid="ai-feedback"]')).toBeNull()
  })

  it('disclaimer：胶囊 +「仅供参考，不能替代医生或营养师建议」', async () => {
    const r = await mount(UiAiBadge, { disclaimer: true })
    expect(r.querySelector('[data-testid="ai-badge-chip"]')?.textContent).toBe('AI 生成')
    expect(r.querySelector('[data-testid="ai-disclaimer"]')?.textContent).toBe('仅供参考，不能替代医生或营养师建议')
  })

  it('反馈入口：复用意见反馈页，带 type=ai + 来源 + 关联 id', async () => {
    const r = await mount(UiAiBadge, { source: 'scan', feedbackRef: 42 })
    ;(r.querySelector('[data-testid="ai-feedback"]') as HTMLElement).click()
    expect(__testUni.mocks.navigateTo).toHaveBeenCalledWith({ url: '/pages/profile/Feedback?type=ai&source=scan&ref=42' })
  })
})

describe('咕嘟助手 AI 消息', () => {
  const msg: AgentMessage = {
    messageId: 'm-1', role: 'assistant', text: '今晚做清蒸鲈鱼', tools: [], cards: [], actions: [],
    traceId: 't-1', createdAt: '2026-10-26T10:00:00', streaming: false,
  }
  it('回复完成后带「AI 生成」标识与反馈入口（来源 agent + 消息 id）', async () => {
    const stub = { render: () => null }
    const r = await mount(AssistantMessage, { msg, family: [] }, {
      'ui-ai-badge': UiAiBadge, 'ui-icon': stub, 'ui-dish-image': stub, 'ui-ing-icon': stub, 'ui-expiry-badge': stub,
    })
    expect(r.querySelector('[data-testid="ai-badge-chip"]')?.textContent).toBe('AI 生成')
    ;(r.querySelector('[data-testid="ai-feedback"]') as HTMLElement).click()
    expect(__testUni.mocks.navigateTo).toHaveBeenCalledWith({ url: '/pages/profile/Feedback?type=ai&source=agent&ref=m-1' })
  })
})
