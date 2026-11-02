import { describe, it, expect, beforeEach, vi } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { __testUni } from '../setup'
import { loadPagesJson } from '../helpers/pagesJson'
import {
  afterReloginFailed,
  classify401,
  guardNavigate,
  promptLogin,
  requireLogin,
  resetPromptState,
  routeNeedsLogin,
} from '@/utils/guest'
import { resolveAgentTabMode } from '@/utils/agentTab'
import { syncAgentTab } from '@/utils/theme'
import { featureStatus } from '@/utils/featureSwitch'
import { shouldPromptProfile, DEFAULT_NICKNAME } from '@/utils/profileSetup'

const src = (p: string) => readFileSync(resolve(__dirname, '../../src', p), 'utf8')

describe('游客路由守卫判定', () => {
  it('游客可直接看：首页、详情、推荐、食材库、协议、跟做模式、登录页', () => {
    for (const u of [
      '/pages/dish/List',
      '/pages/dish/Detail?id=3',
      '/pages/recommend/Index',
      '/pages/ingredient/List',
      '/pages/profile/Legal?type=privacy',
      '/pkg-ai/CookMode?id=2',
      '/pages/login/Login?redirect=x',
    ])
      expect(routeNeedsLogin(u), u).toBe(false)
  })

  it('Tab 页（食集 / 余量）不拦截，由页面渲染登录占位', () => {
    expect(routeNeedsLogin('/pages/pantry/List')).toBe(false)
    expect(routeNeedsLogin('/pages/menu/List')).toBe(false)
  })

  it('家庭 / 采购 / 食记 / 写菜谱 / 点评 / 成员 / 账号 → 需要登录', () => {
    for (const u of [
      '/pages/shopping/List',
      '/pages/foodlog/Index',
      '/pages/dish/Create',
      '/pages/review/Form?dishId=1',
      '/pages/member/Index',
      '/pkg-family/Index',
      '/pkg-account/Security',
      '/pages/profile/Settings',
      '/pkg-agent/Chat',
      '/pages/menu/Detail?id=1',
    ])
      expect(routeNeedsLogin(u), u).toBe(true)
  })
})

describe('登录拦截', () => {
  beforeEach(() => {
    __testUni.resetStorage()
    resetPromptState()
    vi.mocked(__testUni.mocks.showModal).mockReset()
    vi.mocked(__testUni.mocks.navigateTo).mockClear()
  })

  it('游客去需登录页：拦下并弹「登录后可以使用这个功能」，按钮文字 ≤4 字', () => {
    expect(guardNavigate('/pages/shopping/List')).toBe(false)
    const opt: any = vi.mocked(__testUni.mocks.showModal).mock.calls[0][0]
    expect(opt.content).toBe('登录后可以使用这个功能')
    expect(opt.confirmText).toBe('去登录')
    expect([...opt.cancelText].length).toBeLessThanOrEqual(4)
  })

  it('点「去登录」才进登录页并带回跳目标；点取消不跳', async () => {
    vi.mocked(__testUni.mocks.showModal).mockImplementation(((o: any) => o.success({ confirm: true })) as any)
    guardNavigate('/pages/shopping/List')
    await Promise.resolve()
    expect(__testUni.mocks.navigateTo).toHaveBeenCalledWith({
      url: '/pages/login/Login?redirect=' + encodeURIComponent('/pages/shopping/List'),
    })
    vi.mocked(__testUni.mocks.navigateTo).mockClear()
    vi.mocked(__testUni.mocks.showModal).mockImplementation(((o: any) => o.success({ confirm: false })) as any)
    await promptLogin({ target: null })
    expect(__testUni.mocks.navigateTo).not.toHaveBeenCalled()
  })

  it('已登录放行；游客访问公开页放行；requireLogin 游客返回 false 且弹提示', () => {
    expect(guardNavigate('/pages/dish/Detail?id=1')).toBe(true)
    expect(requireLogin(null)).toBe(false)
    expect(__testUni.mocks.showModal).toHaveBeenCalledTimes(1)
    resetPromptState()
    __testUni.setStorage('token', 't')
    expect(guardNavigate('/pages/shopping/List')).toBe(true)
    expect(requireLogin()).toBe(true)
  })

  it('同时只弹一个提示，避免并发请求连弹', () => {
    guardNavigate('/pages/shopping/List')
    guardNavigate('/pages/foodlog/Index')
    expect(__testUni.mocks.showModal).toHaveBeenCalledTimes(1)
  })
})

