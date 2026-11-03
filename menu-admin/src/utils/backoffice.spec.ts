import { describe, expect, it } from 'vitest'
import {
  ACCOUNT_STATUS, cleanAccountQuery, cleanOpLogQuery, operatorIdError, daysLeft, deactivationOverdue, label, mediaActions, reasonError, reviewLabel,
} from './backoffice'

describe('utils/backoffice 运营后台展示规则', () => {
  it('文案映射：未知值原样、空值占位', () => {
    expect(label(ACCOUNT_STATUS, 'DEACTIVATED')).toBe('已注销')
    expect(label(ACCOUNT_STATUS, 'X')).toBe('X')
    expect(label(ACCOUNT_STATUS, null)).toBe('—')
  })

  it('图片复核：待复核可恢复可确认；已确认只能改判恢复；已恢复不可再操作', () => {
    expect(reviewLabel(null)).toBe('待复核')
    expect(mediaActions(null)).toEqual({ restore: true, confirm: true })
    expect(mediaActions('CONFIRMED')).toEqual({ restore: true, confirm: false })
    expect(reviewLabel('RESTORED')).toBe('已恢复（误判）')
    expect(mediaActions('RESTORED')).toEqual({ restore: false, confirm: false })
  })

  it('注销超期：已完成不高亮；后端 overdue 优先；兜底按截止时间', () => {
    const now = new Date('2026-11-17T09:00:00')
    expect(deactivationOverdue({ status: 'DONE', overdue: true }, now)).toBe(false)
    expect(deactivationOverdue({ status: 'FAILED', overdue: true }, now)).toBe(true)
    expect(deactivationOverdue({ status: 'PENDING', deadline: '2026-11-16T23:59:59' }, now)).toBe(true)
    expect(deactivationOverdue({ status: 'PENDING', deadline: '2026-11-20T23:59:59' }, now)).toBe(false)
    expect(daysLeft('2026-11-20T23:59:59', now)).toBe(4)
    expect(daysLeft('2026-11-16T23:59:59', now)).toBeLessThanOrEqual(0)
    expect(daysLeft(null, now)).toBeNull()
  })

  it('账号查询参数：去空、openid 尾号≥4 位、家庭 ID 正整数', () => {
    expect(cleanAccountQuery({ keyword: '  小王 ', openidTail: 'abc', familyId: '0', bannedOnly: false })).toEqual({ keyword: '小王' })
    expect(cleanAccountQuery({ openidTail: 'a1b2', familyId: '8', status: 'ACTIVE', bannedOnly: true }))
      .toEqual({ openidTail: 'a1b2', familyId: 8, status: 'ACTIVE', bannedOnly: true })
  })

  it('原因必填且不超 200 字', () => {
    expect(reasonError(' ')).toBe('请填写原因')
    expect(reasonError('x'.repeat(201))).toBe('原因不超过 200 字')
    expect(reasonError('刷广告')).toBeNull()
  })

  it('操作日志查询参数：操作人正整数、日期区间自动排序、单边只传 from', () => {
    expect(cleanOpLogQuery({ targetType: '', operatorId: ' ', range: null })).toEqual({})
    expect(cleanOpLogQuery({ targetType: 'ACCOUNT', operatorId: '12', range: ['2026-11-02', '2026-10-01'] }))
      .toEqual({ targetType: 'ACCOUNT', operatorId: 12, from: '2026-10-01', to: '2026-11-02' })
    expect(cleanOpLogQuery({ operatorId: 'abc' })).toEqual({})
    expect(cleanOpLogQuery({ range: ['2026-10-01', ''] })).toEqual({ from: '2026-10-01' })
  })

  it('操作人输入校验', () => {
    expect(operatorIdError('')).toBeNull()
    expect(operatorIdError('15')).toBeNull()
    expect(operatorIdError('0')).not.toBeNull()
    expect(operatorIdError('张三')).not.toBeNull()
  })
})
