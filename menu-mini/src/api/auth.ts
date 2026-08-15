import { request } from '@/utils/request'

/** 微信登录：POST /auth/wx-login {code} → {token, nickname}（新 openid 自动建号）。 */
export function wxLogin(code: string): Promise<{ token: string; nickname: string }> {
  return request({
    url: '/auth/wx-login',
    method: 'POST',
    data: { code },
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

/** 静默登录：wx.login → wx-login → 存 token。新用户零操作。 */
export async function silentLogin(): Promise<{ token: string; nickname: string } | null> {
  try {
    const code = await wxCode()
    const r = await wxLogin(code)
    uni.setStorageSync('token', r.token)
    uni.setStorageSync('nickname', r.nickname)
    uni.setStorageSync('login_via', 'wx') // 401 自愈只对微信登录用户启用
    uni.removeStorageSync('logged_out')
    return r
  } catch {
    return null
  }
}
