// API 基址：统一从 config/env.ts 取（环境切换唯一入口，改那里的 ENV 即可全站生效）。
// 本地 H5 调试可临时改 '/gudu' 走 vite proxy。
export { BASE } from '@/config/env'
import { BASE as BASE_URL } from '@/config/env'
import { hasConsent } from './consent'
import { contentSecurityMessage, type ContentScene } from './contentSecurity'

/** 默认请求超时（毫秒），调用方可通过 opt.timeout 覆盖。 */
export const REQUEST_TIMEOUT = 15000
export const MSG_NETWORK = '网络异常，请检查网络'
export const MSG_TOO_FREQUENT = '操作太频繁，请稍后再试'


/**
 * 报错编号（prototype-ui-rollout 三态规范）：后端每个请求回 X-Trace-Id，
 * 请求失败时记下来，错误态与意见反馈展示/附带「7f3a-91c2」形式的短编号。
 */
let lastTraceId = ''

function traceOf(res: any): string {
  const h = res?.header || {}
  return String(h['X-Trace-Id'] || h['x-trace-id'] || h['X-TRACE-ID'] || '')
}

/** 完整 traceId → 短编号（前 8 位，4-4 分组）。 */
export function formatErrorId(traceId?: string | null): string {
  const t = (traceId || '').replace(/[^0-9a-zA-Z]/g, '').toLowerCase().slice(0, 8)
  return t.length >= 8 ? `${t.slice(0, 4)}-${t.slice(4, 8)}` : t
}

/** 最近一次失败请求的短报错编号（无则空串）。 */
export function lastErrorId(): string {
  return formatErrorId(lastTraceId)
}

/** 业务错误：携带后端 R.code / R.data（如 428 CONSENT_REQUIRED 的 docTypes），调用方可按码处理。 */
export class ApiError extends Error {
  code: number
  data: any
  constructor(code: number, msg: string, data?: any) {
    super(msg)
    this.name = 'ApiError'
    this.code = code
    this.data = data ?? null
  }
}

/** 缺少敏感信息单独同意（B.2）：由调用方弹同意窗处理，不 toast。 */
export const CODE_CONSENT_REQUIRED = 428
/** 账号已被平台封禁（F.1）：登录与写接口统一返回，弹窗告知原因并回到登录页。 */
export const CODE_ACCOUNT_BANNED = 423

export function getToken(): string {
  return uni.getStorageSync('token') || ''
}

/**
 * 401 静默自愈（V50）：token 失效时用 wx.login 重新静默登录一次并重放原请求；
 * 重登也失败（无配置/无网络）才踢登录页。并发 401 共享同一次重登。
 */
let reloginPromise: Promise<boolean> | null = null

async function trySilentRelogin(): Promise<boolean> {
  if (!hasConsent()) return false // 未同意协议：不发起 wx.login
  if (!reloginPromise) {
    reloginPromise = (async () => {
      try {
        const code: string = await new Promise((resolve, reject) =>
          uni.login({ success: (r: any) => resolve(r.code), fail: (e: any) => reject(e) }),
        )
        const res: any = await uni.request({
          url: BASE_URL + '/auth/wx-login',
          method: 'POST',
          data: { code },
          header: { Authorization: '' },
          timeout: REQUEST_TIMEOUT,
        })
        const body = res.data
        if (body.code !== 0 || !body.data?.token) return false
        uni.setStorageSync('token', body.data.token)
        uni.setStorageSync('nickname', body.data.nickname || '')
        return true
      } catch {
        return false
      } finally {
        // 下次 401 可再尝试
        setTimeout(() => (reloginPromise = null), 100)
      }
    })()
  }
  return reloginPromise
}

/**
 * 清理请求参数：删除 undefined / null / 空字符串。
 * 微信小程序端 uni.request 会把 undefined 序列化成字符串 "undefined" 发给后端，
 * 导致 List<Long> 等强类型参数转换失败（如 dish/search 的 tagIds）。
 */
