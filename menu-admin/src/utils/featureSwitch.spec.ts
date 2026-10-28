import { describe, it, expect } from 'vitest'
import { featureConfirmCopy, featureStatusText, lastOperationText, validateSwitchReason } from './featureSwitch'

describe('咕嘟助手 / 问咕嘟紧急关停开关文案', () => {
  it('状态文案带名称', () => {
    expect(featureStatusText('咕嘟助手', true)).toBe('咕嘟助手运行中')
    expect(featureStatusText('问咕嘟', false)).toBe('问咕嘟已暂停')
  })

  it('二次确认文案区分关停与恢复，关停说明影响范围', () => {
    const off = featureConfirmCopy('咕嘟助手', false)
    expect(off.title).toBe('紧急关停咕嘟助手')
    expect(off.confirm).toBe('确认关停')
    expect(off.message).toContain('历史仍可查看')
    expect(featureConfirmCopy('问咕嘟', false).message).toContain('跟做模式其余功能不受影响')
    expect(featureConfirmCopy('问咕嘟', true).confirm).toBe('确认恢复')
  })

  it('关停必须写原因；最后操作文案复用', () => {
    expect(validateSwitchReason(false, ' ')).toBe('请填写关停原因')
    expect(validateSwitchReason(true, '')).toBe('')
    expect(lastOperationText(null)).toBe('默认开启，尚未操作过')
  })
})
