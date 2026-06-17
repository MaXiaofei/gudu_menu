export interface Theme {
  key: 'warm' | 'green'
  name: string
  primary: string
  sidebar: string
  bg: string
}

export const themes: Theme[] = [
  { key: 'warm', name: '烟火暖橙', primary: '#E8602C', sidebar: '#2B1A12', bg: '#FFFAF5' },
  { key: 'green', name: '鲜蔬青绿', primary: '#2EA66B', sidebar: '#1F3A2E', bg: '#F6FBF8' },
]

export function getTheme(key: string): Theme {
  return themes.find((t) => t.key === key) || themes[0]
}
