/**
 * 首次登录完善资料（mini-release-compliance A.7）。
 *
 * 微信静默登录新建的账号昵称是默认「微信用户」：不在登录流程里要求设置，
 * 改为「我的」页顶部提示可设置「头像和昵称」（用户点了才弹窗，可跳过）。
 * - 是否默认昵称：以登录接口返回的服务端昵称为准（存 storage `nickname`），换设备改过昵称就不再提示；
 * - 设置或跳过后记 PROFILE_PROMPTED_KEY，提示不再显示。
 */
import { validateNickname } from './validate'

export const DEFAULT_NICKNAME = '微信用户'
export const PROFILE_PROMPTED_KEY = 'gd_profile_setup_prompted'

export function isDefaultNickname(name: unknown): boolean {
  return typeof name === 'string' && name.trim() === DEFAULT_NICKNAME
}

/**
 * 「我的」页是否展示「设置头像和昵称」提示：已登录、昵称仍是默认、本机没设置/跳过过。
 * 注意：登录流程与各页面不再自动弹窗（审核要求不得强制头像昵称），仅「我的」页里提示，用户自愿点开。
 */
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
