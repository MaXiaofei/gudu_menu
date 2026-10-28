import { describe, expect, it } from 'vitest'
import { chooseImageFailHint } from '@/utils/chooseImage'

describe('chooseImageFailHint', () => {
  it('用户主动取消不提示', () => {
    expect(chooseImageFailHint('chooseMedia:fail cancel')).toBeNull()
    expect(chooseImageFailHint('chooseImage:fail cancel')).toBeNull()
  })

  it('隐私授权被拒 / 未声明：提示先同意隐私授权', () => {
    expect(chooseImageFailHint('chooseMedia:fail privacy permission is not authorized')).toBe('需要先同意隐私授权才能选择图片')
    expect(chooseImageFailHint('chooseImage:fail api scope is not declared in the privacy agreement')).toBe('需要先同意隐私授权才能选择图片')
  })

  it('相机 / 相册系统权限被拒：提示去设置打开', () => {
    expect(chooseImageFailHint('chooseMedia:fail auth deny')).toBe('没有相机或相册权限，请在设置里打开后重试')
  })

  it('其他失败：通用提示', () => {
    expect(chooseImageFailHint('chooseMedia:fail unknown')).toBe('没能选择图片，请重试')
    expect(chooseImageFailHint(undefined)).toBe('没能选择图片，请重试')
  })
})
