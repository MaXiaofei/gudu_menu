import { describe, expect, it } from 'vitest'
import { DICT_NAME_MAX, dictDeleteWarning, validateDictName, validateMetric } from './dict'

describe('字典名称校验', () => {
  it('空名称 / 超长', () => {
    expect(validateDictName('  ', [])).toBe('请填写名称')
    expect(validateDictName('a'.repeat(DICT_NAME_MAX + 1), [])).toContain('不超过')
    expect(validateDictName('a'.repeat(DICT_NAME_MAX), [])).toBeNull()
  })
  it('重名：忽略首尾空格与大小写；编辑时自己的原名不算重名', () => {
    expect(validateDictName(' 川菜 ', ['川菜', '粤菜'])).toContain('已存在')
    expect(validateDictName('G', ['g', 'ml'])).toContain('已存在')
    expect(validateDictName('川菜', ['川菜', '粤菜'], '川菜')).toBeNull()
    expect(validateDictName('粤菜', ['川菜', '粤菜'], '川菜')).toContain('已存在')
    expect(validateDictName('湘菜', ['川菜'])).toBeNull()
  })
})

describe('删除提示', () => {
  it('按分组说明影响范围，未知分组走兜底', () => {
    expect(dictDeleteWarning('cuisine', '川菜')).toContain('菜谱')
    expect(dictDeleteWarning('purchase_category', '蔬菜')).toContain('采购')
    expect(dictDeleteWarning('x', '甲')).toContain('正在使用该项的数据')
  })
})

describe('营养指标校验', () => {
  const ok = { name: 'fiber', unit: 'g', metricGroup: 'macro' }
  it('合法', () => expect(validateMetric(ok, ['calorie'])).toBeNull())
  it('标识为空 / 非法 / 重复', () => {
    expect(validateMetric({ ...ok, name: '' }, [])).toBe('请填写指标标识')
    expect(validateMetric({ ...ok, name: '纤维' }, [])).toContain('英文')
    expect(validateMetric(ok, ['fiber'])).toContain('已存在')
    expect(validateMetric(ok, ['fiber'], 'fiber')).toBeNull()
  })
  it('单位与分组必选', () => {
    expect(validateMetric({ ...ok, unit: ' ' }, [])).toBe('请选择单位')
    expect(validateMetric({ ...ok, metricGroup: 'x' }, [])).toBe('请选择分组')
  })
})
