/**
 * 首次登录完善资料（mini-release-compliance A.7）。
 *
 * 微信静默登录新建的账号昵称是默认「微信用户」：登录后弹一次「设置头像和昵称」，可跳过。
 * - 是否默认昵称：以登录接口返回的服务端昵称为准（存 storage `nickname`），换设备改过昵称就不再弹；
 * - 本机只弹一次：跳过或完成后记 PROFILE_PROMPTED_KEY。
 */
import { validateNickname } from './validate'

export const DEFAULT_NICKNAME = '微信用户'
export const PROFILE_PROMPTED_KEY = 'gd_profile_setup_prompted'

export function isDefaultNickname(name: unknown): boolean {
  return typeof name === 'string' && name.trim() === DEFAULT_NICKNAME
}

/** 已登录、昵称仍是默认、本机没弹过 → 需要弹。 */
export function shouldPromptProfile(): boolean {
  if (!uni.getStorageSync('token')) return false
  if (uni.getStorageSync(PROFILE_PROMPTED_KEY)) return false
  return isDefaultNickname(uni.getStorageSync('nickname'))
}

export function markProfilePrompted(): void {
  uni.setStorageSync(PROFILE_PROMPTED_KEY, '1')
}

/** 弹窗里的昵称校验：通用规则 + 不能还是默认昵称。 */
export function validateSetupNickname(name: string): string {
  return validateNickname(name) || (isDefaultNickname(name) ? '换一个专属昵称吧' : '')
}
