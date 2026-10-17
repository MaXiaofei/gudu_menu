import { describe, it, expect } from 'vitest'
import { highlightSegments, scoreTone, validateModeration } from './moderation'

describe('审核表单校验', () => {
  it('动作与原因必填', () => {
    const e = validateModeration({ action: '', reason: '', note: '' })
    expect(e.action).toBeTruthy()
    expect(e.reason).toBeTruthy()
  })
  it('隐藏 + 原因即可提交', () => {
    expect(validateModeration({ action: 'HIDE', reason: '广告营销 / 导流', note: '' })).toEqual({})
  })
  it('封禁或原因为「其他」必须写说明', () => {
    expect(validateModeration({ action: 'BAN_7D', reason: '广告营销 / 导流', note: ' ' }).note).toContain('封禁')
    expect(validateModeration({ action: 'HIDE', reason: '其他', note: '' }).note).toContain('其他')
    expect(validateModeration({ action: 'BAN_7D', reason: '广告营销 / 导流', note: '多次站外导流' })).toEqual({})
  })
  it('说明超长 / 误判只能配通过', () => {
    expect(validateModeration({ action: 'HIDE', reason: '敏感词', note: 'x'.repeat(201) }).note).toContain('200')
    expect(validateModeration({ action: 'HIDE', reason: '误判，内容合规', note: '' }).reason).toContain('通过')
    expect(validateModeration({ action: 'APPROVE', reason: '误判，内容合规', note: '' })).toEqual({})
  })
})

describe('正文高亮', () => {
  it('标出联系方式与促销用语，其余原样', () => {
    const segs = highlightSegments('用了我们家的秘制酱料包，加 V：gudu****88 领试吃装，满 3 包包邮。')
    expect(segs.filter((s) => s.kind === 'contact').map((s) => s.text.replace(/\s/g, ''))).toEqual(['加V：gudu****88'])
    expect(segs.filter((s) => s.kind === 'promo').map((s) => s.text)).toEqual(['领试吃装', '满 3 包包邮'])
    expect(segs.map((s) => s.text).join('')).toBe('用了我们家的秘制酱料包，加 V：gudu****88 领试吃装，满 3 包包邮。')
  })
  it('没有命中 → 一整段', () => {
    expect(highlightSegments('第一次做清蒸鲈鱼就成功了')).toEqual([{ text: '第一次做清蒸鲈鱼就成功了', kind: null }])
    expect(highlightSegments('')).toEqual([])
  })
})

describe('分数档位', () => {
  it('比例与数量', () => {
    expect(scoreTone(0.94)).toBe('err')
    expect(scoreTone(0.4)).toBe('warn')
    expect(scoreTone(0.02)).toBe('ok')
    expect(scoreTone(1, '处')).toBe('err')
    expect(scoreTone(0, '处')).toBe('ok')
  })
})
