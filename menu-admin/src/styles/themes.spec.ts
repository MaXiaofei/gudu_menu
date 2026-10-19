import { describe, it, expect } from 'vitest'
import { themes, getTheme } from './themes'

describe('themes（由 tokens.json 生成）', () => {
  it('包含 cream / matcha 两套皮肤，第一项为默认', () => {
    const keys = themes.map((t) => t.key)
    expect(keys).toContain('cream')
    expect(keys).toContain('matcha')
    expect(themes[0].key).toBe('cream')
  })

  it('每套皮肤字段完整', () => {
    for (const t of themes) {
      expect(t.name).toBeTruthy()
      for (const c of [t.primary, t.sidebar, t.sidebarText, t.bg, t.title, t.body, t.onPrimary]) {
        expect(c).toMatch(/^#[0-9A-Fa-f]{6}$/)
      }
      expect(t.chart.length).toBeGreaterThanOrEqual(3)
    }
  })
})

describe('getTheme', () => {
  it('新 key 返回对应皮肤', () => {
    expect(getTheme('cream').key).toBe('cream')
    expect(getTheme('matcha').key).toBe('matcha')
  })

  it('兼容旧持久化 key warm/green', () => {
    expect(getTheme('warm').key).toBe('cream')
    expect(getTheme('green').key).toBe('matcha')
  })

  it('未知 / 空 / null 回落默认皮肤', () => {
    expect(getTheme('not-exist').key).toBe(themes[0].key)
    expect(getTheme('').key).toBe(themes[0].key)
    expect(getTheme(null).key).toBe(themes[0].key)
  })
})
