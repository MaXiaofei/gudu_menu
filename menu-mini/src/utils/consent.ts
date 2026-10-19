/**
 * 协议同意（prototype-ui-rollout 1.16 · 合规）：
 * 用户在登录页勾选《用户协议》《隐私政策》前，不发起任何登录——包括 wx.login 静默登录
 * 与 401 静默自愈。同意记录带协议版本，协议改版后需重新勾选。
 */
export const CONSENT_KEY = 'legal_consent'
/** 协议版本（docs/legal 改版时同步更新，旧同意随之失效）。 */
export const LEGAL_VERSION = '2026-10'

export function hasConsent(): boolean {
  return uni.getStorageSync(CONSENT_KEY) === LEGAL_VERSION
}

export function setConsent(): void {
  uni.setStorageSync(CONSENT_KEY, LEGAL_VERSION)
}

export function clearConsent(): void {
  uni.removeStorageSync(CONSENT_KEY)
}
