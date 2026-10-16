import { describe, it, expect } from 'vitest'
import { installHttpMock } from '../helpers/http'
import { getMe, updateMe } from '@/api/member'
import { submitFeedback } from '@/api/feedback'
import { recommendHome, recentCooked } from '@/api/recommend'
import { dishCookMaterials, cookDish } from '@/api/cook'
import { searchDishes, coverageText, coverageLevel } from '@/api/dish'
import { setPantryLevel, pantryManualAdd, shelfLifeDays, listExpiring } from '@/api/pantry'

// feature-gaps-closure 新接口（契约：openspec/changes/feature-gaps-closure/api-contract.md）
describe('api/member me（§6）', () => {
  it('GET /member/me 透传', async () => {
    installHttpMock({ 'GET /member/me': { id: 3, name: '妈妈', avatarUrl: null, phone: '138****0001', isAdmin: false } })
    const me = await getMe()
    expect(me.name).toBe('妈妈')
    expect(me.phone).toBe('138****0001')
  })

  it('PUT /member/me：只传改动字段，昵称去首尾空格', async () => {
    const http = installHttpMock({ 'PUT /member/me': null })
    await updateMe({ name: ' 掌勺人 ' })
    expect(http.calls[0].method).toBe('PUT')
    expect(http.calls[0].data).toEqual({ name: '掌勺人' })
    await updateMe({ name: '爸爸', avatarUrl: '/gudu/uploads/original/a.jpg' })
    expect(http.calls[1].data).toEqual({ name: '爸爸', avatarUrl: '/gudu/uploads/original/a.jpg' })
  })
})

describe('api/feedback（§7）', () => {
  it('POST /feedback：内容 trim，空联系方式不传，图片最多 3 张', async () => {
    const http = installHttpMock({ 'POST /feedback': 12 })
    const id = await submitFeedback({
      content: '  搜索有点慢  ',
      contact: '  ',
      images: ['/uploads/1.jpg', '/uploads/2.jpg', '/uploads/3.jpg', '/uploads/4.jpg'],
      clientInfo: 'v1.0.0 / mp-weixin',
    })
    expect(id).toBe(12)
    expect(http.calls[0].data).toEqual({
      content: '搜索有点慢',
      images: ['/uploads/1.jpg', '/uploads/2.jpg', '/uploads/3.jpg'],
      clientInfo: 'v1.0.0 / mp-weixin',
    })
  })

  it('无图片不传 images 字段', async () => {
    const http = installHttpMock({ 'POST /feedback': 1 })
    await submitFeedback({ content: '很好用的工具', contact: '微信 abc' })
    expect(http.calls[0].data).toEqual({ content: '很好用的工具', contact: '微信 abc' })
  })
})

describe('api/recommend（§4/§5）', () => {
  it('GET /recommend/home：缺失分区补空数组', async () => {
    installHttpMock({
      'GET /recommend/home': {
        expiring: [{ ingredientId: 1, ingredientName: '菠菜', level: 'LOW', bestBefore: '2026-10-17', daysLeft: 1 }],
        frequent: [],
      },
    })
    const h = await recommendHome()
    expect(h.expiring).toHaveLength(1)
    expect(h.expiringPicks).toEqual([])
    expect(h.seasonal).toEqual([])
  })

  it('GET /cookbook/recent：limit 夹到 1–30', async () => {
    const http = installHttpMock({ 'GET /cookbook/recent': [{ dishId: 1, name: '番茄炒蛋', coverImage: null, lastCookedAt: '2026-10-15T18:00:00', cookedCount: 3 }] })
    const list = await recentCooked(10)
    expect(list[0].cookedCount).toBe(3)
    expect(http.calls[0].data).toEqual({ limit: 10 })
    await recentCooked(100)
    expect(http.calls[1].data).toEqual({ limit: 30 })
  })
})

