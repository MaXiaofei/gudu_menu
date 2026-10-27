import { describe, it, expect } from 'vitest'
import { toAbsolute, toThumbnail, thumbOf, thumbList, originalList, uploadUrl } from '@/utils/image'

// utils/image：相对→绝对（同 BASE）、/original/ → /thumbnail/、逗号多图拆分。
describe('utils/image', () => {
  it('toAbsolute：相对路径拼 BASE，http(s) 原样', () => {
    expect(toAbsolute('/uploads/original/1.jpg')).toContain('/gudu/uploads/original/1.jpg')
    expect(toAbsolute('https://a.com/x.jpg')).toBe('https://a.com/x.jpg')
    expect(toAbsolute('')).toBe('')
    expect(toAbsolute(null)).toBe('')
  })

  it('uploadUrl：已带 /gudu 的上传地址不重复拼 context-path', () => {
    expect(uploadUrl('/gudu/uploads/original/1.jpg')).toMatch(/\/gudu\/uploads\/original\/1\.jpg$/)
    expect(uploadUrl('/gudu/uploads/original/1.jpg')).not.toContain('/gudu/gudu/')
    expect(uploadUrl('/uploads/x.jpg')).toBe(toAbsolute('/uploads/x.jpg'))
    expect(uploadUrl('https://a.com/x.jpg')).toBe('https://a.com/x.jpg')
    expect(uploadUrl(null)).toBe('')
  })

  it('toThumbnail：/original/ → /thumbnail/', () => {
    expect(toThumbnail('https://h.com/gudu/uploads/original/1.jpg')).toBe(
      'https://h.com/gudu/uploads/thumbnail/1.jpg',
    )
  })

  it('thumbOf：组合相对路径直出缩略图绝对地址', () => {
    expect(thumbOf('/uploads/original/a.jpg')).toContain('/uploads/thumbnail/a.jpg')
  })

  // A.3：后端返回的上传地址带短时签名 ?e=&s=（签名对原图与缩略图通用），拼地址时必须原样保留查询串
  it('签名地址：toAbsolute / thumbOf / thumbList 保留 ?e=&s=', () => {
    const signed = '/gudu/uploads/original/20261026/abc.jpg?e=1793030400&s=Ab_-9x'
    expect(toAbsolute(signed).endsWith('/gudu/uploads/original/20261026/abc.jpg?e=1793030400&s=Ab_-9x')).toBe(true)
    expect(thumbOf(signed).endsWith('/uploads/thumbnail/20261026/abc.jpg?e=1793030400&s=Ab_-9x')).toBe(true)
    const two = thumbList(`${signed},/gudu/uploads/original/20261026/b.png?e=1&s=Z`)
    expect(two).toHaveLength(2)
    expect(two[1].endsWith('/thumbnail/20261026/b.png?e=1&s=Z')).toBe(true)
  })

  // 2026-10-27 真机：上传接口返回 /gudu/uploads/…，thumbOf 拼成 /gudu/gudu/… 导致头像缩略图加载失败
  it('已带 /gudu 的上传地址：toAbsolute / thumbOf / thumbList / originalList 都不重复拼 context-path', () => {
    const signed = '/gudu/uploads/original/20261027/av.jpg?e=1&s=X'
    for (const u of [toAbsolute(signed), thumbOf(signed), ...thumbList(signed), ...originalList(signed)]) {
      expect(u).not.toContain('/gudu/gudu/')
      expect(u).toMatch(/^https?:\/\/[^/]+\/gudu\/uploads\//)
    }
    expect(thumbOf(signed)).toBe(toAbsolute(signed).replace('/original/', '/thumbnail/'))
  })

  it('thumbList / originalList：逗号分隔多图拆分 + 过滤空', () => {
    const urls = thumbList('/uploads/original/a.jpg,/uploads/original/b.jpg,')
    expect(urls).toHaveLength(2)
    expect(urls[0]).toContain('/thumbnail/a.jpg')
    expect(originalList('/uploads/original/a.jpg')).toHaveLength(1)
    expect(thumbList(null)).toEqual([])
  })
})
