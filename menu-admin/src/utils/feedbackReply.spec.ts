import { describe, it, expect } from 'vitest'
import {
  feedbackStatusLabel, feedbackStatusTone, feedbackTypeLabel, feedbackTypeTone, sourceRefLabel, validateReply,
} from './feedback'

describe('A.11 反馈类型 / 状态 / 来源 / 回复校验', () => {
  it('类型标签与色：缺省按一般反馈', () => {
    expect(feedbackTypeLabel('ai')).toBe('AI 内容')
    expect(feedbackTypeLabel('complaint')).toBe('投诉举报')
    expect(feedbackTypeLabel(null)).toBe('一般反馈')
    expect(feedbackTypeLabel('weird')).toBe('一般反馈')
    expect(feedbackTypeTone('ai')).toBe('info')
    expect(feedbackTypeTone('complaint')).toBe('err')
    expect(feedbackTypeTone(undefined)).toBe('mute')
  })

  it('状态标签与色：NEW 待处理 / REPLIED 已回复 / DONE 已处理', () => {
    expect(feedbackStatusLabel('NEW')).toBe('待处理')
    expect(feedbackStatusLabel('REPLIED')).toBe('已回复')
    expect(feedbackStatusLabel('DONE')).toBe('已处理')
    expect(feedbackStatusTone('REPLIED')).toBe('info')
    expect(feedbackStatusTone('DONE')).toBe('ok')
    expect(feedbackStatusTone('NEW')).toBe('warn')
  })

  it('来源引用：AI 来源翻译成功能名，其他原样', () => {
    expect(sourceRefLabel('agent:m123')).toBe('咕嘟助手 · m123')
    expect(sourceRefLabel('weekplan')).toBe('一周餐程')
    expect(sourceRefLabel('scan:')).toBe('拍照识别')
    expect(sourceRefLabel('page:/pages/dish/Detail')).toBe('page:/pages/dish/Detail')
    expect(sourceRefLabel(null)).toBe('')
  })

  it('回复校验：空与超长拒绝', () => {
    expect(validateReply('  ')).toBe('请填写回复内容')
    expect(validateReply('x'.repeat(1001))).toContain('1000')
    expect(validateReply('已修复')).toBe('')
  })
})
