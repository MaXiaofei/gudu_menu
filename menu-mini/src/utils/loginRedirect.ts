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
import { getAccountId, invitePath, onboardKey, PENDING_INVITE_KEY, profileIsBlank } from './family'
import { shouldPromptProfile } from './profileSetup'
import { listMembers } from '@/api/member'

export const LOGIN_PAGE = '/pages/login/Login'
export const HOME_PAGE = '/pages/dish/List'
/** 家庭邀请落地页（multi-tenant-family）。 */
export const INVITE_PAGE = '/pkg-family/Invite'
/** 成员档案引导页（首次引导第 2 步）。 */
export const ONBOARD_PAGE = '/pkg-family/Onboard'

/** 允许登录后回跳的非 Tab 页面（分享落地页）。新增分享落地页时在此登记。 */
export const REDIRECT_WHITELIST: readonly string[] = [
  '/pages/together/Pick',
  '/pages/dish/Detail',
  '/pages/menu/Detail',
  INVITE_PAGE,
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

/** 跳到站内目标：Tab 走 switchTab，其余 reLaunch（让登录页 / 落地页出栈）。返回实际目标。 */
function go(url: string): string {
  if (isTabPath(url)) uni.switchTab({ url: url.split('?')[0] })
  else uni.reLaunch({ url })
  return url
}

/**
 * 登录成功后的唯一去向逻辑（登录页、邀请落地页共用）。优先级：
 *  1. 待加入家庭邀请（PENDING_INVITE_KEY）→ 回邀请落地页继续「加入」；
 *  2. redirect 回跳（白名单校验）；
 *  3. 首次引导：昵称仍是默认 → 回首页，由首页弹头像昵称（A.7），关闭后 continueFirstRun 决定是否进成员档案引导；
 *     昵称已改过、本账号还没处理过成员档案引导且本人档案为空 → 直接进成员档案引导；
 *  4. 首页。
 * 不需要查档案的分支同步完成跳转。
 */
export function goAfterLogin(raw?: unknown, accountId?: number | null): Promise<string> {
  const pending = uni.getStorageSync(PENDING_INVITE_KEY)
  if (pending) return Promise.resolve(go(invitePath(String(pending))))
  const t = sanitizeRedirect(raw)
  if (t) return Promise.resolve(go(t))
  if (shouldPromptProfile() || !onboardPending(accountId)) return Promise.resolve(go(HOME_PAGE))
  return needOnboard(accountId).then((need) => go(need ? ONBOARD_PAGE : HOME_PAGE))
}

// ---------------------------------------------------------------------------
// 首次引导（头像昵称 A.7 → 成员档案 multi-tenant-family）：顺序进行、都可跳过、跳过不再打扰。
// ---------------------------------------------------------------------------

/** 本账号还没处理过成员档案引导（无账号 id 视为不需要）。 */
function onboardPending(accountId?: number | null): boolean {
  const id = accountId || getAccountId()
  return !!id && !uni.getStorageSync(onboardKey(id))
}

/** 记「成员档案引导已处理」（完成或跳过都记，按账号）。 */
export function markOnboarded(accountId?: number | null): void {
  const id = accountId || getAccountId()
  if (id) uni.setStorageSync(onboardKey(id), '1')
}

/**
 * 是否需要成员档案引导：本账号没处理过，且本人在当前家庭的档案还没填身体 / 健康信息。
 * 老用户档案已填 → 记已处理，不再打扰。
 */
export async function needOnboard(accountId?: number | null): Promise<boolean> {
  const id = accountId || getAccountId()
  if (!onboardPending(id)) return false
  try {
    const mine: any = (await listMembers()).find((m: any) => m.accountId === id)
    if (mine && !profileIsBlank(mine.healthProfile)) {
      markOnboarded(id)
      return false
    }
    return !!mine
  } catch {
    return false
  }
}

/**
 * 首页头像昵称弹窗（A.7）关闭后调用：
 * - 保存了 → 接着进成员档案引导（如需要）；
 * - 跳过了 → 视为「先别打扰」，成员档案引导也记为已处理，不再连着弹第二次（之后在「成员档案」随时补）。
 */
export async function continueFirstRun(saved: boolean): Promise<boolean> {
  if (!saved) {
    markOnboarded()
    return false
  }
  if (!(await needOnboard())) return false
  uni.navigateTo({ url: ONBOARD_PAGE })
  return true
}

/**
 * 加入新家庭后：昵称仍是默认 → 先回首页弹头像昵称（之后 continueFirstRun 接成员档案引导）；
 * 否则直接进新家庭的成员档案引导（可跳过）。
 */
export function goAfterJoin(): string {
  if (shouldPromptProfile()) return go(HOME_PAGE)
  return go(`${ONBOARD_PAGE}?from=join`)
}

/**
 * App onLaunch 参数（{path, query}）→ 回跳目标；非白名单返回 null。
 * 来自家庭邀请卡片时同时记下邀请码：新微信用户静默登录随 wx-login 带 inviteCode 直接进邀请家庭，
 * 登录后也按「待加入邀请」优先回到落地页。
 */
export function launchTarget(options?: { path?: string; query?: Record<string, unknown> } | null): string | null {
  if (!options?.path) return null
  const t = sanitizeRedirect(buildTarget(options.path, options.query))
  rememberPendingInvite(t)
  return t
}

/** 目标是家庭邀请落地页且带 code → 记为待加入邀请。 */
export function rememberPendingInvite(target: string | null | undefined): void {
  if (!target || !target.startsWith(INVITE_PAGE + '?')) return
  const m = /[?&]code=([^&]+)/.exec(target)
  if (!m) return
  let code = m[1]
  try {
    code = decodeURIComponent(code)
  } catch {}
  if (code) uni.setStorageSync(PENDING_INVITE_KEY, code)
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
 * 登录页 onLoad：等启动登录流程结束，已有登录态（老 token 或刚静默登录成功）就直接按 goAfterLogin 去向离开登录页。
 * 登录页是 pages.json 第一页：冷启动总是先落在这里，App.vue 有 token 时不做跳转，
 * 不处理的话已登录用户每次打开都要再点一次登录（2026-10-27 真机）。
 */
export async function resumeIfLoggedIn(raw?: unknown): Promise<string | null> {
  await launchAuth
  if (!uni.getStorageSync('token')) return null
  resetLoginRedirect()
  return goAfterLogin(raw)
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
