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

  it('初始 current 默认 cream（localStorage 无值时）', () => {
    const store = useThemeStore()
    expect(store.current).toBe('cream')
  })

  it('localStorage 有值时初始恢复', () => {
    localStorage.setItem('gudu-theme', 'matcha')
    const store = useThemeStore()
    expect(store.current).toBe('matcha')
  })

  it('旧版 key（green）恢复为 matcha', () => {
    localStorage.setItem('gudu-theme', 'green')
    const store = useThemeStore()
    expect(store.current).toBe('matcha')
  })

  describe('apply', () => {
    it('切换到 matcha → 设置 CSS 变量 + data-theme + 持久化', () => {
      const store = useThemeStore()
      store.apply('matcha')

      const el = document.documentElement
      const style = el.style
      expect(style.getPropertyValue('--el-color-primary')).toBe('#7A9A5B')
      expect(style.getPropertyValue('--yh-sidebar')).toBe('#648449')
      expect(style.getPropertyValue('--yh-bg')).toBe('#F7F5EE')
      expect(el.getAttribute('data-theme')).toBe('matcha')
      expect(store.current).toBe('matcha')
      expect(localStorage.getItem('gudu-theme')).toBe('matcha')
    })

    it('切换到 cream → 设置对应主色', () => {
      const store = useThemeStore()
      store.apply('cream')

      const style = document.documentElement.style
      expect(style.getPropertyValue('--el-color-primary')).toBe('#E89150')
      expect(style.getPropertyValue('--yh-sidebar')).toBe('#D17A3C')
      expect(localStorage.getItem('gudu-theme')).toBe('cream')
    })

    it('未知 key → fallback 到默认皮肤 cream', () => {
      const store = useThemeStore()
      store.apply('not-exist')

      expect(store.current).toBe('cream')
      expect(document.documentElement.style.getPropertyValue('--el-color-primary')).toBe('#E89150')
      expect(localStorage.getItem('gudu-theme')).toBe('cream')
    })
  })

  it('themeList() 返回全部主题', () => {
    const store = useThemeStore()
    expect(store.themeList().length).toBeGreaterThanOrEqual(2)
  })

  it('currentTheme() 返回当前主题对象', () => {
    const store = useThemeStore()
    store.apply('matcha')
    expect(store.currentTheme().key).toBe('matcha')
    expect(store.currentTheme().primary).toBe('#7A9A5B')
  })
})
