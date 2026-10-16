import { describe, it, expect } from 'vitest'
import { validateNickname, validateFeedback, agreementError, isUploadUrl, charLen } from '@/utils/validate'

describe('utils/validate 个人资料', () => {
  it('昵称 1–20 字（按字符计，去首尾空格）', () => {
    expect(validateNickname('')).toBe('昵称不能为空')
    expect(validateNickname('   ')).toBe('昵称不能为空')
    expect(validateNickname('掌勺人')).toBe('')
    expect(validateNickname('一'.repeat(20))).toBe('')
    expect(validateNickname('一'.repeat(21))).toBe('昵称最多 20 个字')
    expect(charLen('😀😀')).toBe(2)
  })

  it('isUploadUrl', () => {
    expect(isUploadUrl('/gudu/uploads/original/a.jpg')).toBe(true)
    expect(isUploadUrl('https://evil.com/a.jpg')).toBe(false)
    expect(isUploadUrl(null)).toBe(false)
  })
})

describe('utils/validate 意见反馈', () => {
  it('内容 5–500 字', () => {
    expect(validateFeedback({ content: '太慢' })).toBe('请至少写 5 个字')
    expect(validateFeedback({ content: '    很好    ' })).toBe('请至少写 5 个字')
    expect(validateFeedback({ content: '搜索有点慢了' })).toBe('')
    expect(validateFeedback({ content: '字'.repeat(500) })).toBe('')
    expect(validateFeedback({ content: '字'.repeat(501) })).toBe('最多 500 个字')
  })

  it('联系方式 ≤ 50、图片 ≤ 3', () => {
    expect(validateFeedback({ content: '搜索有点慢了', contact: 'a'.repeat(51) })).toBe('联系方式最多 50 个字')
    expect(validateFeedback({ content: '搜索有点慢了', images: ['1', '2', '3'] })).toBe('')
    expect(validateFeedback({ content: '搜索有点慢了', images: ['1', '2', '3', '4'] })).toBe('最多 3 张图片')
  })
})

describe('utils/validate 登录协议', () => {
  it('未勾选返回提示，勾选通过', () => {
    expect(agreementError(false)).toContain('用户协议')
    expect(agreementError(false)).toContain('隐私政策')
    expect(agreementError(true)).toBe('')
  })
})
