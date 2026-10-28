import { describe, it, expect } from 'vitest'
import {
  OWN_CATEGORY_ID, OWN_TAG, aliasHint, ingredientPermission, matchLocal, mergeMatches, ownFirst, resolveTip,
} from '@/utils/ingredientSearch'

const pub = (id: number, name: string) => ({ id, name })
const own = (id: number, name: string) => ({ id, name, familyOwned: true })

describe('utils/ingredientSearch', () => {
  it('标签与筛选常量：小标签与分类筛选都叫「我家」', () => {
    expect(OWN_TAG).toBe('我家')
    expect(OWN_CATEGORY_ID).toBeLessThan(0)
  })

  it('同名时本家的排前面，不同名顺序不变', () => {
    const out = ownFirst([pub(1, '酱油'), pub(2, '盐'), own(3, '酱油')])
    expect(out.map((i) => i.id)).toEqual([3, 2, 1])
  })

  it('本地匹配：名称包含；空词不匹配', () => {
    const all = [pub(1, '番茄'), pub(2, '圣女果'), own(3, '小番茄')]
    expect(matchLocal(all, '番茄').map((i) => i.id)).toEqual([1, 3])
    expect(matchLocal(all, '  ')).toEqual([])
  })

  it('合并服务端结果：按 id 去重，别名命中的公共食材补进来', () => {
    const local = [pub(1, '番茄')]
    const remote = [pub(1, '番茄'), { id: 9, name: '马铃薯', matchedAlias: '土豆' }]
    const out = mergeMatches(local, remote)
    expect(out.map((i) => i.id)).toEqual([1, 9])
  })

  it('别名提示：只有别名命中才显示', () => {
    expect(aliasHint({ name: '马铃薯', matchedAlias: '土豆' })).toBe('别名：土豆')
    expect(aliasHint({ name: '马铃薯', matchedAlias: null })).toBe('')
    expect(aliasHint({ name: '马铃薯', matchedAlias: '马铃薯' })).toBe('')
  })

  it('新建提示：命中公共显示「已有：马铃薯」', () => {
    expect(resolveTip({ id: 1, name: '马铃薯', existed: 'public', familyOwned: false })).toBe('已有：马铃薯')
    expect(resolveTip({ id: 2, name: '家传酱', existed: 'family', familyOwned: true })).toBe('你家已有「家传酱」')
    expect(resolveTip({ id: 3, name: '新品', existed: 'created', familyOwned: true })).toBe('已建档「新品」')
  })

  it('编辑权限：本家可编辑，公共只读并带提示', () => {
    expect(ingredientPermission({ familyOwned: true })).toEqual({ canEdit: true, readonlyTip: '' })
    expect(ingredientPermission({ familyOwned: false })).toEqual({ canEdit: false, readonlyTip: '平台食材，由平台维护' })
    expect(ingredientPermission(null).canEdit).toBe(false)
  })
})
