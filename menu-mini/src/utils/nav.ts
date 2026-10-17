/**
 * 主导航（showcase-ai-community）：菜谱 / 食集 / 咕嘟（中间）/ 厨房圈 / 我家余量；
 * 「我的」改为主 Tab 右上角头像进入；原「智荐」改为二级页「全部推荐」。
 * 顺序与 pages.json tabBar.list、utils/theme.ts TAB_ICONS 一致（test/utils/nav.spec.ts 守护）。
 */

export interface TabDef {
  key: 'dish' | 'menu' | 'agent' | 'community' | 'pantry'
  text: string
  path: string
}

export const TABS: readonly TabDef[] = [
  { key: 'dish', text: '菜谱', path: '/pages/dish/List' },
  { key: 'menu', text: '食集', path: '/pages/menu/List' },
  { key: 'agent', text: '咕嘟', path: '/pages/agent/Index' },
  { key: 'community', text: '厨房圈', path: '/pages/community/Index' },
  { key: 'pantry', text: '我家余量', path: '/pages/pantry/List' },
]

/** 新模块页面路径（分包）。 */
export const PAGES = {
  profile: '/pages/profile/Profile',
  smart: '/pages/recommend/Index',
  agentChat: '/pkg-agent/Chat',
  agentVoice: '/pkg-agent/Voice',
  post: '/pkg-community/Post',
  postCreate: '/pkg-community/Create',
  topic: '/pkg-community/Topic',
  scan: '/pkg-ai/Scan',
  scanResult: '/pkg-ai/ScanResult',
  weekPlan: '/pkg-ai/WeekPlan',
  nutrition: '/pkg-ai/Nutrition',
  cookMode: '/pkg-ai/CookMode',
} as const

/** 跟做模式最后一步「做好了」→ 回菜谱详情自动打开做完确认（值 = dishId）。 */
export const COOK_AFTER_MODE_KEY = 'gd_cook_after_mode'

function pathOnly(url: string): string {
  return ('/' + url.replace(/^\//, '')).split('?')[0]
}

export function isTabPath(url: string): boolean {
  const p = pathOnly(url)
  return TABS.some((t) => t.path === p)
}

/** 拼 query（跳过空值）。 */
export function withQuery(path: string, q?: Record<string, string | number | null | undefined>): string {
  const qs = Object.entries(q || {})
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`)
    .join('&')
  return qs ? `${path}?${qs}` : path
}

/** 统一跳转：Tab 页走 switchTab（不能带参），其余 navigateTo。 */
export function go(url: string): void {
  if (isTabPath(url)) uni.switchTab({ url: pathOnly(url) })
  else uni.navigateTo({ url })
}

export function goTab(key: TabDef['key']): void {
  const t = TABS.find((x) => x.key === key)
  if (t) uni.switchTab({ url: t.path })
}

/** 右上角头像 → 我的。 */
export function goProfile(): void {
  uni.navigateTo({ url: PAGES.profile })
}
