import { describe, it, expect, beforeEach, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { __testUni } from '../setup'
import { loadPagesJson } from '../helpers/pagesJson'
import { onAppLaunch, onAppShow } from '@/utils/launch'
import { HOME_PAGE, launchTarget, resumeIfLoggedIn, trackLaunchAuth } from '@/utils/loginRedirect'
import { featureState, featureStatus } from '@/utils/featureSwitch'

// 问题二（真机）：清掉的新用户扫码打开体验版，仍先看到登录页。要求：未登录首次进入直接停在首页菜谱，
// 只有点到需要登录的功能才进登录页。这里锁住「冷启动无 token 无同意 → 停在首页、不跳登录页、不弹登录提示」。
vi.mock('@/api/auth', () => ({ silentLogin: vi.fn() }))
import { silentLogin } from '@/api/auth'

const flush = () => new Promise((r) => setTimeout(r, 0))
const src = (p: string) => readFileSync(resolve(__dirname, '../../src', p), 'utf8')

function noNavigation() {
  expect(__testUni.mocks.reLaunch).not.toHaveBeenCalled()
  expect(__testUni.mocks.navigateTo).not.toHaveBeenCalled()
  expect(__testUni.mocks.redirectTo).not.toHaveBeenCalled()
  expect(__testUni.mocks.showModal).not.toHaveBeenCalled()
}

beforeEach(() => {
  __testUni.resetStorage()
  for (const m of [
    __testUni.mocks.reLaunch, __testUni.mocks.navigateTo, __testUni.mocks.redirectTo, __testUni.mocks.switchTab,
    __testUni.mocks.showModal, __testUni.mocks.showToast, __testUni.mocks.request,
  ]) vi.mocked(m).mockReset()
  vi.mocked(silentLogin).mockReset()
  featureState.known = false
  Object.assign(featureStatus, { agentChat: true, cookAsk: true, vision: true, mealPlan: true })
  ;(globalThis as any).uni.setTabBarStyle = vi.fn()
  ;(globalThis as any).uni.setTabBarItem = vi.fn()
  ;(globalThis as any).uni.addInterceptor = vi.fn()
  // 游客读开关：后端旧版本对游客回 401，也不应有任何提示
  vi.mocked(__testUni.mocks.request).mockResolvedValue({ statusCode: 200, data: { code: 401, msg: '未登录', data: null } } as any)
})

describe('冷启动：无 token、无协议同意', () => {
  it('不跳任何页面、不弹登录提示、不发起 wx.login', async () => {
    onAppLaunch({ path: 'pages/dish/List', query: {} })
    await onAppShow()
    const { launchAuthDone } = await import('@/utils/loginRedirect')
    await launchAuthDone()
    await flush()
    noNavigation()
    expect(silentLogin).not.toHaveBeenCalled()
  })

  it('带体验版 / 扫码场景值（scene、query）也一样', async () => {
    onAppLaunch({ path: 'pages/dish/List', query: { scene: 'a%3D1' }, scene: 1011 } as any)
    await onAppShow()
    await flush()
    noNavigation()
  })

  it('主动退出过：保持游客态，同样不跳', async () => {
    __testUni.setStorage('logged_out', '1')
    onAppLaunch({ path: 'pages/dish/List', query: {} })
    await flush()
    noNavigation()
  })

  it('游客读开关被后端回 401（静默）：不弹登录提示', async () => {
    const { loadFeatureSwitches } = await import('@/utils/featureSwitch')
    await loadFeatureSwitches()
    expect(__testUni.mocks.showModal).not.toHaveBeenCalled()
    expect(__testUni.mocks.navigateTo).not.toHaveBeenCalled()
  })
})

describe('扫码路径指向登录页（体验版二维码的页面路径可能是旧的 pages/login/Login）', () => {
  it('冷启动落在登录页、无回跳参数、无登录态：登录页一加载就回首页（游客）', async () => {
    onAppLaunch({ path: 'pages/login/Login', query: {} })
    trackLaunchAuthDone()
    expect(await resumeIfLoggedIn('')).toBe(HOME_PAGE)
    expect(__testUni.mocks.switchTab).toHaveBeenCalledWith({ url: HOME_PAGE })
  })

  it('只对冷启动落地那一次生效：之后游客点「去登录」打开的登录页要正常停留', async () => {
    onAppLaunch({ path: 'pages/login/Login', query: {} })
    trackLaunchAuthDone()
    await resumeIfLoggedIn('')
    vi.mocked(__testUni.mocks.switchTab).mockClear()
    expect(await resumeIfLoggedIn('')).toBeNull()
    expect(__testUni.mocks.switchTab).not.toHaveBeenCalled()
  })

  it('带回跳参数的登录页（分享落地页要求登录）不被打断', async () => {
    onAppLaunch({ path: 'pages/login/Login', query: { redirect: '/pages/menu/Detail?id=3' } })
    trackLaunchAuthDone()
    expect(await resumeIfLoggedIn('/pages/menu/Detail?id=3')).toBeNull()
    expect(__testUni.mocks.switchTab).not.toHaveBeenCalled()
  })

  it('冷启动落在首页时，之后手动打开登录页不会被弹回', async () => {
    onAppLaunch({ path: 'pages/dish/List', query: {} })
    trackLaunchAuthDone()
    expect(await resumeIfLoggedIn('')).toBeNull()
  })

  it('launchTarget 不把登录页当成落地目标', () => {
    expect(launchTarget({ path: 'pages/login/Login', query: {} })).toBeNull()
    expect(launchTarget({ path: 'pages/dish/List', query: { scene: 'x' } })).not.toContain('login')
  })
})

describe('静态保护：启动配置与首页请求', () => {
  it('pages.json：第一页与 tabBar 第一项都是首页菜谱，登录页不是第一页', () => {
    for (const p of ['H5', 'MP-WEIXIN'] as const) {
      const j = loadPagesJson(p)
      expect(j.pages[0].path, p).toBe('pages/dish/List')
      expect(j.tabBar.list[0].pagePath, p).toBe('pages/dish/List')
    }
  })

  it('App.vue 与首页不直接写 reLaunch / 登录页跳转', () => {
    for (const f of ['App.vue', 'utils/launch.ts', 'pages/dish/List.vue']) {
      expect(src(f), f).not.toMatch(/reLaunch\(\{\s*url:\s*['"`]\/pages\/login/)
      expect(src(f), f).not.toMatch(/redirectToLogin\(/)
    }
  })

  it('首页 onLoad 的请求都是游客可读接口（字典 / 菜谱搜索），且不用「需要登录」类请求', () => {
    const list = src('pages/dish/List.vue')
    const onLoad = list.slice(list.indexOf('onLoad(async'), list.indexOf('// 首推'))
    expect(onLoad).toContain('loadDicts()')
    expect(onLoad).toContain('refreshLogin')
    // 登录后才请求家庭成员
    expect(onLoad.indexOf('if (!loggedIn.value) return')).toBeLessThan(onLoad.indexOf('listMembers()'))
  })
})

function trackLaunchAuthDone() {
  trackLaunchAuth(Promise.resolve())
}
