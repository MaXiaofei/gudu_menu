import { describe, it, expect, vi, beforeEach } from 'vitest'
import { __testUni } from '../setup'
import { installHttpMock } from '../helpers/http'
import { BASE, ApiError } from '@/utils/request'
import {
  getSensitiveConsent,
  grantSensitiveConsent,
  withdrawSensitiveConsent,
  exportMyData,
  createExportLink,
  copyExportLink,
  SENSITIVE_DOC_VERSION,
} from '@/api/privacy'
import { saveMemberProfile, ConsentDeclinedError } from '@/api/member'
import {
  requiredConsents,
  isChild,
  ensureSensitiveConsent,
  consentDialog,
  withdrawDialog,
} from '@/utils/sensitiveConsent'

const status = (health: boolean, child: boolean) => ({
  items: { sensitive_health: { granted: health, version: null, at: null }, child_info: { granted: child } },
})

function modalAnswers(...answers: boolean[]) {
  const calls: any[] = []
  ;(globalThis as any).uni.showModal = vi.fn((o: any) => {
    calls.push(o)
    o.success?.({ confirm: answers.shift() ?? false })
  })
  return calls
}

describe('utils/sensitiveConsent 判定（与后端 SensitiveInfoClassifier 一致）', () => {
  it('无健康信息不需要；健康字段 / 目标 / 活动水平需要健康同意', () => {
    expect(requiredConsents({})).toEqual([])
    expect(requiredConsents({ healthProfile: { allergies: [], note: ' ', constraints: {} } })).toEqual([])
    expect(requiredConsents({ healthProfile: { height: 170 } })).toEqual(['sensitive_health'])
    expect(requiredConsents({ goal: 'LOSE' })).toEqual(['sensitive_health'])
    expect(requiredConsents({ activityLevel: 'ACTIVE' })).toEqual(['sensitive_health'])
  })

  it('不满 14 周岁：年龄 / 月龄 / 宝宝辅食 → 另需监护人同意', () => {
    expect(requiredConsents({ healthProfile: { age: 13 } })).toEqual(['sensitive_health', 'child_info'])
    expect(isChild({ ageMonth: 12 })).toBe(true)
    expect(isChild({ audiences: ['宝宝辅食'] })).toBe(true)
    expect(isChild({ age: 14 })).toBe(false)
    expect(isChild({ age: 0 })).toBe(false)
    expect(isChild({ ageMonth: 168 })).toBe(false)
    expect(isChild(null)).toBe(false)
  })

  it('弹窗文案：说明用途、可撤回；儿童场景明确监护人', () => {
    const h = consentDialog(['sensitive_health'])
    expect(h.content).toContain('营养计算')
    expect(h.content).toContain('撤回')
    const c = consentDialog(['child_info'])
    expect(c.title).toContain('监护人')
    expect(c.content).toContain('监护人')
    expect(c.content).toContain('撤回')
    expect(withdrawDialog('child_info').content).toContain('删除')
  })
})

