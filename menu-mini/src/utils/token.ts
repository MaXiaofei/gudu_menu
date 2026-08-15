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
