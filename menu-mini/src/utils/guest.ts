/**
 * 游客浏览（微信审核：未登录也要能先体验功能，用户自己选择何时登录）。
 *
 * - 游客可直接打开的页面：GUEST_PAGES（首页菜谱、详情、搜索、食材库、协议、跟做模式…）；
 *   其余页面在 navigateTo 时被路由守卫拦下，弹「登录后可以使用这个功能」，用户点「去登录」才进登录页，
 *   登录后回到刚才的页面（复用 loginRedirect 的 redirect 机制）；
 * - 底部 Tab 里要登录的页（食集 / 余量）不拦 switchTab，页面自己渲染「登录后查看你家的…」占位；
 * - request.ts 的 401 按 classify401 分流：静默请求不提示，本来就要登录的动作提示登录。
 */
import { currentPageTarget, loginUrl, sanitizeRedirect } from './loginRedirect'
import { bindPromptLogin } from './requestBridge'

export const MSG_LOGIN_NEEDED = '登录后可以使用这个功能'

export function isLoggedIn(): boolean {
  return !!uni.getStorageSync('token')
}

/** 游客可直接打开的页面（不含 query）。新增「游客能看」的页面时在此登记。 */
export const GUEST_PAGES: readonly string[] = [
  '/pages/login/Login',
  '/pages/dish/List',
  '/pages/dish/Detail',
  '/pages/recommend/Index',
  '/pages/agent/Index',
  '/pages/ingredient/List',
  '/pages/profile/Profile', // 游客态只展示登录入口 + 协议 / 关于
  '/pages/profile/Legal',
  '/pages/profile/About',
  '/pages/profile/Theme',
  '/pkg-ai/CookMode', // 跟做模式：只看步骤与计时
  // 分享落地页：自己判断登录态，先展示可公开的预览，操作时再登录
  '/pages/together/Pick',
  '/pkg-family/Invite',
]

/** Tab 页：游客可进入，页面自己渲染「登录后查看…」占位（switchTab 本就不经 navigateTo）。 */
const GUEST_TAB_PAGES: readonly string[] = ['/pages/menu/List', '/pages/pantry/List', '/pages/community/Index']

function pathOf(url: string): string {
  return ('/' + String(url || '').replace(/^\//, '')).split('?')[0]
}

/** 该站内跳转目标对游客是否需要先登录。 */
export function routeNeedsLogin(url: string): boolean {
  const p = pathOf(url)
  return !GUEST_PAGES.includes(p) && !GUEST_TAB_PAGES.includes(p)
}

export type Unauthorized = 'guest-silent' | 'guest-prompt' | 'relogin'

/**
 * 401 分流：
 * - 游客（本来就没 token）：静默请求直接失败不打扰；其余是「需要登录的动作」→ 提示登录；
 * - 有 token 但失效：先走静默重登（relogin），重登失败再按 afterReloginFailed 处理。
 */
export function classify401(p: { hasToken: boolean; silent?: boolean }): Unauthorized {
  if (p.hasToken) return 'relogin'
  return p.silent ? 'guest-silent' : 'guest-prompt'
}

/** token 失效且重登失败后：静默请求不提示，其余提示登录。 */
export function afterReloginFailed(silent?: boolean): 'guest-silent' | 'guest-prompt' {
  return silent ? 'guest-silent' : 'guest-prompt'
}

let prompting = false
/** 仅测试用：复位「正在弹窗」状态。 */
export function resetPromptState(): void {
  prompting = false
}

/**
 * 弹「登录后可以使用这个功能」。用户点「去登录」才进登录页（带回跳目标）；
 * 同一时刻只弹一个，避免并发请求连弹。返回用户是否选择去登录。
 */
export function promptLogin(opts?: { target?: string | null; onCancel?: () => void }): Promise<boolean> {
  if (prompting) return Promise.resolve(false)
  prompting = true
  const target = opts && 'target' in opts ? opts.target : currentPageTarget()
  return new Promise((resolve) => {
    const done = (go: boolean) => {
      prompting = false
      if (go) uni.navigateTo({ url: loginUrl(target) })
      else opts?.onCancel?.()
      resolve(go)
    }
    try {
      uni.showModal({
        title: '提示',
        content: MSG_LOGIN_NEEDED,
        confirmText: '去登录',
        cancelText: '再看看',
        success: (r: any) => done(!!r?.confirm),
        fail: () => done(false),
      })
    } catch {
      done(false)
    }
  })
}

/** 动作前置：已登录返回 true；游客弹登录提示并返回 false。 */
export function requireLogin(target?: string | null): boolean {
  if (isLoggedIn()) return true
  void promptLogin({ target: target === undefined ? currentPageTarget() : target })
  return false
}

/** 路由守卫判定：navigateTo 的目标 url 是否放行；游客去需登录页时拦下并弹提示（回跳目标为将要去的页面）。 */
export function guardNavigate(url: string): boolean {
  if (isLoggedIn() || !routeNeedsLogin(url)) return true
  void promptLogin({ target: sanitizeRedirect(url) })
  return false
}

let installed = false
/** App.onLaunch 调用一次：给 navigateTo 装游客守卫。 */
export function installRouteGuard(): void {
  if (installed) return
  installed = true
  try {
    ;(uni as any).addInterceptor('navigateTo', {
      invoke(args: { url?: string }) {
        return guardNavigate(args?.url || '')
      },
    })
  } catch {
    // 测试环境无 addInterceptor
  }
}

// 注册给 loginRedirect.ensureLogin 使用（它不能动态 import 本文件，见 requestBridge.ts）
bindPromptLogin(promptLogin)
