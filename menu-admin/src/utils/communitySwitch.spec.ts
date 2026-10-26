import { describe, it, expect } from 'vitest'
import { confirmCopy, lastOperationText, switchStatusText, validateSwitchReason } from './communitySwitch'

describe('厨房圈紧急关停开关（A.12）', () => {
  it('状态文案', () => {
    expect(switchStatusText(true)).toBe('厨房圈运行中')
    expect(switchStatusText(false)).toBe('厨房圈已暂停')
  })

  it('最后操作人 + 时间；从未操作过有默认文案', () => {
    expect(lastOperationText({ updatedBy: 7, updatedByName: '运营小王', updatedAt: '2026-10-26T18:00:31' })).toBe(
      '最后操作：运营小王 · 2026-10-26 18:00',
    )
    expect(lastOperationText({ updatedBy: 7, updatedByName: null, updatedAt: null })).toBe('最后操作：用户 #7 · —')
    expect(lastOperationText({ updatedBy: null, updatedByName: null, updatedAt: null })).toBe('默认开启，尚未操作过')
    expect(lastOperationText(null)).toBe('默认开启，尚未操作过')
  })

  it('二次确认文案区分关停与恢复，关停说明审核后台不受影响', () => {
    expect(confirmCopy(false).confirm).toBe('确认关停')
    expect(confirmCopy(false).message).toContain('审核后台不受影响')
    expect(confirmCopy(true).confirm).toBe('确认恢复')
  })

  it('关停必须写原因，恢复可不写，超长不行', () => {
    expect(validateSwitchReason(false, ' ')).toBe('请填写关停原因')
    expect(validateSwitchReason(false, '出现违规图片')).toBe('')
    expect(validateSwitchReason(true, '')).toBe('')
    expect(validateSwitchReason(true, 'x'.repeat(201))).toContain('200')
  })
})
