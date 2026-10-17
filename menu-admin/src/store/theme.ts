import { defineStore } from 'pinia'
import { ref } from 'vue'
import { themes, getTheme, type Theme } from '@/styles/themes'

const THEME_KEY = 'gudu-theme'

export const useThemeStore = defineStore('theme', () => {
  // 读取时兼容旧 key（warm/green），统一规范为皮肤 key
  const current = ref<string>(getTheme(localStorage.getItem(THEME_KEY)).key)

  function apply(key: string) {
    const t: Theme = getTheme(key)
    const r = document.documentElement.style
    r.setProperty('--el-color-primary', t.primary)
    r.setProperty('--el-color-primary-light-3', `color-mix(in srgb, ${t.primary} 70%, white)`)
    r.setProperty('--el-color-primary-light-5', `color-mix(in srgb, ${t.primary} 50%, white)`)
    r.setProperty('--el-color-primary-light-7', `color-mix(in srgb, ${t.primary} 30%, white)`)
    r.setProperty('--el-color-primary-light-8', `color-mix(in srgb, ${t.primary} 22%, white)`)
    r.setProperty('--el-color-primary-light-9', `color-mix(in srgb, ${t.primary} 14%, white)`)
    r.setProperty('--el-color-primary-dark-2', `color-mix(in srgb, ${t.primary} 80%, black)`)
    r.setProperty('--yh-sidebar', t.sidebar)
    r.setProperty('--yh-sidebar-text', t.sidebarText)
    r.setProperty('--yh-sidebar-active-bg', t.sidebarActiveBg)
    r.setProperty('--yh-bg', t.bg)
    r.setProperty('--yh-panel', t.card)
    r.setProperty('--yh-border', t.border)
    r.setProperty('--yh-title', t.title)
    r.setProperty('--yh-body', t.body)
    r.setProperty('--yh-caption', t.caption)
    r.setProperty('--yh-primary-soft', t.primarySoft)
    r.setProperty('--yh-secondary', t.secondary)
    r.setProperty('--yh-accent', t.accent)
    r.setProperty('--yh-highlight', t.highlight)
    r.setProperty('--yh-primary-deep', t.primaryDeep)
    r.setProperty('--yh-on-primary', t.onPrimary)
    r.setProperty('--yh-shadow-rgb', t.shadowRgb)
    document.documentElement.setAttribute('data-theme', t.key)
    current.value = t.key
    localStorage.setItem(THEME_KEY, t.key)
  }

  function themeList() {
    return themes
  }

  function currentTheme() {
    return getTheme(current.value)
  }

  return { current, themes, apply, themeList, currentTheme }
})