describe('401 分流', () => {
  it('游客：静默请求不打扰，动作类请求提示登录；有 token 先走静默重登', () => {
    expect(classify401({ hasToken: false, silent: true })).toBe('guest-silent')
    expect(classify401({ hasToken: false })).toBe('guest-prompt')
    expect(classify401({ hasToken: true })).toBe('relogin')
    expect(afterReloginFailed(true)).toBe('guest-silent')
    expect(afterReloginFailed(false)).toBe('guest-prompt')
  })
})

describe('第三个 Tab：咕嘟 / 推荐', () => {
  beforeEach(() => {
    __testUni.resetStorage()
    featureStatus.agentChat = true
    ;(globalThis as any).uni.setTabBarStyle = vi.fn()
    ;(globalThis as any).uni.setTabBarItem = vi.fn()
  })
  const agentCall = () =>
    vi.mocked((globalThis as any).uni.setTabBarItem).mock.calls.map((c: any[]) => c[0]).filter((o: any) => o.index === 2).at(-1)

  it('模式判定：游客与开关关闭 → 推荐；登录且开关开 → 咕嘟', () => {
    expect(resolveAgentTabMode(false, true)).toBe('recommend')
    expect(resolveAgentTabMode(true, false)).toBe('recommend')
    expect(resolveAgentTabMode(false, false)).toBe('recommend')
    expect(resolveAgentTabMode(true, true)).toBe('agent')
  })

  it('游客：tabBar 文字改「推荐」、换推荐图标', () => {
    expect(syncAgentTab()).toBe('recommend')
    const c = agentCall()
    expect(c.text).toBe('推荐')
    expect(c.iconPath).toContain('recommend-')
  })

  it('登录且开关开：恢复「咕嘟」；开关关闭：又是「推荐」；applyTabBar 不覆盖', () => {
    __testUni.setStorage('token', 't')
    expect(syncAgentTab()).toBe('agent')
    expect(agentCall().text).toBe('咕嘟')
    expect(agentCall().iconPath).toContain('agent-')
    featureStatus.agentChat = false
    expect(syncAgentTab()).toBe('recommend')
    expect(agentCall().text).toBe('推荐')
  })

  it('推荐图标文件存在', () => {
    for (const f of ['recommend-cream', 'recommend-cream-on', 'recommend-matcha', 'recommend-matcha-on'])
      expect(existsSync(resolve(__dirname, `../../src/static/tabbar/${f}.png`))).toBe(true)
  })
})

describe('资料完善不再自动弹', () => {
  it('提示条条件仍成立（默认昵称、已登录），但组件不再挂载时自动检查', () => {
    __testUni.resetStorage()
    __testUni.setStorage('token', 't')
    __testUni.setStorage('nickname', DEFAULT_NICKNAME)
    expect(shouldPromptProfile()).toBe(true)
    const c = src('components/ui-profile-setup.vue')
    expect(c).not.toMatch(/onMounted/)
    expect(c).toMatch(/defineExpose\(\{ open \}\)/)
  })

  it('没有任何页面直接挂载并自动弹出；「我的」页提供入口', () => {
    for (const p of ['pages/dish/List.vue', 'pages/dish/Detail.vue', 'pages/together/Pick.vue'])
      expect(src(p), p).not.toContain('<ui-profile-setup')
    expect(src('pages/profile/Profile.vue')).toContain('<ui-profile-setup ref="setupRef"')
  })
})

describe('源码守卫：冷启动不是登录页；登录流程不要求头像昵称', () => {
  it('pages.json 第一页不是登录页', () => {
    for (const plat of ['H5', 'MP-WEIXIN'] as const) {
      const first = loadPagesJson(plat).pages[0].path
      expect(first).not.toBe('pages/login/Login')
      expect(first).toBe('pages/dish/List')
    }
  })

  it('App.vue onLaunch 不再跳登录页', () => {
    const app = src('App.vue')
    expect(app).not.toMatch(/redirectToLogin|reLaunch/)
    expect(app).toContain('installRouteGuard')
  })

  it('登录页与登录后流程不调用头像昵称授权', () => {
    const login = src('pages/login/Login.vue')
    for (const bad of [/getUserProfile/, /getUserInfo/, /chooseAvatar/, /type="nickname"/, /open-type="getUserInfo"/])
      expect(login).not.toMatch(bad)
    const redirect = src('utils/loginRedirect.ts')
    expect(redirect).not.toMatch(/shouldPromptProfile|ui-profile-setup/)
  })

  it('咕嘟 Tab 页不再渲染「咕嘟助手暂停服务」提示页', () => {
    expect(src('pages/agent/Index.vue')).not.toContain('ui-agent-paused')
  })
})
