import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { __testUni } from '../setup'
import {
  FEATURE_MAX_STALE_MS,
  FEATURE_POLL_MS,
  FEATURE_THROTTLE_MS,
  featureState,
  featureStatus,
  loadFeatureSwitches,
  refreshFeatureSwitchesThrottled,
  resetFeatureRefreshClock,
  startFeaturePolling,
  stopFeaturePolling,
} from '@/utils/featureSwitch'
import { agentTabState } from '@/utils/agentTab'
import { setTheme } from '@/utils/theme'
import { onAppLaunch, onAppShow, onTabPageShow } from '@/utils/launch'

// 开关拉取时机表（问题一补充）：每个时机都要有测试。
//  1 冷启动·有 token        onAppLaunch 恢复缓存并同步标签；随后 onShow 强制拉取
//  2 冷启动·无 token        同上（游客也拉，失败静默）；标签恒为「推荐」
//  3 静默登录进行中         标签保持「推荐」，不因默认值闪「咕嘟」
//  4 静默登录成功 / 失败后  成功：强制拉取再同步；失败：保持游客态，不跳转
//  5 热启动回前台 onShow    强制拉取（不节流）+ 开启前台轮询
//  6 登录成功后             强制拉取再同步
//  7 退出登录后             标签回「推荐」并强制拉取
//  8 401 静默重登成功后     强制拉取再同步
//  9 切换主题后             标签文字 / 图标保持当前模式
// 10 进入 AI 相关页面       强制拉取（见静态保护）
// 11 收到 5032~5035         立即切暂停状态并落缓存
// 12 底部标签各页 onShow    重新同步标签 + 节流拉取（FEATURE_THROTTLE_MS 内不重复发请求）
// 13 前台空闲               每 FEATURE_POLL_MS 轮询；后台改开关最长 FEATURE_MAX_STALE_MS 后可见
vi.mock('@/api/auth', () => ({ silentLogin: vi.fn() }))
import { silentLogin } from '@/api/auth'

const ALL_ON = { agentChat: true, cookAsk: true, vision: true, mealPlan: true }
const reply = (data: any) =>
  vi.mocked(__testUni.mocks.request).mockResolvedValueOnce({ statusCode: 200, data: { code: 0, msg: 'ok', data } } as any)
const tabText = () =>
  vi.mocked((globalThis as any).uni.setTabBarItem).mock.calls.map((c: any[]) => c[0]).filter((o: any) => o.index === 2).at(-1)?.text
const fetchCount = () => vi.mocked(__testUni.mocks.request).mock.calls.filter((c: any[]) => String(c[0].url).includes('/feature-switches')).length
const src = (p: string) => readFileSync(resolve(__dirname, '../../src', p), 'utf8')

beforeEach(() => {
  __testUni.resetStorage()
  Object.assign(featureStatus, ALL_ON)
  featureState.known = false
  agentTabState.mode = 'recommend'
  ;(globalThis as any).uni.setTabBarStyle = vi.fn()
  ;(globalThis as any).uni.setTabBarItem = vi.fn()
  ;(globalThis as any).uni.addInterceptor = vi.fn()
  vi.mocked(__testUni.mocks.request).mockReset()
  vi.mocked(__testUni.mocks.reLaunch).mockReset()
  vi.mocked(silentLogin).mockReset()
  resetFeatureRefreshClock()
  setTheme('cream')
})
afterEach(() => {
  stopFeaturePolling()
  vi.useRealTimers()
})

describe('时机 2/3/4：游客与静默登录', () => {
  it('无 token 冷启动：游客标签恒为「推荐」，onShow 也会拉（失败静默）', async () => {
    vi.mocked(__testUni.mocks.request).mockRejectedValue(new Error('offline'))
    onAppLaunch({ path: 'pages/dish/List', query: {} })
    await onAppShow()
    expect(tabText()).toBe('推荐')
    expect(fetchCount()).toBe(1)
  })

  it('静默登录进行中：标签保持「推荐」', async () => {
    const { setConsent } = await import('@/utils/consent')
    setConsent()
    let finish!: (v: any) => void
    vi.mocked(silentLogin).mockImplementation(() => new Promise((r) => (finish = r)))
    onAppLaunch({ path: 'pages/dish/List', query: {} })
    await onAppShow()
    expect(tabText()).toBe('推荐')
    finish(null)
  })

  it('静默登录失败：保持游客态，不跳转，标签「推荐」', async () => {
    const { setConsent } = await import('@/utils/consent')
    setConsent()
    vi.mocked(silentLogin).mockResolvedValue(null)
    vi.mocked(__testUni.mocks.request).mockRejectedValue(new Error('offline'))
    onAppLaunch({ path: 'pages/dish/List', query: {} })
    const { launchAuthDone } = await import('@/utils/loginRedirect')
    await launchAuthDone()
    expect(__testUni.mocks.reLaunch).not.toHaveBeenCalled()
    expect(tabText()).not.toBe('咕嘟')
  })
})

