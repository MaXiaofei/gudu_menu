import { describe, it, expect } from 'vitest'
import { countNutrition, hasNutrition } from './nutrition'

describe('hasNutrition', () => {
  it('至少一项有效数值', () => {
    expect(hasNutrition({ calorie: 105 })).toBe(true)
    expect(hasNutrition({ calorie: 0 })).toBe(true)
    expect(hasNutrition({ calorie: null, protein: undefined })).toBe(false)
    expect(hasNutrition({})).toBe(false)
    expect(hasNutrition(null)).toBe(false)
  })
})

describe('countNutrition', () => {
  it('有数据 / 缺数据 / 非食用分开计', () => {
    expect(
      countNutrition([
        { nutrition: { calorie: 105 } },
        { nutrition: {} },
        { nutrition: null },
        { nutrition: {}, edible: false },
      ]),
    ).toEqual({ total: 4, withData: 1, missing: 2, inedible: 1 })
  })
})
