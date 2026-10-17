import { describe, it, expect } from 'vitest'
import { feedbackTitle, platformOf, relativeTime } from './feedback'

describe('platformOf', () => {
  it.each([
    ['flutter/ios v1.0.0', 'App'],
    ['v1.0.0 / mp-weixin / iPhone 15 / iOS 18.2 / wx8.0.50', '小程序'],
    ['v1.0.0 / web / Chrome', 'H5'],
    ['', '未知'],
    [null, '未知'],
    ['something else', '未知'],
  ])('%s → %s', (info, expected) => {
    expect(platformOf(info as string | null)).toBe(expected)
  })
})

describe('feedbackTitle', () => {
  it('取首个非空行并截断', () => {
    expect(feedbackTitle('\n采购清单没反应\n第二行')).toBe('采购清单没反应')
    expect(feedbackTitle('一'.repeat(40), 10)).toBe(`${'一'.repeat(10)}…`)
    expect(feedbackTitle(null)).toBe('')
  })
})

describe('relativeTime', () => {
  const now = new Date('2026-10-16T22:40:00')
  it('分钟 / 小时 / 昨天 / 更早', () => {
    expect(relativeTime('2026-10-16T22:30:00', now)).toBe('10 分钟前')
    expect(relativeTime('2026-10-16T19:00:00', now)).toBe('3 小时前')
    expect(relativeTime('2026-10-15T08:00:00', now)).toBe('昨天')
    expect(relativeTime('2026-10-01 08:05:00', now)).toBe('10-01 08:05')
  })
  it('空值 → -；无法解析原样返回', () => {
    expect(relativeTime(null, now)).toBe('-')
    expect(relativeTime('bad', now)).toBe('bad')
  })
})
