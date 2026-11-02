import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import {
  buildMatrix,
  CATEGORY_ORDER,
  featureConfirmCopy,
  featureStatusText,
  lastOperationText,
  platformBatchCopy,
  switchName,
} from './featureSwitch'
import type { FeatureCategory, FeaturePlatform, FeatureSwitchState } from '@/api/featureSwitch'

// 开关矩阵页：6 类 × 2 端，每端 64 种组合、两端共 4096 种开关状态在矩阵里逐格还原、互不串扰（穷举）；一键操作只作用于所选端。
const LABEL: Record<FeatureCategory, string> = {
  agentChat: '咕嘟助手',
  cookAsk: '问咕嘟',
  vision: '拍照识别',
  mealPlan: '一周餐程',
  estimate: '营养估算',
  postText: 'AI 帮写',
}
const state = (category: FeatureCategory, platform: FeaturePlatform, enabled: boolean): FeatureSwitchState => ({
  key: `${category}.${platform}` as any,
  category,
  label: LABEL[category],
  platform,
  enabled,
  updatedBy: null,
  updatedByName: null,
  updatedAt: null,
  reason: null,
  recentChanges: [],
})

describe('开关矩阵：两端各 64 种组合共 4096 种逐格还原', () => {
  it('每一格的开 / 关都落在正确的类别与端上', () => {
    for (let mp = 0; mp < 64; mp++) {
      for (let app = 0; app < 64; app++) {
        const list: FeatureSwitchState[] = []
        CATEGORY_ORDER.forEach((c, i) => {
          list.push(state(c, 'mp', (mp & (1 << i)) !== 0))
          list.push(state(c, 'app', (app & (1 << i)) !== 0))
        })
        const rows = buildMatrix(list)
        expect(rows.map((r) => r.category)).toEqual(CATEGORY_ORDER)
        rows.forEach((r, i) => {
          expect(r.mp?.enabled, `mp=${mp} app=${app} ${r.category}`).toBe((mp & (1 << i)) !== 0)
          expect(r.app?.enabled, `mp=${mp} app=${app} ${r.category}`).toBe((app & (1 << i)) !== 0)
          expect(r.mp?.platform).toBe('mp')
          expect(r.app?.platform).toBe('app')
        })
      }
    }
  })

  it('状态文案与开关名按端区分', () => {
    for (const c of CATEGORY_ORDER) {
      expect(featureStatusText(LABEL[c], true)).toBe(`${LABEL[c]}运行中`)
      expect(featureStatusText(LABEL[c], false)).toBe(`${LABEL[c]}已暂停`)
      expect(switchName('mp', LABEL[c])).toBe(`小程序${LABEL[c]}`)
      expect(switchName('app', LABEL[c])).toBe(`App${LABEL[c]}`)
    }
  })
})

describe('二次确认文案：每类 × 每端 × 关停 / 恢复', () => {
  it('关停：标明影响范围与仅限本端；恢复：确认风险已处理', () => {
    for (const c of CATEGORY_ORDER) {
      for (const p of ['mp', 'app'] as FeaturePlatform[]) {
        const name = switchName(p, LABEL[c])
        const off = featureConfirmCopy(name, false, LABEL[c])
        expect(off.title).toBe(`紧急关停${name}`)
        expect(off.confirm).toBe('确认关停')
        expect(off.message).toContain('最迟 15 秒')
        expect(off.message).toContain(p === 'mp' ? '仅小程序' : '仅App')
        const on = featureConfirmCopy(name, true, LABEL[c])
        expect(on.title).toBe(`恢复${name}`)
        expect(on.confirm).toBe('确认恢复')
      }
    }
  })

  it('一键操作文案只提所选端，六类都列出', () => {
    for (const p of ['mp', 'app'] as FeaturePlatform[]) {
      const other = p === 'mp' ? 'App' : '小程序'
      for (const next of [false, true]) {
        const copy = platformBatchCopy(p, next)
        expect(copy.title).toContain(p === 'mp' ? '小程序' : 'App')
        expect(copy.title).not.toContain(other)
        expect(copy.message).not.toContain(other)
        for (const c of CATEGORY_ORDER) expect(copy.message).toContain(LABEL[c])
      }
    }
  })
})

describe('默认状态与最后操作文案', () => {
  it('小程序默认开启，App 默认关闭；操作过则显示操作人', () => {
    expect(lastOperationText(null, 'mp')).toBe('默认开启，尚未操作过')
    expect(lastOperationText(null, 'app')).toBe('默认关闭，尚未操作过')
    const never = { updatedBy: null, updatedByName: null, updatedAt: null }
    expect(lastOperationText(never, 'app')).toBe('默认关闭，尚未操作过')
    const done = { updatedBy: 7, updatedByName: '运营小王', updatedAt: '2026-10-26T18:00:00' }
    expect(lastOperationText(done, 'app')).toBe('最后操作：运营小王 · 2026-10-26 18:00')
  })
  it('类别固定 6 类，新增两类有影响说明', () => {
    expect(CATEGORY_ORDER).toEqual(['agentChat', 'cookAsk', 'vision', 'mealPlan', 'estimate', 'postText'])
    expect(featureConfirmCopy('小程序营养估算', false, '营养估算').message).toContain('管理后台运营维护食材时补全营养不受影响')
    expect(featureConfirmCopy('AppAI 帮写', false, 'AI 帮写').message).toContain('发帖照常')
  })
})

describe('开关页源码守卫：一键操作按端传参', () => {
  const view = readFileSync(resolve(__dirname, '../views/feature-switch/Index.vue'), 'utf8')
  it('两端各有一键关闭 / 恢复按钮，调用时带本端 platform', () => {
    expect(view).toContain('batch-off-${p}')
    expect(view).toContain('batch-on-${p}')
    expect(view).toContain('@click="batch(p, false)"')
    expect(view).toContain('@click="batch(p, true)"')
    expect(view).toContain('setPlatformSwitches({ platform, enabled: next')
  })
  it('页面说明写明默认状态：小程序默认开启，App 默认关闭', () => {
    expect(view).toContain('小程序默认开启，App 默认关闭')
  })
  it('关停必须填原因（恢复不用）', () => {
    expect(view).toContain('validateSwitchReason(false, v)')
  })
})
