import { describe, it, expect, beforeEach, vi } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { __testUni } from '../setup'
import {
  THEMES, THEME_OPTIONS, TAB_ICONS, THEME_STORAGE_KEY, themeState, setTheme, initTheme, themeCssVars, normalizeTheme, palette, tabIconPath, applyTabBar,
} from '@/utils/theme'
import { T } from '@/utils/token'
import { SKINS, DEFAULT_SKIN } from '@/styles/tokens.generated'

const srcDir = resolve(process.cwd(), 'src') + '/'

describe('utils/theme 双主题', () => {
  beforeEach(() => {
    __testUni.resetStorage()
    setTheme('cream')
    ;(globalThis as any).uni.setTabBarStyle = vi.fn()
    ;(globalThis as any).uni.setTabBarItem = vi.fn()
  })

  it('normalizeTheme：未知值回退 cream', () => {
    expect(normalizeTheme('matcha')).toBe('matcha')
    expect(normalizeTheme('')).toBe('cream')
    expect(normalizeTheme('dark')).toBe('cream')
  })

  it('themeCssVars：输出 page 级 CSS 变量', () => {
    const v = themeCssVars('matcha')
    expect(v).toContain('--primary:#7A9A5B')
    expect(v).toContain('--primary-deep:#648449')
    expect(v).toContain('--bg:#F7F5EE')
    expect(v.split(';')).toHaveLength(Object.keys(THEMES.matcha).length)
  })

  it('setTheme：持久化 + 响应式状态 + T 跟随 + tabBar 同步', () => {
    setTheme('matcha')
    expect(__testUni.getStorage(THEME_STORAGE_KEY)).toBe('matcha')
    expect(themeState.name).toBe('matcha')
    expect(themeState.style).toBe(themeCssVars('matcha'))
    expect(palette().primary).toBe('#7A9A5B')
    expect(T.primary).toBe('#7A9A5B')
    expect(T.success).toBe('#4FAE6E') // 功能色共享
    const uni = (globalThis as any).uni
    expect(uni.setTabBarStyle).toHaveBeenCalledWith(expect.objectContaining({ selectedColor: '#648449' }))
    expect(uni.setTabBarItem).toHaveBeenCalledTimes(5)
    expect(uni.setTabBarItem).toHaveBeenCalledWith(
      expect.objectContaining({ index: 2, selectedIconPath: '/static/tabbar/recommend-matcha-on.png' }),
    )
  })

  it('initTheme：读取持久化主题', () => {
    __testUni.setStorage(THEME_STORAGE_KEY, 'matcha')
    themeState.name = 'cream'
    expect(initTheme()).toBe('matcha')
    expect(themeState.name).toBe('matcha')
  })

  it('tabIconPath / applyTabBar 无 tabBar 时不抛错', () => {
    expect(tabIconPath('dish', false, 'cream')).toBe('/static/tabbar/dish-cream.png')
    expect(tabIconPath('dish', true, 'cream')).toBe('/static/tabbar/dish-cream-on.png')
    ;(globalThis as any).uni.setTabBarStyle = () => {
      throw new Error('not tab page')
    }
    expect(() => applyTabBar('cream')).not.toThrow()
  })

  it('皮肤注册表来自生成文件：THEMES / THEME_OPTIONS 与 SKINS 一一对应', () => {
    expect(Object.keys(THEMES)).toEqual(SKINS.map((s) => s.key))
    expect(THEME_OPTIONS.map((o) => o.name)).toEqual(SKINS.map((s) => s.key))
    expect(THEME_OPTIONS[0].name).toBe(DEFAULT_SKIN)
    for (const s of SKINS) expect(THEMES[s.key]).toBe(s.palette)
  })

  it('每套皮肤都有全部 tabBar 图标（新增皮肤需跑 scripts/design/gen-tabbar-icons.py）', () => {
    for (const s of SKINS) {
      for (const icon of TAB_ICONS) {
        for (const on of [false, true]) {
          const rel = tabIconPath(icon, on, s.key).replace(/^\//, '')
          expect(existsSync(srcDir + rel), rel).toBe(true)
        }
      }
    }
  })

  it('守护：theme.ts / App.vue 不再手写皮肤色值', () => {
    const theme = readFileSync(srcDir + 'utils/theme.ts', 'utf8')
    expect(theme).not.toMatch(/#[0-9A-Fa-f]{6}\b/)
    const app = readFileSync(srcDir + 'App.vue', 'utf8')
    for (const s of SKINS) {
      for (const k of ['primary', 'bg', 'title', 'body'] as const) {
        expect(app.toUpperCase(), `${s.key}.${k}`).not.toContain(`: ${s.palette[k].toUpperCase()};`)
      }
    }
  })
})
