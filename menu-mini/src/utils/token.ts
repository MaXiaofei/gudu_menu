/**
 * 设计 token 的 JS 侧常量（CSS 变量见 App.vue）。
 * 用于 JS 动态取色（canvas 绘制、动态 style 绑定等）。
 * 权威定义对齐 menu-flutter/lib/core/app_theme.dart 的 AppTokens.cream。
 */
export const T = {
  primary: '#E89150',
  primaryDeep: '#D17A3C',
  primarySoft: '#F6D9BE',
  secondary: '#FBF0DD',
  accent: '#B8762E',
  bg: '#FDFAF4',
  card: '#FFFFFF',
  border: '#F0E6D6',
  title: '#4A382A',
  body: '#6E5C49',
  caption: '#9C8C7A',
  highlight: '#FFF7EC',
  success: '#4FAE6E',
  warning: '#E5A938',
  warningText: '#B8860B',
  error: '#DB5A4E',
  info: '#4FA0D0',
} as const

/** 库存档位 → 主题色（ENOUGH 绿 / LOW 黄 / NONE 红；与 Flutter stockColor 一致）。 */
export function stockColor(level?: string | null): string {
  if (level === 'ENOUGH') return T.success
  if (level === 'LOW') return T.warning
  return T.error
}

/** 库存档位 → 中文文案。 */
export function stockLabel(level?: string | null): string {
  if (level === 'ENOUGH') return '充足'
  if (level === 'LOW') return '不足'
  return '用完'
}

/** 备菜状态 → 主题色（READY 绿 / THAWING 蓝 / MARINATING 黄 / PENDING 灰）。 */
export function prepColor(status?: string | null): string {
  switch (status) {
    case 'READY': return T.success
    case 'THAWING': return T.info
    case 'MARINATING': return T.warning
    default: return T.caption
  }
}

/** 备菜状态 → 中文文案。 */
export function prepLabel(status?: string | null): string {
  switch (status) {
    case 'READY': return '✓ 已备'
    case 'THAWING': return '化冻中'
    case 'MARINATING': return '腌制中'
    default: return '待备'
  }
}

/** 状态栏高度（自定义导航用，px）。 */
export function statusBarHeight(): number {
  try {
    return uni.getSystemInfoSync().statusBarHeight || 0
  } catch {
    return 0
  }
}

/**
 * 自定义导航统一顶距（状态栏 + 胶囊区，px）。
 * 所有页面顶栏第一行统一用它做 padding-top：
 * - 内容起始高度全站一致，切换页面不会高低不齐；
 * - 顶栏右上按钮落到微信胶囊（关闭/…）下方，不再被遮挡。
 * 微信端按胶囊真实位置算（胶囊底部 + 8px 呼吸距），其他端回退 状态栏 + 44。
 */
export function navInset(): number {
  try {
    // #ifdef MP-WEIXIN
    const cap = (uni as any).getMenuButtonBoundingClientRect?.()
    if (cap && cap.bottom > 0) return cap.bottom + 8
    // #endif
    return statusBarHeight() + 44
  } catch {
    return statusBarHeight() + 44
  }
}
