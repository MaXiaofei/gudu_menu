import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { createApp, h, nextTick, type App, type Component } from 'vue'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { resolve, join } from 'node:path'
import { __testUni } from '../setup'
import {
  entryOn,
  featureCover,
  featureState,
  featureStatus,
  FEATURE_CACHE_KEY,
  loadFeatureSwitches,
  restoreFeatureSwitches,
  type FeatureName,
} from '@/utils/featureSwitch'
import UiFeaturePaused from '@/components/ui-feature-paused.vue'
import UiAgentPaused from '@/components/ui-agent-paused.vue'

// AI 入口不闪出：开关状态未知（还没拉到、也没缓存）时入口一律隐藏，暂停页也不先闪。
const NAMES: FeatureName[] = ['agentChat', 'cookAsk', 'vision', 'mealPlan']
const SRC = resolve(__dirname, '../../src')
const read = (p: string) => readFileSync(resolve(SRC, p), 'utf8')
const tpl = (p: string) => {
  const s = read(p)
  return s.slice(0, s.indexOf('<script'))
}
const reply = (data: any) =>
  vi.mocked(__testUni.mocks.request).mockResolvedValueOnce({ statusCode: 200, data: { code: 0, msg: 'x', data } } as any)
const setCache = (v: Record<FeatureName, boolean>) => (globalThis as any).uni.setStorageSync(FEATURE_CACHE_KEY, v)

beforeEach(() => {
  __testUni.resetStorage()
  Object.assign(featureStatus, { agentChat: true, cookAsk: true, vision: true, mealPlan: true })
  featureState.known = false
  ;(featureState as any).failed = false
  vi.mocked(__testUni.mocks.request).mockReset()
})

describe('entryOn / featureCover 判断', () => {
  it.each(NAMES)('%s：未知隐藏 / 已知开显示 / 已知关隐藏', (n) => {
    expect(entryOn(n)).toBe(false) // 未知（默认 true 也不能显示）
    featureState.known = true
    expect(entryOn(n)).toBe(true)
    featureStatus[n] = false
    expect(entryOn(n)).toBe(false)
  })

  it.each(NAMES)('%s：覆盖层 未知=加载 / 开=无 / 关=暂停；拉取失败且无缓存=不挡页面', (n) => {
    expect(featureCover(n)).toBe('loading')
    featureState.known = true
    expect(featureCover(n)).toBe(null)
    featureStatus[n] = false
    expect(featureCover(n)).toBe('paused')
    featureState.known = false
    ;(featureState as any).failed = true
    expect(featureCover(n)).toBe(null)
  })

  it('缓存为关的冷启动首帧：入口隐藏，覆盖层直接是暂停（不先闪正常）', () => {
    setCache({ agentChat: false, cookAsk: false, vision: false, mealPlan: false })
    restoreFeatureSwitches()
    for (const n of NAMES) {
      expect(entryOn(n)).toBe(false)
      expect(featureCover(n)).toBe('paused')
    }
  })

  it('拉取成功由未知变开：入口出现；变关：保持隐藏', async () => {
    reply({ agentChat: true, cookAsk: true, vision: true, mealPlan: true })
    expect(entryOn('vision')).toBe(false)
    await loadFeatureSwitches()
    for (const n of NAMES) expect(entryOn(n)).toBe(true)
    reply({ agentChat: true, cookAsk: true, vision: false, mealPlan: false })
    await loadFeatureSwitches()
    expect(entryOn('vision')).toBe(false)
    expect(entryOn('mealPlan')).toBe(false)
    expect(entryOn('agentChat')).toBe(true)
  })

  it('拉取失败且无缓存：入口仍隐藏，覆盖层撤掉（页面可用，后端兜底拦截）', async () => {
    vi.mocked(__testUni.mocks.request).mockRejectedValueOnce(new Error('net'))
    await loadFeatureSwitches()
    expect(entryOn('vision')).toBe(false)
    expect(featureCover('vision')).toBe(null)
  })
})

