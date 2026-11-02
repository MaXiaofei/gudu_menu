import { describe, it, expect } from 'vitest'
import type { AiGuardOverview } from '@/api/aiGuard'
import {
  buildUpdate,
  formFromOverview,
  levelColor,
  levelText,
  limitText,
  logKeyName,
  logText,
  validateForm,
  yuan,
} from './aiGuard'

const overview = (over: Partial<AiGuardOverview> = {}): AiGuardOverview => ({
  spentYuan: 5,
  limitYuan: 20,
  percent: 25,
  level: 'NORMAL',
  warnPercent: 80,
  countsAvailable: true,
  logs: [],
  categories: [
    { key: 'vision', label: '拍照识别', todayCount: 3, limitPerAccount: 20 },
    { key: 'mealplan', label: '一周餐程', todayCount: 1, limitPerAccount: 10 },
    { key: 'agent', label: '咕嘟对话', todayCount: 9, limitPerAccount: 50 },
    { key: 'other', label: '其他 AI', todayCount: 0, limitPerAccount: 50 },
  ],
  ...over,
})

describe('utils/aiGuard', () => {
  it('状态文案：正常 / 提醒 / 已暂停', () => {
    expect(levelText('NORMAL', 10)).toBe('运行正常')
    expect(levelText('WARN', 85)).toContain('85%')
    expect(levelText('PAUSED', 100)).toContain('已暂停')
    expect(levelColor('PAUSED')).toBe('var(--yh-error)')
    expect(levelColor('WARN')).toBe('var(--yh-warning)')
    expect(levelColor('NORMAL')).toBe('var(--yh-success)')
  })

  it('金额与上限展示：0 = 不限', () => {
    expect(yuan(12.3)).toBe('¥12.30')
    expect(limitText(0, '次')).toBe('不限')
    expect(limitText(20, '次')).toBe('20 次')
  })

  it('表单从概览还原', () => {
    expect(formFromOverview(overview())).toEqual({
      dailyBudgetYuan: 20,
      warnPercent: 80,
      limits: { vision: 20, mealplan: 10, agent: 50, other: 50 },
    })
  })

  it('校验：预算 / 比例 / 次数范围', () => {
    const ok = formFromOverview(overview())
    expect(validateForm(ok)).toBeNull()
    expect(validateForm({ ...ok, dailyBudgetYuan: -1 })).toContain('每日花费上限')
    expect(validateForm({ ...ok, warnPercent: 0 })).toContain('提醒比例')
    expect(validateForm({ ...ok, warnPercent: 101 })).toContain('提醒比例')
    expect(validateForm({ ...ok, limits: { ...ok.limits, vision: -3 } })).toContain('拍照识别')
    expect(validateForm({ ...ok, limits: { ...ok.limits, mealplan: 1.5 } })).toContain('一周餐程')
    expect(validateForm({ ...ok, dailyBudgetYuan: 0 })).toBeNull()
  })

  it('只提交改过的字段，没改返回 null', () => {
    const before = formFromOverview(overview())
    expect(buildUpdate(before, { ...before, limits: { ...before.limits } })).toBeNull()
    expect(buildUpdate(before, { ...before, dailyBudgetYuan: 30, limits: { ...before.limits, vision: 25 } })).toEqual({
      dailyBudgetYuan: 30,
      limits: { vision: 25 },
    })
    expect(buildUpdate(before, { ...before, warnPercent: 70, limits: { ...before.limits } })).toEqual({ warnPercent: 70 })
  })

  it('修改日志文案含中文名与新旧值', () => {
    expect(logKeyName('limit.mealplan')).toBe('一周餐程次数上限')
    expect(logKeyName('budget.daily_yuan')).toBe('每日花费上限（元）')
    expect(
      logText({ key: 'budget.daily_yuan', oldValue: '20', newValue: '30', operatorId: 1, operatorName: '小王', at: '2026-11-02T10:00:00' }),
    ).toBe('每日花费上限（元）：20 → 30')
    expect(
      logText({ key: 'limit.vision', oldValue: null, newValue: '5', operatorId: 1, operatorName: null, at: '2026-11-02T10:00:00' }),
    ).toBe('拍照识别次数上限：默认 → 5')
  })
})
