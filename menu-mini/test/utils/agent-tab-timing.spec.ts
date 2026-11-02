import { describe, it, expect, beforeEach, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { __testUni } from '../setup'
import { loadPagesJson } from '../helpers/pagesJson'
import {
  featureState,
  featureStatus,
  loadFeatureSwitches,
  noteSuspendedCode,
  restoreFeatureSwitches,
  FEATURE_CACHE_KEY,
} from '@/utils/featureSwitch'
import { agentTabState } from '@/utils/agentTab'
import { applyTabBar, setTheme, syncAgentTab } from '@/utils/theme'
import { onAppLaunch, onAppShow } from '@/utils/launch'

// 问题一（真机）：关停咕嘟助手后底部第三个标签应一直是「推荐」；
// 此前开关未拉到前默认「开」、静默登录后不重新拉、并发拉取互相覆盖、拉取失败保持默认开，都会把标签刷回「咕嘟」。
vi.mock('@/api/auth', () => ({ silentLogin: vi.fn() }))
import { silentLogin } from '@/api/auth'

const reply = (data: any, code = 0) =>
  vi.mocked(__testUni.mocks.request).mockResolvedValueOnce({ statusCode: 200, data: { code, msg: 'ok', data } } as any)

const lastAgentItem = () =>
  vi.mocked((globalThis as any).uni.setTabBarItem).mock.calls.map((c: any[]) => c[0]).filter((o: any) => o.index === 2).at(-1)

const flush = () => new Promise((r) => setTimeout(r, 0))

beforeEach(() => {
  __testUni.resetStorage()
  Object.assign(featureStatus, { agentChat: true, cookAsk: true, vision: true, mealPlan: true })
  featureState.known = false
  agentTabState.mode = 'recommend'
  ;(globalThis as any).uni.setTabBarStyle = vi.fn()
  ;(globalThis as any).uni.setTabBarItem = vi.fn()
  vi.mocked(__testUni.mocks.request).mockReset()
  vi.mocked(silentLogin).mockReset()
  setTheme('cream')
})

describe('标签模式：开关状态未知时不出现「咕嘟」', () => {
  it('已登录、开关还没拉到、本地也没有缓存：保守显示「推荐」', () => {
    __testUni.setStorage('token', 't')
    expect(syncAgentTab()).toBe('recommend')
    expect(lastAgentItem().text).toBe('推荐')
  })

  it('已登录、拉取成功且咕嘟助手开：「咕嘟」', async () => {
    __testUni.setStorage('token', 't')
    reply({ agentChat: true, cookAsk: true, vision: true, mealPlan: true })
    await loadFeatureSwitches()
    expect(syncAgentTab()).toBe('agent')
  })

  it('拉取失败（断网）且没有缓存：保持「推荐」，不当成开', async () => {
    __testUni.setStorage('token', 't')
    vi.mocked(__testUni.mocks.request).mockRejectedValueOnce(new Error('timeout'))
    await loadFeatureSwitches()
    expect(syncAgentTab()).toBe('recommend')
  })

  it('换主题（applyTabBar）在开关未知 / 关闭时也不会把标签刷回「咕嘟」', () => {
    __testUni.setStorage('token', 't')
    syncAgentTab()
    setTheme('matcha')
    applyTabBar('cream')
    expect(lastAgentItem().text).toBe('推荐')
    expect(lastAgentItem().iconPath).toContain('recommend-')
  })
})

describe('本地缓存上次的开关状态，启动先用它，再以服务端为准', () => {
  it('拉取成功后写入本地缓存', async () => {
    reply({ agentChat: false, cookAsk: false, vision: true, mealPlan: true })
    await loadFeatureSwitches()
    expect(__testUni.getStorage(FEATURE_CACHE_KEY)).toMatchObject({ agentChat: false, cookAsk: false, vision: true, mealPlan: true })
  })

  it('冷启动：缓存为关 → 登录用户第一帧就是「推荐」，拉取失败也保持', async () => {
    __testUni.setStorage('token', 't')
    __testUni.setStorage(FEATURE_CACHE_KEY, { agentChat: false, cookAsk: false, vision: true, mealPlan: true })
    restoreFeatureSwitches()
    expect(featureStatus.agentChat).toBe(false)
    expect(syncAgentTab()).toBe('recommend')
    vi.mocked(__testUni.mocks.request).mockRejectedValueOnce(new Error('offline'))
    await loadFeatureSwitches()
    expect(featureStatus.agentChat).toBe(false)
    expect(syncAgentTab()).toBe('recommend')
  })

  it('冷启动：缓存为开 → 先「咕嘟」；服务端已改为关 → 拉取后翻成「推荐」', async () => {
    __testUni.setStorage('token', 't')
    __testUni.setStorage(FEATURE_CACHE_KEY, { agentChat: true, cookAsk: true, vision: true, mealPlan: true })
    restoreFeatureSwitches()
    expect(syncAgentTab()).toBe('agent')
    reply({ agentChat: false, cookAsk: true, vision: true, mealPlan: true })
    await loadFeatureSwitches()
    expect(syncAgentTab()).toBe('recommend')
    expect(__testUni.getStorage(FEATURE_CACHE_KEY).agentChat).toBe(false)
  })

  it('缓存内容损坏 / 缺字段：忽略，视为未知', () => {
    __testUni.setStorage(FEATURE_CACHE_KEY, 'oops')
    restoreFeatureSwitches()
    expect(featureState.known).toBe(false)
    __testUni.setStorage(FEATURE_CACHE_KEY, { agentChat: 'x' })
    restoreFeatureSwitches()
    expect(featureState.known).toBe(false)
  })
})

describe('并发拉取：以最后发起的那次为准', () => {
  it('先发起的慢响应（开）晚于后发起的快响应（关）到达，不覆盖', async () => {
    let resolveSlow!: (v: any) => void
    vi.mocked(__testUni.mocks.request).mockImplementationOnce(
      () => new Promise((r) => (resolveSlow = r)) as any,
    )
    reply({ agentChat: false, cookAsk: false, vision: true, mealPlan: true })
    const slow = loadFeatureSwitches()
    await flush()
    const fast = loadFeatureSwitches()
    await fast
    expect(featureStatus.agentChat).toBe(false)
    resolveSlow({ statusCode: 200, data: { code: 0, msg: 'ok', data: { agentChat: true, cookAsk: true, vision: true, mealPlan: true } } })
    await slow
    expect(featureStatus.agentChat).toBe(false)
    expect(__testUni.getStorage(FEATURE_CACHE_KEY).agentChat).toBe(false)
  })
})

describe('收到 5032 立即把标签翻成「推荐」', () => {
  it('已显示「咕嘟」时收到咕嘟助手暂停码：无需等下一次同步', async () => {
    __testUni.setStorage('token', 't')
    reply({ agentChat: true, cookAsk: true, vision: true, mealPlan: true })
    await loadFeatureSwitches()
    syncAgentTab()
    expect(lastAgentItem().text).toBe('咕嘟')
    noteSuspendedCode(5032)
    expect(agentTabState.mode).toBe('recommend')
    expect(lastAgentItem().text).toBe('推荐')
  })

  it('5033 / 5034 / 5035 不影响标签', async () => {
    __testUni.setStorage('token', 't')
    reply({ agentChat: true, cookAsk: true, vision: true, mealPlan: true })
    await loadFeatureSwitches()
    syncAgentTab()
    for (const c of [5033, 5034, 5035]) noteSuspendedCode(c)
    syncAgentTab()
    expect(agentTabState.mode).toBe('agent')
  })
})

describe('启动流程里开关的拉取时机', () => {
  it('onLaunch：先恢复缓存再同步标签（缓存为关 → 首帧「推荐」）', () => {
    __testUni.setStorage('token', 't')
    __testUni.setStorage(FEATURE_CACHE_KEY, { agentChat: false, cookAsk: true, vision: true, mealPlan: true })
    reply({ agentChat: false, cookAsk: true, vision: true, mealPlan: true })
    onAppLaunch({ path: 'pages/dish/List', query: {} })
    expect(lastAgentItem().text).toBe('推荐')
  })

  it('静默登录成功后重新拉开关，再同步标签（此前只同步不重拉）', async () => {
    __testUni.setStorage('consent', '1')
    __testUni.setStorage('agreed_terms', '1')
    const { setConsent } = await import('@/utils/consent')
    setConsent()
    vi.mocked(silentLogin).mockImplementation(async () => {
      __testUni.setStorage('token', 'new')
      return { token: 'new' } as any
    })
    reply({ agentChat: false, cookAsk: false, vision: false, mealPlan: false })
    onAppLaunch({ path: 'pages/dish/List', query: {} })
    const { launchAuthDone } = await import('@/utils/loginRedirect')
    await launchAuthDone()
    await flush()
    await flush()
    expect(__testUni.mocks.request).toHaveBeenCalled()
    expect((vi.mocked(__testUni.mocks.request).mock.calls[0][0] as any).url).toContain('/feature-switches')
    expect(featureStatus.agentChat).toBe(false)
    expect(lastAgentItem().text).toBe('推荐')
  })

  it('onShow：同步标签并拉开关，拉完再同步', async () => {
    __testUni.setStorage('token', 't')
    reply({ agentChat: true, cookAsk: true, vision: true, mealPlan: true })
    await onAppShow()
    expect(lastAgentItem().text).toBe('咕嘟')
    reply({ agentChat: false, cookAsk: false, vision: true, mealPlan: true })
    await onAppShow()
    expect(lastAgentItem().text).toBe('推荐')
  })
})

describe('静态保护：任何时序下 tabBar 不会先于判定显示「咕嘟」', () => {
  const src = (p: string) => readFileSync(resolve(__dirname, '../../src', p), 'utf8')

  it('pages.json 第三个 tab 的静态文字 / 图标是「推荐」（setTabBarItem 失败时也是安全方向）', () => {
    const tab = loadPagesJson().tabBar.list[2]
    expect(tab.pagePath).toBe('pages/agent/Index')
    expect(tab.text).toBe('推荐')
    expect(tab.iconPath).toContain('recommend-')
    expect(tab.selectedIconPath).toContain('recommend-')
  })

  it('所有 tab 页 onShow 都会重新同步标签（setTabBarItem 在非 tab 页会静默失败）', () => {
    for (const t of loadPagesJson().tabBar.list) {
      const code = src(t.pagePath + '.vue')
      expect(code, t.pagePath).toMatch(/useAgentTabSync\(\)|syncAgentTab\(\)/)
    }
  })

  it('各 AI 页面拉取开关的地方不再各自直接改 tab（统一由 syncAgentTab 订阅）', () => {
    expect(src('App.vue')).toContain('onAppLaunch')
  })
})
