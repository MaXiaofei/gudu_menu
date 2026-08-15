import { BASE } from './request'

/** 相对路径 → 绝对地址（同 Flutter ImageHelper.toAbsolute；已是 http(s) 原样返回）。 */
export function toAbsolute(url?: string | null): string {
  if (!url) return ''
  if (/^https?:\/\//.test(url)) return url
  return BASE + url
}

/** 原图 → 缩略图（路径 /original/ → /thumbnail/，同 Flutter ImageHelper）。 */
export function toThumbnail(absUrl: string): string {
  return absUrl.replace('/original/', '/thumbnail/')
}

/** 组合：相对路径直出缩略图绝对地址。 */
export function thumbOf(url?: string | null): string {
  return toThumbnail(toAbsolute(url))
}

/** 逗号分隔的多图字段 → 缩略图 URL 数组。 */
export function thumbList(images?: string | null): string[] {
  if (!images) return []
  return images.split(',').filter(Boolean).map(thumbOf)
}

/** 原图数组（全屏预览用）。 */
export function originalList(images?: string | null): string[] {
  if (!images) return []
  return images.split(',').filter(Boolean).map(toAbsolute)
}