describe('源码守卫：入口显隐统一走 entryOn', () => {
  const vueFiles: string[] = []
  const walk = (d: string) => {
    for (const f of readdirSync(d)) {
      const p = join(d, f)
      if (statSync(p).isDirectory()) walk(p)
      else if (p.endsWith('.vue')) vueFiles.push(p)
    }
  }
  walk(SRC)

  it('任何 .vue 都不得直接引用 featureStatus（暂停页也走 featureCover）', () => {
    const bad = vueFiles.filter((p) => readFileSync(p, 'utf8').includes('featureStatus'))
    expect(bad.map((p) => p.replace(SRC + '/', ''))).toEqual([])
  })

  const ENTRIES: Array<[string, string, string]> = [
    ['pages/pantry/List.vue', "entryOn('vision')", 'fab-scan'],
    ['pages/agent/Index.vue', "entryOn('vision')", 'agent-scan'],
    ['pkg-agent/Chat.vue', "entryOn('vision')", 'chat-scan'],
    ['pages/menu/List.vue', "entryOn('mealPlan')", 'go-weekplan'],
    ['pkg-ai/Nutrition.vue', "entryOn('mealPlan')", 'swap-into-plan'],
    ['pkg-agent/parts/AssistantMessage.vue', "entryOn('mealPlan')", "c.type === 'plan'"],
    ['pages/agent/Index.vue', "entryOn('agentChat')", 'hero-swap'],
    ['pkg-community/Topic.vue', "entryOn('agentChat')", 'topic-ask'],
  ]
  it.each(ENTRIES)('%s 用 %s 控制 %s', (file, expr, marker) => {
    const line = tpl(file)
      .split('\n')
      .find((l) => l.includes(marker))!
    expect(line, `${file} 找不到 ${marker}`).toBeTruthy()
    expect(line).toContain(expr)
  })
  it('咕嘟页：快捷提问过滤 / 临期按钮 / 问咕嘟 askOn 用 entryOn', () => {
    expect(read('pages/agent/Index.vue')).toContain("entryOn('vision') || !(p.includes('拍')")
    expect(tpl('pages/agent/Index.vue')).toMatch(/v-if="entryOn\('agentChat'\)" class="exp-btn"/)
    expect(read('pkg-ai/CookMode.vue')).toContain("entryOn('cookAsk') && isLoggedIn()")
  })
  it('各 AI 页面都挂了覆盖层，并在进入时强制拉取', () => {
    for (const f of ['pkg-ai/Scan.vue', 'pkg-ai/ScanResult.vue', 'pkg-ai/WeekPlan.vue', 'pkg-agent/Chat.vue', 'pkg-agent/Voice.vue']) {
      expect(tpl(f)).toMatch(/<ui-(feature|agent)-paused/)
      expect(read(f)).toContain('loadFeatureSwitches()')
    }
    expect(read('pkg-ai/CookMode.vue')).toContain('loadFeatureSwitches()')
    for (const f of ['pages/pantry/List.vue', 'pages/agent/Index.vue', 'pkg-community/Topic.vue']) {
      expect(read(f)).toContain('loadFeatureSwitches()')
    }
    // 食集页入口靠底部标签同步拉取
    expect(read('pages/menu/List.vue')).toContain('useAgentTabSync()')
  })
})

describe('暂停页：未知时不先闪暂停提示', () => {
  let app: App | null = null
  let root: HTMLElement
  async function mount(comp: Component, props: Record<string, unknown>) {
    root = document.createElement('div')
    document.body.appendChild(root)
    app = createApp({ render: () => h(comp, props) })
    app.config.warnHandler = () => {}
    app.component('ui-icon', { render: () => h('i') })
    app.component('ui-state', {
      props: ['mode', 'text'],
      render() {
        return h('span', { class: 'state', 'data-mode': (this as any).mode }, (this as any).text)
      },
    })
    app.mount(root)
    await nextTick()
    return root
  }
  afterEach(() => {
    app?.unmount()
    root?.remove()
    app = null
  })

  it('ui-feature-paused：未知=加载态（无暂停提示）；已知开=不渲染；已知关=暂停提示', async () => {
    const el = await mount(UiFeaturePaused, { feature: 'vision' })
    expect(el.querySelector('[data-mode="loading"]')).not.toBeNull()
    expect(el.textContent).not.toContain('暂停服务')
    featureState.known = true
    await nextTick()
    expect(el.querySelector('.ap')).toBeNull()
    featureStatus.vision = false
    await nextTick()
    expect(el.textContent).toContain('拍照识别暂停服务')
  })

  it('ui-agent-paused：未知=加载态且无 agent-paused 标记；已知关=暂停', async () => {
    const el = await mount(UiAgentPaused, {})
    expect(el.querySelector('[data-testid="agent-paused"]')).toBeNull()
    expect(el.textContent).not.toContain('暂停服务')
    featureState.known = true
    featureStatus.agentChat = false
    await nextTick()
    expect(el.querySelector('[data-testid="agent-paused"]')).not.toBeNull()
  })

  it('缓存为关的冷启动首帧直接是暂停页；拉取成功变开后消失', async () => {
    setCache({ agentChat: true, cookAsk: true, vision: false, mealPlan: true })
    restoreFeatureSwitches()
    const el = await mount(UiFeaturePaused, { feature: 'vision' })
    expect(el.textContent).toContain('拍照识别暂停服务')
    reply({ agentChat: true, cookAsk: true, vision: true, mealPlan: true })
    await loadFeatureSwitches()
    await nextTick()
    expect(el.querySelector('.ap')).toBeNull()
  })
})
