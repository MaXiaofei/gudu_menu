import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { existsSync } from 'node:fs'
import { matchIngKey, resolveIngKey, ingIconSrc, defaultAvatarKey } from '@/utils/ingIcon'
import localRules from '@/utils/ingredient-icons.json'
import localAssets from '@/utils/assets.json'

const root = resolve(process.cwd(), '..')

// prototype-ui-rollout P0：食材插画解析规则（单一来源 docs/design/ingredient-icons.json）。
describe('utils/ingIcon 食材插画', () => {
  it('副本与 docs/design 源文件一致（改规则请同步复制）', () => {
    const rules = JSON.parse(readFileSync(resolve(root, 'docs/design/ingredient-icons.json'), 'utf8'))
    const assets = JSON.parse(readFileSync(resolve(root, 'docs/design/assets.json'), 'utf8'))
    expect(localRules).toEqual(rules)
    expect(localAssets).toEqual(assets)
  })

  it('每个插画 key 都有打包素材，规则引用的 key 都存在', () => {
    for (const k of localAssets.ing) expect(existsSync(resolve(process.cwd(), `src/static/ing/${k}.png`)), k).toBe(true)
    for (const k of localAssets.avatar) expect(existsSync(resolve(process.cwd(), `src/static/avatar/${k}.png`)), k).toBe(true)
    for (const [k] of localRules.keywords as [string, string[]][]) expect(localAssets.ing).toContain(k)
  })

  it('名称关键词：取最长命中', () => {
    expect(matchIngKey('西红柿')).toBe('tomato')
    expect(matchIngKey('胡萝卜')).toBe('carrot') // 「萝卜」「胡萝卜」都命中，取更长
    expect(matchIngKey('鸡翅中')).toBe('wing')
    expect(matchIngKey('秋葵')).toBeNull()
  })

  it('解析顺序：iconKey → 名称 → 品类兜底 → null', () => {
    expect(resolveIngKey({ iconKey: 'fish', name: '番茄' })).toBe('fish')
    expect(resolveIngKey({ iconKey: 'not-exist', name: '番茄' })).toBe('tomato')
    expect(resolveIngKey({ name: '秋葵', category: '蔬菜' })).toBe('cabbage')
    expect(resolveIngKey({ name: '秋葵' })).toBeNull()
    expect(ingIconSrc('egg')).toBe('/static/ing/egg.png')
    expect(ingIconSrc(null)).toBe('')
  })

  it('默认头像：宝宝档案用宝宝插画；成人按名字稳定散列', () => {
    expect(defaultAvatarKey('朵朵', ['宝宝辅食'])).toBe('duoduo')
    const k = defaultAvatarKey('林林')
    expect(k).not.toBe('duoduo')
    expect(defaultAvatarKey('林林')).toBe(k)
  })
})
