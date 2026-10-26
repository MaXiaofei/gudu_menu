import { request } from '@/utils/request'
import { hasConsent } from '@/utils/consent'
import { applyLoginResult, PENDING_INVITE_KEY, type LoginResult } from '@/utils/family'

/**
 * 微信登录：POST /auth/wx-login {code, inviteCode?} → {token, nickname, accountId, families, currentFamilyId…}。
 * 新 openid 自动建号：带有效 inviteCode 直接加入邀请家庭，否则后端自动建「我的家」。
 */
export function wxLogin(code: string, inviteCode?: string): Promise<LoginResult> {
  return request({
    url: '/auth/wx-login',
    method: 'POST',
    data: inviteCode ? { code, inviteCode } : { code },
  })
}

/** wx.login 拿一次性 code（静默，无需用户授权）。 */
export function wxCode(): Promise<string> {
  return new Promise((resolve, reject) => {
    uni.login({
      success: (r) => resolve(r.code),
      fail: (e) => reject(new Error(e?.errMsg || 'wx.login 失败')),
    })
  })
}

/**
 * 静默登录：wx.login → wx-login → 存 token + 家庭上下文。新用户零操作。
 * 邀请落地页记下的邀请码（PENDING_INVITE_KEY）随登录带上：新用户直接进邀请家庭。
 */
export async function silentLogin(): Promise<LoginResult | null> {
  if (!hasConsent()) return null // 合规：未勾选协议不调用 wx.login
  try {
    const code = await wxCode()
    const r = await wxLogin(code, uni.getStorageSync(PENDING_INVITE_KEY) || undefined)
    applyLoginResult(r)
    uni.setStorageSync('login_via', 'wx') // 401 自愈只对微信登录用户启用
    return r
  } catch {
    return null
  }
}
