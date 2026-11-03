import { describe, it, expect } from 'vitest'
import { offlineReasonError, statusText, REASON_MAX } from './dishLibrary'

describe('utils/dishLibrary', () => {
  it('下架原因：必填、去空格、不超过 200 字', () => {
    expect(offlineReasonError('')).toBe('请填写下架原因')
    expect(offlineReasonError('   ')).toBe('请填写下架原因')
    expect(offlineReasonError(null)).toBe('请填写下架原因')
    expect(offlineReasonError('图片侵权')).toBe('')
    expect(offlineReasonError('x'.repeat(REASON_MAX))).toBe('')
    expect(offlineReasonError('x'.repeat(REASON_MAX + 1))).toContain('200')
  })

  it('状态文案', () => {
    expect(statusText(true)).toBe('已下架')
    expect(statusText(false)).toBe('已上架')
    expect(statusText(undefined)).toBe('已上架')
  })
})
