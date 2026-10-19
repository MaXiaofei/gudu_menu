// 图片 URL 规则（与 Flutter core/image_helper.dart、小程序 utils/image.ts 一致）：
// 后端返回绝对地址或 /gudu 前缀的相对地址，原样使用；缩略图把路径里的 /original/ 换成 /thumbnail/。

/** 取缩略图地址；空值返回空串（组件据此回退首字占位）。 */
export function thumbOf(url: string | null | undefined): string {
  const u = (url ?? '').trim()
  if (!u) return ''
  return u.includes('/original/') ? u.replace('/original/', '/thumbnail/') : u
}

/** 名称首字（图片缺失/加载失败时的占位），空名回退 fallback。 */
export function initialOf(name: string | null | undefined, fallback = '菜'): string {
  const s = (name ?? '').trim()
  return s ? Array.from(s)[0] : fallback
}
