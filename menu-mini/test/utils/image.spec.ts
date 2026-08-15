import { describe, it, expect } from 'vitest'
import { toAbsolute, toThumbnail, thumbOf, thumbList, originalList } from '@/utils/image'

// utils/image：相对→绝对（同 BASE）、/original/ → /thumbnail/、逗号多图拆分。
describe('utils/image', () => {
  it('toAbsolute：相对路径拼 BASE，http(s) 原样', () => {
    expect(toAbsolute('/uploads/original/1.jpg')).toContain('/gudu/uploads/original/1.jpg')
    expect(toAbsolute('https://a.com/x.jpg')).toBe('https://a.com/x.jpg')
    expect(toAbsolute('')).toBe('')
    expect(toAbsolute(null)).toBe('')
  })

  it('toThumbnail：/original/ → /thumbnail/', () => {
    expect(toThumbnail('https://h.com/gudu/uploads/original/1.jpg')).toBe(
      'https://h.com/gudu/uploads/thumbnail/1.jpg',
    )
  })

  it('thumbOf：组合相对路径直出缩略图绝对地址', () => {
    expect(thumbOf('/uploads/original/a.jpg')).toContain('/uploads/thumbnail/a.jpg')
  })

  it('thumbList / originalList：逗号分隔多图拆分 + 过滤空', () => {
    const urls = thumbList('/uploads/original/a.jpg,/uploads/original/b.jpg,')
    expect(urls).toHaveLength(2)
    expect(urls[0]).toContain('/thumbnail/a.jpg')
    expect(originalList('/uploads/original/a.jpg')).toHaveLength(1)
    expect(thumbList(null)).toEqual([])
  })
})
