import { describe, it, expect, beforeEach } from 'vitest'
import {
  DEFAULT_NICKNAME,
  PROFILE_PROMPTED_KEY,
  isDefaultNickname,
  markProfilePrompted,
  shouldPromptProfile,
  validateSetupNickname,
} from '@/utils/profileSetup'
import { __testUni } from '../setup'

// A.7 首次登录完善资料：默认昵称的已登录账号弹一次，跳过/完成后不再弹
describe('utils/profileSetup', () => {
  beforeEach(() => __testUni.resetStorage())

  it('默认昵称 + 已登录 + 本机没弹过 → 弹', () => {
    __testUni.setStorage('token', 't')
    __testUni.setStorage('nickname', DEFAULT_NICKNAME)
    expect(shouldPromptProfile()).toBe(true)
  })

  it('跳过/完成后（本机已记）不再弹', () => {
    __testUni.setStorage('token', 't')
    __testUni.setStorage('nickname', DEFAULT_NICKNAME)
    markProfilePrompted()
    expect(__testUni.getStorage(PROFILE_PROMPTED_KEY)).toBe('1')
    expect(shouldPromptProfile()).toBe(false)
  })

  it('未登录、或服务端昵称已改过（换设备）都不弹', () => {
    __testUni.setStorage('nickname', DEFAULT_NICKNAME)
    expect(shouldPromptProfile()).toBe(false)
    __testUni.setStorage('token', 't')
    __testUni.setStorage('nickname', '小张')
    expect(shouldPromptProfile()).toBe(false)
  })

  it('昵称校验：空 / 超长 / 仍为默认昵称都不行', () => {
    expect(isDefaultNickname(' 微信用户 ')).toBe(true)
    expect(validateSetupNickname('')).toBeTruthy()
    expect(validateSetupNickname('微信用户')).toContain('昵称')
    expect(validateSetupNickname('一'.repeat(21))).toBeTruthy()
    expect(validateSetupNickname('张妈妈')).toBe('')
  })
})
