import { describe, it, expect } from 'vitest'
import { stockColor, stockLabel, prepColor, prepLabel, T } from '@/utils/token'

// utils/token：档位/备菜状态 → 色值与文案（两端一致的映射）。
describe('utils/token', () => {
  it('stockColor：ENOUGH 绿 / LOW 黄 / NONE（及未知）红', () => {
    expect(stockColor('ENOUGH')).toBe(T.success)
    expect(stockColor('LOW')).toBe(T.warning)
    expect(stockColor('NONE')).toBe(T.error)
    expect(stockColor(null)).toBe(T.error)
  })

  it('stockLabel：充足/不足/用完（兜底用完）', () => {
    expect(stockLabel('ENOUGH')).toBe('充足')
    expect(stockLabel('LOW')).toBe('不足')
    expect(stockLabel('NONE')).toBe('用完')
    expect(stockLabel(undefined)).toBe('用完')
  })

  it('prepColor/prepLabel：4 档备菜状态', () => {
    expect(prepColor('READY')).toBe(T.success)
    expect(prepColor('THAWING')).toBe(T.info)
    expect(prepColor('MARINATING')).toBe(T.warning)
    expect(prepColor('PENDING')).toBe(T.caption)
    expect(prepLabel('READY')).toBe('✓ 已备')
    expect(prepLabel('THAWING')).toBe('化冻中')
    expect(prepLabel('MARINATING')).toBe('腌制中')
    expect(prepLabel(null)).toBe('待备')
  })
})
