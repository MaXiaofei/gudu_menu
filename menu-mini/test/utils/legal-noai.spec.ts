import { describe, it, expect, vi, beforeEach } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { __testUni } from '../setup'
import { legalMarkdown, consentHint } from '@/utils/legal'
import { anyAiEnabled, featureStatus, loadFeatureSwitches } from '@/utils/featureSwitch'
import { consentDialog } from '@/utils/sensitiveConsent'
import { reportedDocVersion, LEGAL_VERSION } from '@/utils/consent'
import { TERMS_NOAI_MD } from '@/static/legal/terms-noai'
import { PRIVACY_NOAI_MD } from '@/static/legal/privacy-noai'

const FORBIDDEN = ['AI', '人工智能', '大模型', '深度求索', 'DeepSeek', '智谱', '生成式', '咕嘟助手', '拍照识别', '一周餐程', '营养估算']
const setAll = (v: boolean) => {
  featureStatus.agentChat = featureStatus.cookAsk = featureStatus.vision = featureStatus.mealPlan = v
}

describe('协议无 AI 版', () => {
  beforeEach(() => {
    setAll(true)
    __testUni.resetStorage()
    vi.mocked(__testUni.mocks.request).mockReset()
  })

  it('四项全关 → 无 AI 版；任一项开 → 完整版', () => {
    setAll(false)
    expect(anyAiEnabled()).toBe(false)
    expect(legalMarkdown('terms', anyAiEnabled())).toBe(TERMS_NOAI_MD)
    expect(legalMarkdown('privacy', anyAiEnabled())).toBe(PRIVACY_NOAI_MD)
    for (const k of ['agentChat', 'cookAsk', 'vision', 'mealPlan'] as const) {
      setAll(false)
      featureStatus[k] = true
      expect(anyAiEnabled()).toBe(true)
      expect(legalMarkdown('terms', anyAiEnabled())).toContain('AI 生成内容')
      expect(legalMarkdown('privacy', anyAiEnabled())).toContain('AI 功能的特别说明')
    }
  })

  it('无 AI 版两份文本不含任何 AI 相关关键词，且章节编号连续', () => {
    for (const md of [TERMS_NOAI_MD, PRIVACY_NOAI_MD]) {
      for (const w of FORBIDDEN) expect(md, w).not.toContain(w)
      const heads = [...md.matchAll(/^## (.)、/gm)].map((m) => m[1])
      expect(heads.join('')).toBe('一二三四五六七八九'.slice(0, heads.length))
    }
  })

  it('无 AI 版与 docs/legal 源文件一致', () => {
    const root = resolve(__dirname, '../../..')
    expect(TERMS_NOAI_MD).toBe(readFileSync(resolve(root, 'docs/legal/terms-noai.md'), 'utf8'))
    expect(PRIVACY_NOAI_MD).toBe(readFileSync(resolve(root, 'docs/legal/privacy-noai.md'), 'utf8'))
  })

  it('成员档案说明与敏感授权弹窗：全关时不提 AI 回答', () => {
    expect(consentHint(true)).toContain('AI 回答')
    expect(consentHint(false)).not.toContain('AI')
    expect(consentDialog(['sensitive_health'], true).content).toContain('AI 回答')
    expect(consentDialog(['sensitive_health'], false).content).not.toContain('AI')
  })

  it('游客（无 token）拉取到全关后切无 AI 版；拉取失败保持完整版', async () => {
    vi.mocked(__testUni.mocks.request).mockResolvedValueOnce({
      statusCode: 200,
      data: { code: 0, msg: 'ok', data: { agentChat: false, cookAsk: false, vision: false, mealPlan: false } },
    } as any)
    await loadFeatureSwitches()
    expect(__testUni.mocks.request).toHaveBeenCalledTimes(1)
    expect(legalMarkdown('terms', anyAiEnabled())).toBe(TERMS_NOAI_MD)
    expect(reportedDocVersion()).toBe(LEGAL_VERSION + '-noai')

    setAll(true)
    vi.mocked(__testUni.mocks.request).mockRejectedValueOnce(new Error('network'))
    await loadFeatureSwitches()
    expect(anyAiEnabled()).toBe(true)
    expect(reportedDocVersion()).toBe(LEGAL_VERSION)
  })
})
