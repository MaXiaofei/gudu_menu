import { describe, it, expect, beforeEach, vi } from 'vitest'
import { __testUni } from '../setup'
import {
  anyAiEnabled,
  canSendFor,
  FEATURE_CACHE_KEY,
  featureState,
  featureStatus,
  loadFeatureSwitches,
  noteSuspendedCode,
  restoreFeatureSwitches,
  type FeatureName,
} from '@/utils/featureSwitch'
import { resolveAgentTabMode } from '@/utils/agentTab'
import { syncAgentTab } from '@/utils/theme'
import { legalMarkdown } from '@/utils/legal'
import { reportedDocVersion, LEGAL_VERSION } from '@/utils/consent'
import { TERMS_NOAI_MD } from '@/static/legal/terms-noai'
import { request } from '@/utils/request'

// 四类 AI（咕嘟助手 / 问咕嘟 / 拍照识别 / 一周餐程）开关的 16 种组合在小程序端的行为。
const NAMES: FeatureName[] = ['agentChat', 'cookAsk', 'vision', 'mealPlan']
const CODES: Record<FeatureName, number> = { agentChat: 5032, cookAsk: 5033, vision: 5034, mealPlan: 5035 }
const combo = (mask: number) => Object.fromEntries(NAMES.map((n, i) => [n, (mask & (1 << i)) !== 0])) as Record<FeatureName, boolean>
const reply = (data: any, code = 0) =>
  vi.mocked(__testUni.mocks.request).mockResolvedValueOnce({ statusCode: 200, data: { code, msg: 'x', data } } as any)
const tabItem = () =>
  vi.mocked((globalThis as any).uni.setTabBarItem).mock.calls.map((c: any[]) => c[0]).filter((o: any) => o.index === 2).at(-1)

beforeEach(() => {
  __testUni.resetStorage()
  Object.assign(featureStatus, combo(15))
  featureState.known = true
  ;(globalThis as any).uni.setTabBarStyle = vi.fn()
  ;(globalThis as any).uni.setTabBarItem = vi.fn()
  vi.mocked(__testUni.mocks.request).mockReset()
  vi.mocked(__testUni.mocks.showToast).mockClear()
})

describe('标签模式：16 种组合 × 登录 / 游客', () => {
  for (let mask = 0; mask < 16; mask++) {
    const c = combo(mask)
    it(`mask=${mask} ${JSON.stringify(c)}`, () => {
      // 纯判定
      expect(resolveAgentTabMode(true, c.agentChat)).toBe(c.agentChat ? 'agent' : 'recommend')
      expect(resolveAgentTabMode(false, c.agentChat)).toBe('recommend')
      // 登录：只看咕嘟助手，其余三类绝不影响
      Object.assign(featureStatus, c)
      __testUni.setStorage('token', 't')
      expect(syncAgentTab()).toBe(c.agentChat ? 'agent' : 'recommend')
      expect(tabItem().text).toBe(c.agentChat ? '咕嘟' : '推荐')
      // 游客：恒为推荐
      __testUni.resetStorage()
      expect(syncAgentTab()).toBe('recommend')
      expect(tabItem().text).toBe('推荐')
    })
  }
})

describe('状态拉取：16 种组合逐项还原，互不影响', () => {
  for (let mask = 0; mask < 16; mask++) {
    it(`mask=${mask}`, async () => {
      const c = combo(mask)
      reply(c)
      await loadFeatureSwitches()
      expect({ ...featureStatus }).toEqual(c)
      expect(anyAiEnabled()).toBe(mask !== 0)
      // 对话上下文：咕嘟助手关停全拦；跟做模式还需问咕嘟开
      expect(canSendFor({ page: 'cook_mode' })).toBe(c.agentChat && c.cookAsk)
      expect(canSendFor({ page: 'agent' })).toBe(c.agentChat)
      expect(canSendFor(null)).toBe(c.agentChat)
      expect(__testUni.getStorage(FEATURE_CACHE_KEY)).toEqual(c)
    })
  }

  it('服务端字段缺失 / 返回 null：按开处理；只有明确 false 才算关', async () => {
    reply({ agentChat: false })
    await loadFeatureSwitches()
    expect({ ...featureStatus }).toEqual({ agentChat: false, cookAsk: true, vision: true, mealPlan: true })
    reply(null)
    await loadFeatureSwitches()
    expect({ ...featureStatus }).toEqual(combo(15))
    reply({ agentChat: 0, cookAsk: null, vision: '', mealPlan: undefined } as any)
    await loadFeatureSwitches()
    expect({ ...featureStatus }).toEqual(combo(15)) // 非 false 的假值不当成关（服务端只会给布尔）
  })
})

