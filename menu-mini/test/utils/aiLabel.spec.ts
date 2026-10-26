import { describe, it, expect } from 'vitest'
import {
  AI_DISCLAIMER, AI_DISCLAIMER_TAIL, AI_ASSISTANT_INTRO, HEALTH_DISCLAIMER, AI_SOURCE_LABEL, aiFeedbackUrl, aiSourceRef,
} from '@/utils/aiLabel'

describe('utils/aiLabel', () => {
  it('免责提示文案与隐私政策一致', () => {
    expect(AI_DISCLAIMER).toBe('AI 生成，仅供参考，不能替代医生或营养师建议')
    expect(AI_DISCLAIMER_TAIL).toBe('仅供参考，不能替代医生或营养师建议')
    expect(AI_ASSISTANT_INTRO).toContain('我是 AI 助手')
  })
  it('健康类免责提示不含「AI 生成」；营养报告 / 智荐不在 AI 来源里（非模型生成）', () => {
    expect(HEALTH_DISCLAIMER).toBe('仅供参考，不能替代医生或营养师建议')
    expect(HEALTH_DISCLAIMER).not.toContain('AI')
    expect(Object.keys(AI_SOURCE_LABEL)).toEqual(['agent', 'scan', 'weekplan'])
  })
  it('反馈地址：来源 + 可选 ref（编码）', () => {
    expect(aiFeedbackUrl('agent')).toBe('/pages/profile/Feedback?type=ai&source=agent')
    expect(aiFeedbackUrl('weekplan', 'a b')).toBe('/pages/profile/Feedback?type=ai&source=weekplan&ref=a%20b')
    expect(aiFeedbackUrl('scan', '')).toBe('/pages/profile/Feedback?type=ai&source=scan')
  })
  it('来源引用 sourceRef（A.11，不再塞进 clientInfo）：只认已知来源', () => {
    expect(aiSourceRef('scan')).toBe('scan')
    expect(aiSourceRef('agent', ' m1 ')).toBe('agent:m1')
    expect(aiSourceRef('')).toBe('')
    expect(aiSourceRef('evil', 'x')).toBe('')
    expect(aiSourceRef('agent', 'x'.repeat(200))).toHaveLength(128)
  })
})
