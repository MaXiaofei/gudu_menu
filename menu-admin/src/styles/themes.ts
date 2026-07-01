export interface Theme {
  key: 'warm' | 'green'
  name: string
  primary: string
  sidebar: string          // 侧栏背景（浅色）
  sidebarText: string      // 侧栏菜单文字
  sidebarActiveBg: string  // 侧栏选中项背景（主色）
  bg: string
}

export const themes: Theme[] = [
  { key: 'warm', name: '奶油轻食', primary: '#E89150', sidebar: '#D17A3C', sidebarText: '#FFFFFF', sidebarActiveBg: '#E89150', bg: '#FDFAF4' },
  { key: 'green', name: '抹茶禅意', primary: '#7A9A5B', sidebar: '#648449', sidebarText: '#FFFFFF', sidebarActiveBg: '#7A9A5B', bg: '#F7F5EE' },
]

export function getTheme(key: string): Theme {
  return themes.find((t) => t.key === key) || themes[0]
}
