import { describe, it, expect } from 'vitest'
import {
  buildMatrix,
  featureConfirmCopy,
  featureStatusText,
  lastOperationText,
  platformBatchCopy,
  recentChangeRows,
  switchName,
  validateSwitchReason,
} from './featureSwitch'
import type { FeatureCategory, FeaturePlatform, FeatureSwitchState } from '@/api/featureSwitch'

function st(category: FeatureCategory, platform: FeaturePlatform, label: string, over: Partial<FeatureSwitchState> = {}): FeatureSwitchState {
  return {
    key: `x.${platform}` as any,
    category,
    label,
    platform,
    enabled: true,
    updatedBy: null,
    updatedByName: null,
    updatedAt: null,
    reason: null,
    recentChanges: [],
    ...over,
  }
}

const ALL: FeatureSwitchState[] = [
  st('mealPlan', 'app', '一周餐程'),
  st('agentChat', 'mp', '咕嘟助手'),
  st('vision', 'mp', '拍照识别'),
  st('agentChat', 'app', '咕嘟助手'),
  st('cookAsk', 'mp', '问咕嘟'),
  st('cookAsk', 'app', '问咕嘟'),
  st('vision', 'app', '拍照识别'),
  st('mealPlan', 'mp', '一周餐程'),
  st('estimate', 'mp', '营养估算'),
  st('postText', 'app', 'AI 帮写'),
  st('estimate', 'app', '营养估算'),
  st('postText', 'mp', 'AI 帮写'),
]

describe('生成式 AI 紧急关停开关文案', () => {
  it('状态文案带名称', () => {
    expect(featureStatusText('咕嘟助手', true)).toBe('咕嘟助手运行中')
    expect(featureStatusText('问咕嘟', false)).toBe('问咕嘟已暂停')
    expect(switchName('mp', '拍照识别')).toBe('小程序拍照识别')
  })

  it('二次确认文案区分关停与恢复，关停说明影响范围', () => {
    const off = featureConfirmCopy('小程序咕嘟助手', false, '咕嘟助手')
    expect(off.title).toBe('紧急关停小程序咕嘟助手')
    expect(off.confirm).toBe('确认关停')
    expect(off.message).toContain('历史仍可查看')
    expect(featureConfirmCopy('App问咕嘟', false, '问咕嘟').message).toContain('跟做模式其余功能不受影响')
    expect(featureConfirmCopy('小程序拍照识别', false, '拍照识别').message).toContain('已有识别结果仍可查看')
    expect(featureConfirmCopy('小程序一周餐程', false, '一周餐程').message).toContain('已生成的餐程仍可查看')
    // 与后端行为一致：对话里的排一周也暂停、首页不再给一周快捷提问
    expect(featureConfirmCopy('小程序一周餐程', false, '一周餐程').message).toContain('咕嘟对话里的「排一周」同样暂停')
    expect(featureConfirmCopy('小程序咕嘟助手', false, '咕嘟助手').message).toContain('跟做模式里的提问同时暂停')
    expect(featureConfirmCopy('App问咕嘟', true, '问咕嘟').confirm).toBe('确认恢复')
  })

  it('关停必须写原因；最后操作文案复用', () => {
    expect(validateSwitchReason(false, ' ')).toBe('请填写关停原因')
    expect(validateSwitchReason(true, '')).toBe('')
    expect(lastOperationText(null)).toBe('默认开启，尚未操作过')
  })

  it('一键操作文案', () => {
    expect(platformBatchCopy('mp', false).title).toBe('小程序一键关闭全部 AI')
    expect(platformBatchCopy('mp', false).message).toContain('拍照识别')
    expect(platformBatchCopy('mp', true).confirm).toBe('确认全部恢复')
  })
})

describe('开关矩阵与最近记录', () => {
  it('乱序扁平列表 → 行固定为 6 类，列为小程序 / App', () => {
    const rows = buildMatrix(ALL)
    expect(rows.map((r) => r.category)).toEqual(['agentChat', 'cookAsk', 'vision', 'mealPlan', 'estimate', 'postText'])
    expect(rows.every((r) => r.mp?.platform === 'mp' && r.app?.platform === 'app')).toBe(true)
    expect(rows[2].label).toBe('拍照识别')
  })

  it('缺一端也保留该行；两端都没有则丢弃', () => {
    const rows = buildMatrix([st('vision', 'mp', '拍照识别')])
    expect(rows).toHaveLength(1)
    expect(rows[0].app).toBeNull()
  })

  it('最近修改记录带类别与端，按时间倒序', () => {
    const ch = (at: string) => ({ enabled: false, updatedBy: 1, updatedByName: '运营', updatedAt: at, reason: 'r' })
    const rows = recentChangeRows([
      st('vision', 'mp', '拍照识别', { recentChanges: [ch('2026-10-30T10:00:00')] }),
      st('mealPlan', 'app', '一周餐程', { recentChanges: [ch('2026-10-31T09:00:00')] }),
    ])
    expect(rows.map((r) => `${r.platform}:${r.label}`)).toEqual(['app:一周餐程', 'mp:拍照识别'])
    expect(recentChangeRows(ALL)).toEqual([])
  })
})