describe('ensureSensitiveConsent / saveMemberProfile（B.2 先同意后保存）', () => {
  beforeEach(() => {
    __testUni.resetStorage()
  })

  it('无敏感信息：不查状态、不弹窗，直接保存', async () => {
    const http = installHttpMock({ 'POST /member': 31 })
    const calls = modalAnswers()
    expect(await saveMemberProfile({ name: '奶奶' })).toBe(31)
    expect(calls).toHaveLength(0)
    expect(http.calls.map((c) => c.url)).toEqual(['/member'])
  })

  it('首次填健康档案：弹单独同意 → 记录同意 → 再保存', async () => {
    const http = installHttpMock({
      'GET /consent/sensitive': status(false, false),
      'POST /consent/sensitive': status(true, false),
      'PUT /member': null,
    })
    const calls = modalAnswers(true)
    await saveMemberProfile({ id: 5, healthProfile: { height: 170 } })
    expect(calls).toHaveLength(1)
    expect(calls[0].title).toContain('健康')
    expect(http.calls.map((c) => `${c.method} ${c.url}`)).toEqual([
      'GET /consent/sensitive',
      'POST /consent/sensitive',
      'PUT /member',
    ])
    expect(http.calls[1].data).toEqual({
      docTypes: ['sensitive_health'],
      docVersion: SENSITIVE_DOC_VERSION,
      channel: 'mp-weixin',
    })
  })

  it('不同意：不记录、不保存，抛 ConsentDeclinedError', async () => {
    const http = installHttpMock({ 'GET /consent/sensitive': status(false, false) })
    modalAnswers(false)
    await expect(saveMemberProfile({ id: 5, goal: 'LOSE' })).rejects.toBeInstanceOf(ConsentDeclinedError)
    expect(http.callsOf('/member')).toHaveLength(0)
    expect(http.callsOf('/consent/sensitive').filter((c) => c.method === 'POST')).toHaveLength(0)
  })

  it('已同意健康档案、新录宝宝：只弹监护人同意，一并记录两项', async () => {
    const http = installHttpMock({
      'GET /consent/sensitive': status(true, false),
      'POST /consent/sensitive': status(true, true),
      'POST /member': 40,
    })
    const calls = modalAnswers(true)
    const id = await saveMemberProfile({ name: '宝宝', healthProfile: { ageMonth: 10, audiences: ['宝宝辅食'] } })
    expect(id).toBe(40)
    expect(calls[0].title).toContain('监护人')
    expect(http.calls[1].data.docTypes.sort()).toEqual(['child_info', 'sensitive_health'])
  })

  it('已全部同意：不弹窗', async () => {
    installHttpMock({ 'GET /consent/sensitive': status(true, true), 'PUT /member': null })
    const calls = modalAnswers()
    expect(await ensureSensitiveConsent({ healthProfile: { age: 3 } })).toBe(true)
    expect(calls).toHaveLength(0)
  })

  it('后端兜底 428：按返回 docTypes 再弹窗并重试一次', async () => {
    let memberCalls = 0
    const reqs: string[] = []
    ;(globalThis as any).uni.request = async (opt: any) => {
      const url = opt.url.replace(/^https?:\/\/[^/]+\/gudu/, '')
      reqs.push(`${opt.method} ${url}`)
      if (url === '/consent/sensitive' && opt.method === 'GET') return { statusCode: 200, data: { code: 0, data: status(true, false) } }
      if (url === '/consent/sensitive') return { statusCode: 200, data: { code: 0, data: status(true, false) } }
      memberCalls++
      return memberCalls === 1
        ? { statusCode: 200, data: { code: 428, msg: '需要同意', data: { reason: 'CONSENT_REQUIRED', docTypes: ['sensitive_health'] } } }
        : { statusCode: 200, data: { code: 0, data: null } }
    }
    const calls = modalAnswers(true)
    vi.mocked(__testUni.mocks.showToast).mockClear()
    await saveMemberProfile({ id: 9, healthProfile: { weight: 60 } })
    expect(memberCalls).toBe(2)
    expect(calls).toHaveLength(1)
    expect(reqs).toContain('POST /consent/sensitive')
    expect(__testUni.mocks.showToast).not.toHaveBeenCalled() // 428 不 toast
  })

  it('428 以 ApiError 抛出，带 code 与 data', async () => {
    ;(globalThis as any).uni.request = async () => ({
      statusCode: 200,
      data: { code: 428, msg: 'x', data: { docTypes: ['child_info'] } },
    })
    const err = await getSensitiveConsent().catch((e) => e)
    expect(err).toBeInstanceOf(ApiError)
    expect(err.code).toBe(428)
    expect(err.data.docTypes).toEqual(['child_info'])
  })
})

describe('api/privacy 同意与撤回', () => {
  it('状态归一化：缺项按未同意', async () => {
    installHttpMock({ 'GET /consent/sensitive': { items: { sensitive_health: { granted: true, at: '2026-10-26T09:00:00' } } } })
    const s = await getSensitiveConsent()
    expect(s.sensitive_health.granted).toBe(true)
    expect(s.child_info.granted).toBe(false)
  })

  it('同意去重；撤回传 docType', async () => {
    const http = installHttpMock({
      'POST /consent/sensitive': status(true, true),
      'POST /consent/sensitive/withdraw': status(false, true),
    })
    await grantSensitiveConsent(['child_info', 'child_info'])
    expect(http.calls[0].data.docTypes).toEqual(['child_info'])
    const s = await withdrawSensitiveConsent('sensitive_health')
    expect(http.calls[1].data).toEqual({ docType: 'sensitive_health', channel: 'mp-weixin' })
    expect(s.sensitive_health.granted).toBe(false)
  })
})

describe('api/privacy 导出我的数据（C.2）', () => {
  it('GET /account/me/export 透传', async () => {
    installHttpMock({ 'GET /account/me/export': { formatVersion: 1, accountId: 7, account: { id: 7 }, families: [] } })
    const d = await exportMyData()
    expect(d.accountId).toBe(7)
    expect(d.families).toEqual([])
  })

  it('临时下载链接：BASE + 相对路径，复制到剪贴板', async () => {
    installHttpMock({
      'POST /account/me/export/link': { path: '/account/export/download?token=abc', expiresAt: '2026-10-26T09:30:00' },
    })
    const link = await createExportLink()
    expect(link.url).toBe(BASE + '/account/export/download?token=abc')
    expect(link.expiresAt).toBe('2026-10-26T09:30:00')
    let copied = ''
    ;(globalThis as any).uni.setClipboardData = vi.fn((o: any) => {
      copied = o.data
      o.success?.()
    })
    await copyExportLink(link)
    expect(copied).toBe(link.url)
  })

  it('超过每天 3 次：后端 429 → 抛错并提示太频繁', async () => {
    ;(globalThis as any).uni.request = async () => ({ statusCode: 200, data: { code: 429, msg: '每天最多导出 3 次' } })
    await expect(createExportLink()).rejects.toThrow('每天最多导出 3 次')
  })
})
