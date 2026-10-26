/**
 * 协议同意（prototype-ui-rollout 1.16 · 合规）：
 * 用户在登录页勾选《用户协议》《隐私政策》前，不发起任何登录——包括 wx.login 静默登录
 * 与 401 静默自愈。同意记录带协议版本，协议改版后需重新勾选。
 */
export const CONSENT_KEY = 'legal_consent'
/** 协议版本（docs/legal 改版时同步更新，旧同意随之失效）。 */
export const LEGAL_VERSION = '2026-10-26'

export function hasConsent(): boolean {
  return uni.getStorageSync(CONSENT_KEY) === LEGAL_VERSION
}

export function setConsent(): void {
  uni.setStorageSync(CONSENT_KEY, LEGAL_VERSION)
}

export function clearConsent(): void {
  uni.removeStorageSync(CONSENT_KEY)
}

/**
 * 登录成功后上报协议同意（mini-release-compliance B.1）：POST /consent 记录当前 LEGAL_VERSION 的
 * 《用户协议》《隐私政策》同意。静默、失败不影响登录（下次登录会再报）。
 */
export async function reportConsent(): Promise<boolean> {
  if (!hasConsent()) return false
  try {
    const { request } = await import('./request')
    await request({
      url: '/consent',
      method: 'POST',
      data: { docVersion: LEGAL_VERSION, channel: 'mp-weixin' },
      silent: true,
    })
    return true
  } catch {
    return false
  }
}