function cleanData(data: unknown): unknown {
  if (!data || typeof data !== 'object') return data
  const out: Record<string, unknown> = {}
  for (const [k, v] of Object.entries(data as Record<string, unknown>)) {
    if (v !== undefined && v !== null && v !== '') out[k] = v
  }
  return out
}

export async function request<T = any>(
  opt: UniApp.RequestOptions & {
    guestKey?: string
    _retried?: boolean
    /** 失败不弹 toast（可降级的探测性接口用） */
    silent?: boolean
    /** 内容安全拒绝（451/452）时按此场景给友好提示 */
    contentScene?: ContentScene
  },
): Promise<T> {
  const toast = (title: string) => {
    if (!opt.silent) uni.showToast({ title, icon: 'none' })
  }
  let res: any
  try {
    res = await uni.request({
      timeout: REQUEST_TIMEOUT,
      ...opt,
      data: cleanData(opt.data) as UniApp.RequestOptions['data'],
      url: BASE_URL + opt.url,
      header: {
        Authorization: getToken(),
        // 朋友点菜免登录凭证（对应 H5 的 X-Guest-Key）
        ...(opt.guestKey ? { 'X-Guest-Key': opt.guestKey } : {}),
        ...opt.header,
      },
    })
  } catch {
    // 断网 / 超时 / DNS 失败：uni.request 直接 reject
    lastTraceId = ''
    toast(MSG_NETWORK)
    throw new Error(MSG_NETWORK)
  }
  const traceId = traceOf(res)
  const body = res?.data // 后端统一 R{code,msg,data}（限流/权限也走 HTTP 200 + R.code）
  if (!body || typeof body !== 'object' || body.code !== 0) lastTraceId = traceId
  if (!body || typeof body !== 'object') {
    // 网关层返回的非 R 响应（如 nginx 429/502 HTML 页）
    const msg = res?.statusCode === 429 ? MSG_TOO_FREQUENT : MSG_NETWORK
    toast(msg)
    throw new Error(msg)
  }
  if (body.code === 401) {
    if (opt.guestKey) {
      // 访客凭证失效：不跳登录，直接抛错由页面提示重新打开邀请
      throw new Error(body.msg || '凭证失效，请重新打开邀请链接')
    }
    // 静默自愈仅限微信登录用户：账号密码用户 401 直接回登录页（防静默串号到微信新号）
    const isWxUser = uni.getStorageSync('login_via') === 'wx'
    const recovered =
      isWxUser && opt.url !== '/auth/wx-login' ? await trySilentRelogin() : false
    if (recovered && !opt._retried) {
      return request<T>({ ...opt, _retried: true }) // 新 token 重放一次
    }
    uni.removeStorageSync('token')
    uni.reLaunch({ url: '/pages/login/Login' })
    throw new Error('未登录')
  }
  if (body.code === CODE_ACCOUNT_BANNED) {
    const msg = body.msg || '账号已被封禁'
    uni.removeStorageSync('token')
    uni.showModal({ title: '账号不可用', content: msg, showCancel: false })
    const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
    const route = pages.length ? (pages[pages.length - 1] as any).route || '' : ''
    if (!String(route).includes('pages/login/Login')) uni.reLaunch({ url: '/pages/login/Login' })
    throw new ApiError(body.code, msg, body.data)
  }
  if (body.code === 429) {
    toast(MSG_TOO_FREQUENT)
    throw new Error(body.msg || MSG_TOO_FREQUENT)
  }
  if (body.code === 403) {
    // 无权限：展示服务端原因（如「无权操作该家庭数据」），不清 token
    const msg = body.msg || '无权限访问'
    toast(msg)
    throw new Error(msg)
  }
  const csMsg = contentSecurityMessage(body.code, opt.contentScene)
  if (csMsg) {
    // 内容安全拒绝：前端文案，不展示后端原文
    toast(csMsg)
    throw new Error(csMsg)
  }
  if (body.code === CODE_CONSENT_REQUIRED) {
    throw new ApiError(body.code, body.msg || '需要您的单独同意', body.data)
  }
  if (body.code !== 0) {
    toast(body.msg || '请求失败')
    throw new ApiError(body.code, body.msg, body.data)
  }
  return body.data as T
}
