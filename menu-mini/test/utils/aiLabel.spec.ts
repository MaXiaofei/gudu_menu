import { describe, it, expect } from 'vitest'
import { AI_DISCLAIMER, AI_DISCLAIMER_TAIL, AI_ASSISTANT_INTRO, aiFeedbackUrl, aiSourceTag } from '@/utils/aiLabel'

describe('utils/aiLabel', () => {
  it('免责提示文案与隐私政策一致', () => {
    expect(AI_DISCLAIMER).toBe('AI 生成，仅供参考，不能替代医生或营养师建议')
    expect(AI_DISCLAIMER_TAIL).toBe('仅供参考，不能替代医生或营养师建议')
    expect(AI_ASSISTANT_INTRO).toContain('我是 AI 助手')
  })
  it('反馈地址：来源 + 可选 ref（编码）', () => {
    expect(aiFeedbackUrl('agent')).toBe('/pages/profile/Feedback?type=ai&source=agent')
    expect(aiFeedbackUrl('weekplan', 'a b')).toBe('/pages/profile/Feedback?type=ai&source=weekplan&ref=a%20b')
    expect(aiFeedbackUrl('scan', '')).toBe('/pages/profile/Feedback?type=ai&source=scan')
  })
  it('clientInfo 来源标记', () => {
    expect(aiSourceTag('nutrition')).toBe('来源:AI/营养报告')
    expect(aiSourceTag('agent', 'm1')).toBe('来源:AI/咕嘟助手#m1')
    expect(aiSourceTag('')).toBe('')
  })
})
