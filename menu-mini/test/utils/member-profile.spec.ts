import { describe, it, expect } from 'vitest'
import { emptyForm, toForm, toSave, validateForm } from '@/utils/memberProfile'
import { isPublicDish } from '@/api/dish'

// 本家成员档案表单（含无账号成员）+ 公共菜谱判定（复制到我家入口）。
describe('utils/memberProfile', () => {
  it('档案 → 表单 → 入参：保留表单外字段（糖/盐上限），清空的字段删除', () => {
    const m = {
      id: 3,
      name: '小宝',
      accountId: null,
      healthProfile: { age: 2, audiences: ['宝宝辅食'], allergies: ['花生'], sugarMax: 10 },
      goal: null,
    }
    const f = toForm(m)
    expect(f.allergies).toBe('花生')
    f.allergies = '花生、虾， 蛋'
    f.age = ''
    f.height = '85'
    const s = toSave(f, m)
    expect(s.id).toBe(3)
    expect(s.healthProfile).toEqual({ audiences: ['宝宝辅食'], allergies: ['花生', '虾', '蛋'], sugarMax: 10, height: 85 })
  })

  it('新建无账号成员：不带 id', () => {
    const f = { ...emptyForm('奶奶'), gender: 'female' as const, goal: 'MAINTAIN' as const }
    const s = toSave(f)
    expect(s.id).toBeUndefined()
    expect(s.name).toBe('奶奶')
    expect(s.healthProfile).toEqual({ gender: 'female' })
    expect(s.goal).toBe('MAINTAIN')
  })

  it('校验：称呼必填、数值范围', () => {
    expect(validateForm(emptyForm(''))).toBe('请填写称呼')
    expect(validateForm({ ...emptyForm('a'), age: '200' })).toContain('年龄')
    expect(validateForm({ ...emptyForm('a'), weight: 'abc' })).toContain('体重')
    expect(validateForm({ ...emptyForm('a'), age: '3', height: '90' })).toBe('')
  })

  it('isPublicDish：familyId 为 null 是公共；有家庭 id 是本家；旧后端按 sourceName', () => {
    expect(isPublicDish({ familyId: null })).toBe(true)
    expect(isPublicDish({ familyId: 8, sourceName: '下厨房' })).toBe(false)
    expect(isPublicDish({ sourceName: '公共库' })).toBe(true)
    expect(isPublicDish({})).toBe(false)
  })
})
