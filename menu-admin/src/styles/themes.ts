export interface Theme {
  key: 'warm' | 'green'
  name: string
  primary: string
  sidebar: string
  bg: string
}

export const themes: Theme[] = [
  { key: 'warm', name: '奶油轻食', primary: '#E89150', sidebar: '#3A2818', bg: '#FDFAF4' },
  { key: 'green', name: '抹茶禅意', primary: '#7A9A5B', sidebar: '#2E3520', bg: '#F7F5EE' },
]

export function getTheme(key: string): Theme {
  return themes.find((t) => t.key === key) || themes[0]
}
