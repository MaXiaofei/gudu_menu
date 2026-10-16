/**
 * 多皮肤运行时切换。
 *
 * 皮肤注册表唯一来源：docs/design/tokens.json `color.theme`，经
 * `node scripts/design/gen-tokens.mjs` 生成到 styles/tokens.generated.ts（SKINS）；
 * 本文件不手写任何皮肤色值，新增皮肤只改 JSON + 重新生成（tabBar 图标另跑
 * scripts/design/gen-tabbar-icons.py）。
 * 落地方式：
 * - 小程序：每个页面模板首节点 `<page-meta :page-style="gdThemeStyle" />`，
 *   gdThemeStyle 由 main.ts 全局 mixin 提供（读本模块的响应式 themeState）。
 *   page 上覆盖 CSS 变量 → 页面内所有 var(--primary) 等随之切换。
 * - H5：page-meta 只作用于第一个 uni-page-body，另注入一段全局 <style> 兜底所有页面栈。
 * - tabBar：uni.setTabBarStyle + setTabBarItem 换选中色与选中图标。
 * 持久化：storage `gd_theme`（存皮肤 key）。
 */
import { reactive } from 'vue'
import { DEFAULT_SKIN, SKINS, skinCssVars, type SkinPalette } from '@/styles/tokens.generated'

/** 皮肤 key（cream / matcha / …，以 tokens.json 为准）。 */
export type ThemeName = string

export type ThemePalette = SkinPalette

export const THEMES: Record<ThemeName, ThemePalette> = Object.fromEntries(
  SKINS.map((s) => [s.key, s.palette]),
)

export const THEME_OPTIONS: { name: ThemeName; label: string; desc: string }[] = SKINS.map((s) => ({
  name: s.key,
  label: s.label,
  desc: s.tagline,
}))

export const THEME_STORAGE_KEY = 'gd_theme'

/** tabBar 页（顺序与 pages.json tabBar.list 一致），图标文件名前缀。 */
export const TAB_ICONS = ['dish', 'menu', 'recommend', 'pantry', 'profile'] as const

export function normalizeTheme(v: unknown): ThemeName {
  return typeof v === 'string' && v in THEMES ? v : DEFAULT_SKIN
}

/** 主题 → CSS 变量声明串（page-meta page-style 用）。 */
export function themeCssVars(name: ThemeName): string {
  return skinCssVars(normalizeTheme(name))
}

function readStored(): ThemeName {
  try {
    return normalizeTheme(uni.getStorageSync(THEME_STORAGE_KEY))
  } catch {
    return DEFAULT_SKIN
  }
}

/** 全局响应式主题状态（mixin 计算属性读它）。 */
export const themeState = reactive<{ name: ThemeName; style: string }>({
  name: DEFAULT_SKIN,
  style: themeCssVars(DEFAULT_SKIN),
})

export function currentTheme(): ThemeName {
  return themeState.name
}

/** 当前主题色板（JS 侧取色：canvas 绘制等）。 */
export function palette(): ThemePalette {
  return THEMES[normalizeTheme(themeState.name)]
}

/** tabBar 选中图标路径。 */
export function tabIconPath(icon: string, selected: boolean, theme: ThemeName): string {
  return `/static/tabbar/${icon}-${theme}${selected ? '-on' : ''}.png`
}

/** 同步 tabBar 选中色 + 选中图标（非 tab 页调用可能失败，静默）。 */
export function applyTabBar(name: ThemeName = themeState.name): void {
  const p = THEMES[normalizeTheme(name)]
  try {
    uni.setTabBarStyle({
      color: p.caption,
      selectedColor: p.primaryDeep,
      backgroundColor: p.card,
      borderStyle: 'white',
      fail: () => {},
    } as any)
    TAB_ICONS.forEach((icon, index) => {
      uni.setTabBarItem({
        index,
        iconPath: tabIconPath(icon, false, name),
        selectedIconPath: tabIconPath(icon, true, name),
        fail: () => {},
      } as any)
    })
  } catch {
    // 非 tab 页 / 测试环境无 tabBar
  }
}

/** H5：注入全局样式覆盖所有页面栈（page-meta 只改第一个 uni-page-body）。 */
function applyH5(name: ThemeName): void {
  // #ifdef H5
  try {
    if (typeof document === 'undefined') return
    let el = document.getElementById('gd-theme-vars') as HTMLStyleElement | null
    if (!el) {
      el = document.createElement('style')
      el.id = 'gd-theme-vars'
      document.head.appendChild(el)
    }
    const decl = themeCssVars(name)
      .split(';')
      .map((d) => `${d} !important`)
      .join(';')
    el.textContent = `uni-page-body,body{${decl}}`
    document.documentElement.setAttribute('data-theme', name)
  } catch {
    // ignore
  }
  // #endif
}

/** 设置主题：更新响应式状态 + 持久化 + tabBar + H5 兜底。 */
export function setTheme(name: ThemeName): void {
  const n = normalizeTheme(name)
  themeState.name = n
  themeState.style = themeCssVars(n)
  try {
    uni.setStorageSync(THEME_STORAGE_KEY, n)
  } catch {
    // ignore
  }
  applyTabBar(n)
  applyH5(n)
}

/** 启动时读持久化主题（App onLaunch 调用）。 */
export function initTheme(): ThemeName {
  const n = readStored()
  themeState.name = n
  themeState.style = themeCssVars(n)
  applyH5(n)
  return n
}
