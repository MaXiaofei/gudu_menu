import { describe, it, expect } from 'vitest'
import { initialOf, thumbOf } from './image'

describe('thumbOf', () => {
  it('/original/ → /thumbnail/，其余原样', () => {
    expect(thumbOf('/gudu/uploads/original/a.jpg')).toBe('/gudu/uploads/thumbnail/a.jpg')
    expect(thumbOf('https://staging.imxf.cloud/images/dish/xc_1.jpg')).toBe('https://staging.imxf.cloud/images/dish/xc_1.jpg')
  })
  it('空值 → 空串', () => {
    expect(thumbOf(null)).toBe('')
    expect(thumbOf('  ')).toBe('')
  })
})

describe('initialOf', () => {
  it('取首字（含 emoji/多字节安全），空名回退', () => {
    expect(initialOf('滑蛋虾仁')).toBe('滑')
    expect(initialOf('  ')).toBe('菜')
    expect(initialOf(undefined, '材')).toBe('材')
  })
})
