import { describe, it, expect } from 'vitest'
import { chooseAvatarFailHint } from '@/utils/chooseAvatar'

describe('chooseAvatarFailHint', () => {
  it('拿到头像：不提示', () => {
    expect(chooseAvatarFailHint({ avatarUrl: 'http://tmp/a.jpg', errMsg: 'chooseAvatar:ok' })).toBeNull()
  })

  it('用户取消：不提示', () => {
    expect(chooseAvatarFailHint({ errMsg: 'chooseAvatar:fail cancel' })).toBeNull()
  })

  it('隐私指引未声明 / 隐私授权被拒：提示隐私授权', () => {
    expect(chooseAvatarFailHint({ errMsg: 'chooseAvatar:fail api scope is not declared in the privacy agreement' }))
      .toBe('需要先同意隐私授权才能使用微信头像')
    expect(chooseAvatarFailHint({ errMsg: 'chooseAvatar:fail privacy permission is not authorized' }))
      .toBe('需要先同意隐私授权才能使用微信头像')
  })

  it('其他失败或没有 detail：通用提示，不再静默', () => {
    expect(chooseAvatarFailHint({ errMsg: 'chooseAvatar:fail' })).toBe('没有拿到头像，请重试')
    expect(chooseAvatarFailHint(undefined)).toBe('没有拿到头像，请重试')
  })
})
