import { defineStore } from 'pinia'
import { ref } from 'vue'
import { themes, getTheme, type Theme } from '@/styles/themes'

const THEME_KEY = 'gudu-theme'

export const useThemeStore = defineStore('theme', () => {
  const current = ref<string>(localStorage.getItem(THEME_KEY) || 'warm')

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
