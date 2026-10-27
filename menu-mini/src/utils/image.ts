import { BASE } from './request'

// 上传图片地址（A.3）：后端出参统一带短时签名 ?e=过期&s=签名（签名在后端 HTTP 序列化层集中生成，前端拿不到密钥），
// 签名对原图与缩略图通用——这里只做拼前缀 / 换 thumbnail，必须原样保留查询串；保存时把收到的地址原样回传即可。

/**
 * 相对路径 → 绝对地址（同 Flutter ImageHelper.toAbsolute；已是 http(s) 原样返回）。
 * 上传接口返回的地址已带 context-path（/gudu/uploads/…）：拼 BASE 的源（去掉末尾 /gudu），
 * 不能拼成 /gudu/gudu/…（2026-10-27 真机：选完头像缩略图 404，看起来像「选中没反应」）。
 */
export function toAbsolute(url?: string | null): string {
  if (!url) return ''
  if (/^https?:\/\//.test(url)) return url
  if (url.startsWith('/gudu/')) return BASE.replace(/\/gudu\/?$/, '') + url
  return BASE + url
}

/** 上传接口返回的地址 → 绝对地址（toAbsolute 已处理 context-path 前缀，保留此名供现有调用）。 */
export function uploadUrl(url?: string | null): string {
  return toAbsolute(url)
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
