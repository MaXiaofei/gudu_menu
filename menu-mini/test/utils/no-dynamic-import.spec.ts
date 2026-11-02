import { describe, it, expect, vi, beforeEach } from 'vitest'
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { __testUni } from '../setup'
import { bridgedRequest } from '@/utils/requestBridge'
import { loadFeatureSwitches, featureState } from '@/utils/featureSwitch'
import { reportConsent, reportConsentIfPending, setConsent, clearConsent, REPORTED_KEY, LEGAL_VERSION } from '@/utils/consent'
import { ensureLogin } from '@/utils/loginRedirect'
import '@/utils/request' // 触发 bindRequest 注册
import '@/utils/guest' // 触发 bindPromptLogin 注册

function walk(dir: string, out: string[] = []): string[] {
  for (const n of readdirSync(dir)) {
    const p = join(dir, n)
    if (statSync(p).isDirectory()) walk(p, out)
    else if (/\.(ts|vue)$/.test(n)) out.push(p)
  }
  return out
}

// 事故：uni-app 编译微信小程序时把 `await import('./x')` 变成字符串 "./x.js"，解构出 undefined，
// 开关拉取 / 同意上报 / 游客登录引导在真机上全部失效（单测环境动态导入正常，测不出来）。
describe('守护：小程序源码禁止动态 import()', () => {
  it('src 下 .ts/.vue 不出现 import(（类型位置的 import("x").T 除外）', () => {
    const bad: string[] = []
    for (const f of walk(join(__dirname, '../../src'))) {
      readFileSync(f, 'utf8').split('\n').forEach((line, i) => {
        const code = line.replace(/\/\/.*$/, '').replace(/^\s*\*.*$/, '')
        // 排除类型位置：`typeof import('x')`、`import('x').Type`、`: import('x')`
        const m = code.match(/(?<![\w.])import\s*\(([^)]*)\)(\s*\.\w+)?/)
        if (!m) return
        if (/typeof\s+import\s*\(/.test(code) || m[2] || /:\s*import\s*\(/.test(code)) return
        bad.push(`${f}:${i + 1}: ${line.trim()}`)
      })
    }
    expect(bad).toEqual([])
  })
})

describe('注入后的请求路径（动态导入替代方案）', () => {
  beforeEach(() => {
    __testUni.resetStorage()
    vi.mocked(__testUni.mocks.request).mockReset()
  })

  it('bridgedRequest 经 request.ts 注册的真实 request 发出请求', async () => {
    vi.mocked(__testUni.mocks.request).mockResolvedValueOnce({ statusCode: 200, data: { code: 0, data: { ok: 1 } } } as any)
    expect(await bridgedRequest({ url: '/x', method: 'GET', silent: true })).toEqual({ ok: 1 })
  })

  it('loadFeatureSwitches：请求 /feature-switches 并写入状态', async () => {
    vi.mocked(__testUni.mocks.request).mockResolvedValueOnce({
      statusCode: 200,
      data: { code: 0, data: { agentChat: true, cookAsk: false, vision: true, mealPlan: true } },
    } as any)
    await loadFeatureSwitches()
    expect((vi.mocked(__testUni.mocks.request).mock.calls[0][0] as any).url).toContain('/feature-switches')
    expect(featureState.known).toBe(true)
  })

  it('reportConsent：成功落「已上报」标记；失败不落标记', async () => {
    setConsent()
    vi.mocked(__testUni.mocks.request).mockResolvedValueOnce({ statusCode: 200, data: { code: 0, data: null } } as any)
    expect(await reportConsent()).toBe(true)
    expect(uni.getStorageSync(REPORTED_KEY)).toBe(LEGAL_VERSION)
    clearConsent()
    setConsent()
    vi.mocked(__testUni.mocks.request).mockRejectedValueOnce(new Error('net'))
    expect(await reportConsent()).toBe(false)
    expect(uni.getStorageSync(REPORTED_KEY)).toBe('')
  })

  it('reportConsentIfPending：已同意+已登录+未上报才补报，成功后不再重复', async () => {
    const req = vi.mocked(__testUni.mocks.request)
    expect(await reportConsentIfPending()).toBe(false) // 未同意
    setConsent()
    expect(await reportConsentIfPending()).toBe(false) // 未登录
    __testUni.setStorage('token', 't')
    req.mockResolvedValue({ statusCode: 200, data: { code: 0, data: null } } as any)
    expect(await reportConsentIfPending()).toBe(true)
    expect(req).toHaveBeenCalledTimes(1)
    expect(await reportConsentIfPending()).toBe(false) // 已上报
    expect(req).toHaveBeenCalledTimes(1)
  })

  it('ensureLogin 游客：经注入的 promptLogin 弹「去登录」提示', async () => {
    vi.mocked(__testUni.mocks.showModal).mockClear()
    vi.mocked(__testUni.mocks.showModal).mockImplementation(((o: any) => o.success({ confirm: false })) as any)
    expect(await ensureLogin('/pages/together/Pick?token=t')).toBe(false)
    expect(__testUni.mocks.showModal).toHaveBeenCalled()
    vi.mocked(__testUni.mocks.showModal).mockReset()
  })
})
