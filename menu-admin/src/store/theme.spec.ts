import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

const { useThemeStore } = await import('./theme')

describe('useThemeStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    document.documentElement.style.cssText = ''
    document.documentElement.removeAttribute('data-theme')
  })

  it('初始 current 默认 warm（localStorage 无值时）', () => {
    const store = useThemeStore()
    expect(store.current).toBe('warm')
  })

  it('localStorage 有值时初始恢复', () => {
    localStorage.setItem('gudu-theme', 'green')
    const store = useThemeStore()
    expect(store.current).toBe('green')
  })

  describe('apply', () => {
    it('切换到 green → 设置 CSS 变量 + data-theme + 持久化', () => {
      const store = useThemeStore()
      store.apply('green')

      const el = document.documentElement
      const style = el.style
      expect(style.getPropertyValue('--el-color-primary')).toBe('#7A9A5B')
      expect(style.getPropertyValue('--yh-sidebar')).toBe('#2E3520')
      expect(style.getPropertyValue('--yh-bg')).toBe('#F7F5EE')
      expect(el.getAttribute('data-theme')).toBe('green')
      expect(store.current).toBe('green')
      expect(localStorage.getItem('gudu-theme')).toBe('green')
    })

    it('切换到 warm → 设置对应主色', () => {
      const store = useThemeStore()
      store.apply('warm')

      const style = document.documentElement.style
      expect(style.getPropertyValue('--el-color-primary')).toBe('#E89150')
      expect(style.getPropertyValue('--yh-sidebar')).toBe('#3A2818')
      expect(localStorage.getItem('gudu-theme')).toBe('warm')
    })

    it('未知 key → fallback 到 themes[0](warm) 并应用 warm 主色', () => {
      const store = useThemeStore()
      store.apply('not-exist')

      expect(store.current).toBe('warm')
      expect(document.documentElement.style.getPropertyValue('--el-color-primary')).toBe('#E89150')
      expect(localStorage.getItem('gudu-theme')).toBe('warm')
    })
  })

  it('themeList() 返回全部主题', () => {
    const store = useThemeStore()
    expect(store.themeList().length).toBeGreaterThanOrEqual(2)
  })

  it('currentTheme() 返回当前主题对象', () => {
    const store = useThemeStore()
    store.apply('green')
    expect(store.currentTheme().key).toBe('green')
    expect(store.currentTheme().primary).toBe('#7A9A5B')
  })
})