describe('时机 5：回前台', () => {
  it('每次 onShow 都强制拉取，不受节流影响', async () => {
    __testUni.setStorage('token', 't')
    reply(ALL_ON)
    reply(ALL_ON)
    await onAppShow()
    await onAppShow()
    expect(fetchCount()).toBe(2)
  })
})

describe('时机 6/7/8：会话变化后强制重拉', () => {
  it('登录成功 / 退出 / 401 重登成功：源码都会调用开关刷新', () => {
    expect(src('pages/login/Login.vue')).toContain('loadFeatureSwitches')
    expect(src('store/auth.ts')).toContain('loadFeatureSwitches')
    expect(src('utils/request.ts')).toContain('loadFeatureSwitches')
  })
})

describe('时机 12：底部标签页 onShow', () => {
  it('同步标签；节流窗口内不重复发请求，窗口外再拉', async () => {
    vi.useFakeTimers()
    __testUni.setStorage('token', 't')
    reply(ALL_ON)
    await onTabPageShow()
    expect(tabText()).toBe('咕嘟')
    await onTabPageShow()
    expect(fetchCount()).toBe(1)
    vi.advanceTimersByTime(FEATURE_THROTTLE_MS + 1)
    reply({ ...ALL_ON, agentChat: false })
    await onTabPageShow()
    expect(fetchCount()).toBe(2)
    expect(tabText()).toBe('推荐')
  })

  it('节流窗口内上一次拉取失败不记为成功，下一次 onShow 立即重试', async () => {
    __testUni.setStorage('token', 't')
    vi.mocked(__testUni.mocks.request).mockRejectedValueOnce(new Error('x'))
    await onTabPageShow()
    reply(ALL_ON)
    await onTabPageShow()
    expect(fetchCount()).toBe(2)
  })

  it('并发的两次节流刷新共用一次请求', async () => {
    __testUni.setStorage('token', 't')
    reply(ALL_ON)
    await Promise.all([refreshFeatureSwitchesThrottled(), refreshFeatureSwitchesThrottled()])
    expect(fetchCount()).toBe(1)
  })
})

describe('时机 13：后台改开关后的可见延迟', () => {
  it('最长延迟 = 服务端缓存 15 秒 + 前台轮询周期', () => {
    expect(FEATURE_POLL_MS).toBe(60_000)
    expect(FEATURE_MAX_STALE_MS).toBe(15_000 + FEATURE_POLL_MS)
  })

  it('前台空闲不操作：到一个轮询周期就看到新状态；停止轮询后不再请求', async () => {
    vi.useFakeTimers()
    __testUni.setStorage('token', 't')
    reply(ALL_ON)
    await loadFeatureSwitches()
    startFeaturePolling()
    reply({ ...ALL_ON, agentChat: false })
    await vi.advanceTimersByTimeAsync(FEATURE_POLL_MS - 1)
    expect(featureStatus.agentChat).toBe(true)
    await vi.advanceTimersByTimeAsync(2)
    expect(featureStatus.agentChat).toBe(false)
    expect(tabText()).toBe('推荐')
    stopFeaturePolling()
    const n = fetchCount()
    await vi.advanceTimersByTimeAsync(FEATURE_POLL_MS * 3)
    expect(fetchCount()).toBe(n)
  })

  it('重复 startFeaturePolling 只有一个定时器', async () => {
    vi.useFakeTimers()
    startFeaturePolling()
    startFeaturePolling()
    vi.mocked(__testUni.mocks.request).mockResolvedValue({ statusCode: 200, data: { code: 0, msg: 'ok', data: ALL_ON } } as any)
    await vi.advanceTimersByTimeAsync(FEATURE_POLL_MS + 1)
    expect(fetchCount()).toBe(1)
  })
})

describe('时机 9/10：换主题与 AI 页面（静态保护）', () => {
  it('AI 相关页面进入时都强制拉取开关', () => {
    for (const f of [
      'pkg-agent/Chat.vue', 'pkg-agent/Voice.vue', 'pkg-ai/CookMode.vue', 'pkg-ai/Scan.vue', 'pkg-ai/ScanResult.vue',
      'pkg-ai/WeekPlan.vue', 'pages/agent/Index.vue', 'pages/pantry/List.vue', 'pages/member/Index.vue', 'pages/profile/Legal.vue',
    ])
      expect(src(f), f).toMatch(/loadFeatureSwitches\(|onTabPageShow|useAgentTabSync/)
  })

  it('换主题后保持当前模式', () => {
    __testUni.setStorage('token', 't')
    featureState.known = true
    featureStatus.agentChat = false
    setTheme('matcha')
    expect(tabText()).toBe('推荐')
  })
})
