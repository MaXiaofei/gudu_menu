/**
 * 协议同意（prototype-ui-rollout 1.16 · 合规）：
 * 用户在登录页勾选《用户协议》《隐私政策》前，不发起任何登录——包括 wx.login 静默登录
 * 与 401 静默自愈。同意记录带协议版本，协议改版后需重新勾选。
 */
import { anyAiEnabled } from './featureSwitch'
import { bridgedRequest } from './requestBridge'

export const CONSENT_KEY = 'legal_consent'
/** 协议版本（docs/legal 改版时同步更新，旧同意随之失效）。 */
export const LEGAL_VERSION = '2026-10-26'

/** 无 AI 版协议的版本后缀：同意记录里区分用户当时读到的是哪套文本。 */
export const NOAI_SUFFIX = '-noai'

/** 上报用的协议版本：本端四类 AI 全关（读到的是无 AI 版）→ 加 -noai 后缀；本地 hasConsent 仍只认 LEGAL_VERSION。 */
export function reportedDocVersion(): string {
  return anyAiEnabled() ? LEGAL_VERSION : LEGAL_VERSION + NOAI_SUFFIX
}

/** 已成功上报的协议版本（与 CONSENT_KEY 分开存：本地同意不等于服务端已记录）。 */
export const REPORTED_KEY = 'legal_consent_reported'

export function hasConsent(): boolean {
  return uni.getStorageSync(CONSENT_KEY) === LEGAL_VERSION
}

export function setConsent(): void {
  uni.setStorageSync(CONSENT_KEY, LEGAL_VERSION)
}

export function clearConsent(): void {
  uni.removeStorageSync(CONSENT_KEY)
  uni.removeStorageSync(REPORTED_KEY)
}

/**
 * 登录成功后上报协议同意（mini-release-compliance B.1）：POST /consent 记录当前 LEGAL_VERSION 的
 * 《用户协议》《隐私政策》同意。静默、失败不影响登录（下次登录会再报）。
 */
export async function reportConsent(): Promise<boolean> {
  if (!hasConsent()) return false
  try {
    await bridgedRequest({
      url: '/consent',
      method: 'POST',
      data: { docVersion: reportedDocVersion(), channel: 'mp-weixin' },
      silent: true,
    })
    uni.setStorageSync(REPORTED_KEY, LEGAL_VERSION)
    return true
  } catch {
    return false
  }
}

/**
 * 补报：本地已同意当前版本、已登录，但服务端还没记录成功（旧版本动态导入失效导致一直上报失败，
 * 或登录时网络不通）时，启动后补报一次；成功后落标记，不再重复。
 */
export async function reportConsentIfPending(): Promise<boolean> {
  if (!hasConsent() || !uni.getStorageSync('token')) return false
  if (uni.getStorageSync(REPORTED_KEY) === LEGAL_VERSION) return false
  return reportConsent()
}