describe('收到 5032~5035：只切对应类别，不弹通用报错', () => {
  for (const n of NAMES) {
    it(`${CODES[n]} → ${n}`, async () => {
      reply(null, CODES[n])
      await expect(request({ url: '/x', method: 'POST' })).rejects.toThrow()
      for (const m of NAMES) expect(featureStatus[m]).toBe(m !== n)
      expect(__testUni.mocks.showToast).not.toHaveBeenCalled()
    })
  }

  it('四个码依次到达：全部关停；非暂停码不识别', () => {
    for (const n of NAMES) expect(noteSuspendedCode(CODES[n])).toBe(true)
    expect(anyAiEnabled()).toBe(false)
    for (const c of [0, 401, 429, 5031, 5036, '5032x', null, undefined]) expect(noteSuspendedCode(c as any)).toBe(c === '5032x' ? false : false)
  })

  it('重新拉取后可恢复（后台重新打开）', async () => {
    for (const n of NAMES) noteSuspendedCode(CODES[n])
    reply(combo(15))
    await loadFeatureSwitches()
    expect({ ...featureStatus }).toEqual(combo(15))
  })
})

describe('协议 / 成员档案无 AI 版：边界', () => {
  it('收到四个暂停码后切无 AI 版；任一类重新开放立即回完整版', async () => {
    for (const n of NAMES) noteSuspendedCode(CODES[n])
    expect(legalMarkdown('terms', anyAiEnabled())).toBe(TERMS_NOAI_MD)
    expect(reportedDocVersion()).toBe(LEGAL_VERSION + '-noai')
    reply({ agentChat: false, cookAsk: false, vision: true, mealPlan: false })
    await loadFeatureSwitches()
    expect(legalMarkdown('terms', anyAiEnabled())).not.toBe(TERMS_NOAI_MD)
    expect(reportedDocVersion()).toBe(LEGAL_VERSION)
  })

  it('冷启动恢复本地缓存（全关）：第一帧就是无 AI 版；拉取失败保持，不回完整版', async () => {
    featureState.known = false
    Object.assign(featureStatus, combo(15))
    __testUni.setStorage(FEATURE_CACHE_KEY, combo(0))
    restoreFeatureSwitches()
    expect(legalMarkdown('terms', anyAiEnabled())).toBe(TERMS_NOAI_MD)
    vi.mocked(__testUni.mocks.request).mockRejectedValueOnce(new Error('offline'))
    await loadFeatureSwitches()
    expect(anyAiEnabled()).toBe(false)
  })

  it('没有缓存且拉取失败：宁可多披露，显示完整版', async () => {
    featureState.known = false
    vi.mocked(__testUni.mocks.request).mockRejectedValueOnce(new Error('offline'))
    await loadFeatureSwitches()
    expect(legalMarkdown('terms', anyAiEnabled())).not.toBe(TERMS_NOAI_MD)
  })

  it('同意版本号上报：16 种组合里只有全关带 -noai 后缀', () => {
    for (let mask = 0; mask < 16; mask++) {
      Object.assign(featureStatus, combo(mask))
      expect(reportedDocVersion(), `mask=${mask}`).toBe(mask === 0 ? LEGAL_VERSION + '-noai' : LEGAL_VERSION)
    }
  })

  it('5036 / 5037（营养估算 / AI 帮写）不是小程序识别的暂停码：不改状态，走通用错误提示（后端文案可读）', () => {
    const before = { ...featureStatus }
    expect(noteSuspendedCode(5036)).toBe(false)
    expect(noteSuspendedCode(5037)).toBe(false)
    expect({ ...featureStatus }).toEqual(before)
  })
})
