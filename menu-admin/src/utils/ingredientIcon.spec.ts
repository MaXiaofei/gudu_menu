import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import rules from './ingredient-icons.json'
import assets from './assets.json'
import { availableIconKeys, iconUrl, matchIconKey, resolveIconKey } from './ingredientIcon'

const root = resolve(process.cwd(), '..')

describe('食材插画 key', () => {
  it('副本与 docs/design 源文件一致（改规则请同步复制；Docker 构建上下文仅 menu-admin/，够不到仓库级 docs/）', () => {
    const srcRules = JSON.parse(readFileSync(resolve(root, 'docs/design/ingredient-icons.json'), 'utf8'))
    const srcAssets = JSON.parse(readFileSync(resolve(root, 'docs/design/assets.json'), 'utf8'))
    expect(rules).toEqual(srcRules)
    expect(assets).toEqual(srcAssets)
  })

  it('素材与 assets.json 一致（gen-assets 生成后同步）', () => {
    expect(availableIconKeys()).toEqual([...assets.ing].sort())
  })

  it('规则里的每个 key 都有素材', () => {
    const keys = new Set(availableIconKeys())
    for (const [key] of rules.keywords as [string, string[]][]) expect(keys.has(key)).toBe(true)
    for (const key of Object.values(rules.categoryFallback as Record<string, string>)) expect(keys.has(key)).toBe(true)
  })

  it('取最长关键词：胡萝卜 → carrot（不是「萝卜」以外的项），鸡蛋 → egg（不是 wing）', () => {
    expect(matchIconKey('胡萝卜')).toBe('carrot')
    expect(matchIconKey('鸡蛋')).toBe('egg')
    expect(matchIconKey('鸡翅中')).toBe('wing')
    expect(matchIconKey('北豆腐')).toBe('tofu')
  })

  it('优先级：接口 iconKey → 名称 → 品类兜底 → null', () => {
    expect(resolveIconKey({ iconKey: 'fish', name: '番茄' })).toBe('fish')
    expect(resolveIconKey({ iconKey: 'not-exist', name: '番茄' })).toBe('tomato')
    expect(resolveIconKey({ name: '秋葵', categoryName: '蔬菜' })).toBe('cabbage')
    expect(resolveIconKey({ name: '秋葵' })).toBeNull()
  })

  it('iconUrl 有素材返回地址，无则 undefined', () => {
    expect(iconUrl('tomato')).toMatch(/tomato/)
    expect(iconUrl(null)).toBeUndefined()
  })
})