describe('api/cook 单菜直做（§2）', () => {
  it('GET /cook/dish/{id}/materials：兼容 {items} 结构，servings 默认 1', async () => {
    const http = installHttpMock({
      'GET /cook/dish/5/materials': { menuId: null, items: [{ ingredientId: 9, ingredientName: '鸡蛋', usageTexts: ['2 个'], level: 'ENOUGH', isCondiment: false }] },
    })
    const items = await dishCookMaterials(5)
    expect(items[0].ingredientName).toBe('鸡蛋')
    expect(http.calls[0].data).toEqual({ servings: 1 })
  })

  it('POST /cook/dish/{id}：默认份数 1、空数组，note 超长截断', async () => {
    const http = installHttpMock({ 'POST /cook/dish/5': { recordId: 77, dishId: 5 } })
    const r = await cookDish(5)
    expect(r.recordId).toBe(77)
    expect(http.calls[0].data).toEqual({ servings: 1, usedUp: [], partiallyUsed: [] })
    await cookDish(5, { servings: 2, usedUp: [9], partiallyUsed: [3], note: 'x'.repeat(300) })
    expect(http.calls[1].data.usedUp).toEqual([9])
    expect(http.calls[1].data.partiallyUsed).toEqual([3])
    expect(http.calls[1].data.note).toHaveLength(200)
  })
})

describe('api/dish 覆盖率（§3）', () => {
  it('sort=coverage 透传，latest 不传', async () => {
    const http = installHttpMock({ 'GET /dish/search': { records: [], total: 0 } })
    await searchDishes({ pageNum: 1, sort: 'coverage' })
    expect(http.calls[0].data.sort).toBe('coverage')
    await searchDishes({ pageNum: 1, sort: 'latest' })
    expect(http.calls[1].data.sort).toBeUndefined()
  })

  it('coverageText / coverageLevel', () => {
    expect(coverageText(null)).toBe('')
    expect(coverageText(undefined)).toBe('')
    expect(coverageText({ enough: 0, total: 0, missing: [] })).toBe('')
    expect(coverageText({ enough: 3, total: 5, missing: [] })).toBe('家里够 3/5 样')
    expect(coverageLevel({ enough: 5, total: 5, missing: [] })).toBe('full')
    expect(coverageLevel({ enough: 2, total: 5, missing: [] })).toBe('partial')
    expect(coverageLevel({ enough: 0, total: 5, missing: [] })).toBe('none')
    expect(coverageLevel(null)).toBe('')
  })
})

describe('api/pantry 保质期（§1）', () => {
  it('PUT level：传 bestBefore 才带上', async () => {
    const http = installHttpMock({ 'PUT /pantry/8/level': null })
    await setPantryLevel(8, 'LOW')
    expect(http.calls[0].data).toEqual({ level: 'LOW' })
    await setPantryLevel(8, 'ENOUGH', '2026-10-20')
    expect(http.calls[1].data).toEqual({ level: 'ENOUGH', bestBefore: '2026-10-20' })
  })

  it('POST /pantry/manual 带 bestBefore', async () => {
    const http = installHttpMock({ 'POST /pantry/manual': null })
    await pantryManualAdd({ ingredientId: 8, level: 'ENOUGH', sourceNote: '朋友送', bestBefore: '2026-10-20' })
    expect(http.calls[0].data).toEqual({ ingredientId: 8, level: 'ENOUGH', sourceNote: '朋友送', bestBefore: '2026-10-20' })
  })

  it('GET /pantry/shelf-life：正数返回天数，null/0/失败返回 null', async () => {
    let body: any = { suggestedDays: 5 }
    const http = installHttpMock({ 'GET /pantry/shelf-life': () => body })
    expect(await shelfLifeDays(8)).toBe(5)
    expect(http.calls[0].data).toEqual({ ingredientId: 8 })
    body = { suggestedDays: null }
    expect(await shelfLifeDays(8)).toBeNull()
    body = { suggestedDays: 0 }
    expect(await shelfLifeDays(8)).toBeNull()
    installHttpMock({}) // 404 → 业务失败
    expect(await shelfLifeDays(8)).toBeNull()
  })

  it('GET /pantry/expiring?days=', async () => {
    const http = installHttpMock({ 'GET /pantry/expiring': [] })
    await listExpiring()
    expect(http.calls[0].data).toEqual({ days: 3 })
  })
})
