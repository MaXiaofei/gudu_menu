/**
 * 登录回跳（mini-release-compliance A.10）。
 *
 * 分享落地页（聚餐点菜、菜谱详情…）被未登录 / 未同意协议的人打开时：
 * 带着「原始目标页 + 参数」去登录页，登录成功后回到原页，邀请参数不丢。
 *
 * 防开放跳转：回跳目标只接受站内白名单页面（REDIRECT_WHITELIST + Tab 页），
 * 路径必须以单个 `/` 开头、不含协议/反斜杠，参数名只允许字母数字下划线，值重新编码。
 * 其他一律回首页。
 */
import { isTabPath } from './nav'

export const LOGIN_PAGE = '/pages/login/Login'
export const HOME_PAGE = '/pages/dish/List'

/** 允许登录后回跳的非 Tab 页面（分享落地页）。新增分享落地页时在此登记。 */
export const REDIRECT_WHITELIST: readonly string[] = [
  '/pages/together/Pick',
  '/pages/dish/Detail',
  '/pages/menu/Detail',
]

const MAX_QUERY_KEYS = 8
const MAX_VALUE_LEN = 128
const KEY_RE = /^[A-Za-z0-9_]{1,32}$/

function safeDecode(s: string): string | null {
  try {
    return decodeURIComponent(s)
  } catch {
    return null
  }
}

function isAllowedPath(path: string): boolean {
  return REDIRECT_WHITELIST.includes(path) || isTabPath(path)
}

/** 由页面路径 + 参数拼回跳目标（不校验白名单；sanitizeRedirect 再收口）。 */
export function buildTarget(path: string, query?: Record<string, unknown> | null): string {
  const p = '/' + String(path || '').replace(/^\/+/, '')
  const qs = Object.entries(query || {})
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`)
    .join('&')
  return qs ? `${p}?${qs}` : p
}

/**
 * 校验并规范化回跳目标：合法返回站内 url（参数重新编码），否则 null。
 * 兼容被整体 encodeURIComponent 过一次的值（小程序 onLoad 参数不一定自动解码）。
 */
export function sanitizeRedirect(raw: unknown): string | null {
  if (typeof raw !== 'string') return null
  let s = raw.trim()
  if (/^%2F/i.test(s)) s = safeDecode(s) ?? ''
  if (!s || s.length > 512) return null
  if (!s.startsWith('/') || s.startsWith('//') || s.includes('\\')) return null
  const qi = s.indexOf('?')
  const path = qi >= 0 ? s.slice(0, qi) : s
  if (!/^\/[A-Za-z0-9/_-]+$/.test(path) || path.includes('//') || path.includes('..')) return null
  if (!isAllowedPath(path)) return null
  const query: Record<string, string> = {}
  if (qi >= 0) {
    const pairs = s.slice(qi + 1).split('&').filter(Boolean)
    if (pairs.length > MAX_QUERY_KEYS) return null
    for (const pair of pairs) {
      const ei = pair.indexOf('=')
      const k = ei >= 0 ? pair.slice(0, ei) : pair
      const v = safeDecode(ei >= 0 ? pair.slice(ei + 1) : '')
      if (!KEY_RE.test(k) || v === null || v.length > MAX_VALUE_LEN) return null
      query[k] = v
    }
  }
  return buildTarget(path, query)
}

/** 登录页地址（带合法回跳目标；不合法时不带）。 */
export function loginUrl(target?: string | null): string {
  const t = sanitizeRedirect(target)
  return t ? `${LOGIN_PAGE}?redirect=${encodeURIComponent(t)}` : LOGIN_PAGE
}

/** 登录成功后：回到合法目标页（Tab 走 switchTab，其余 reLaunch 让登录页出栈），否则回首页。 */
export function goAfterLogin(raw?: unknown): void {
  const t = sanitizeRedirect(raw)
  if (!t) {
    uni.switchTab({ url: HOME_PAGE })
    return
  }
  if (isTabPath(t)) uni.switchTab({ url: t.split('?')[0] })
  else uni.reLaunch({ url: t })
}

/** App onLaunch 参数（{path, query}）→ 回跳目标；非白名单返回 null。 */
export function launchTarget(options?: { path?: string; query?: Record<string, unknown> } | null): string | null {
  if (!options?.path) return null
  return sanitizeRedirect(buildTarget(options.path, options.query))
}

/** 当前页面 → 回跳目标（401 被踢回登录页时用）；非白名单返回 null。 */
export function currentPageTarget(): string | null {
  try {
    const pages = getCurrentPages()
    const cur: any = pages[pages.length - 1]
    if (!cur?.route) return null
    return sanitizeRedirect(buildTarget(cur.route, cur.options || cur.$page?.options))
  } catch {
    return null
  }
}

// ---------------------------------------------------------------------------
// 启动登录态：App.vue onLaunch 里登记（静默登录可能还在进行），落地页 await 后再决定。
// ---------------------------------------------------------------------------

let launchAuth: Promise<void> = Promise.resolve()
let loginRedirected = false

/** App.vue onLaunch 同步调用：登记启动登录流程。 */
export function trackLaunchAuth(p: Promise<void>): void {
  launchAuth = p.catch(() => undefined)
}

/** 启动登录流程结束（静默登录完成或已跳登录页）。 */
export function launchAuthDone(): Promise<void> {
  return launchAuth
}

/** 跳登录页（带回跳目标）；同一次启动内只跳一次，避免 App 与落地页重复 reLaunch。 */
export function redirectToLogin(target?: string | null): void {
  if (loginRedirected) return
  loginRedirected = true
  uni.reLaunch({ url: loginUrl(target) })
}

/** 登录成功后复位（下次 401 / 退出还能再跳）。 */
export function resetLoginRedirect(): void {
  loginRedirected = false
}

/**
 * 落地页守卫：等启动登录流程结束，已登录返回 true；
 * 否则带 target 跳登录页并返回 false（页面不要再发请求）。
 */
export async function ensureLogin(target: string): Promise<boolean> {
  await launchAuth
  if (uni.getStorageSync('token')) return true
  redirectToLogin(target)
  return false
}
