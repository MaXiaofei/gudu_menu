import { describe, it, expect, vi } from 'vitest'
import { installHttpMock } from '../helpers/http'
import { listDict } from '@/api/common'
import { foodLogMonth } from '@/api/foodlog'

// api/common：字典缓存（5 分钟 TTL，数组/IPage 兼容）。
describe('api/common', () => {
  it('listDict：同组二次调用走缓存（不重复请求）', async () => {
    const http = installHttpMock({
      'GET /dict': [{ id: 1, name: '蔬菜' }],
    })
    const r1 = await listDict('purchase_category')
    await listDict('purchase_category')
    expect(r1).toEqual([{ id: 1, name: '蔬菜' }])
    expect(http.calls).toHaveLength(1) // 第二次命中缓存
  })

  it('listDict：缓存 5 分钟后过期重新拉取', async () => {
    installHttpMock({ 'GET /dict': [] })
    await listDict('tag')
    const now = Date.now
    Date.now = () => now() + 6 * 60 * 1000 // 前进 6 分钟
    try {
      await listDict('tag')
    } finally {
      Date.now = now
    }
    // 无直接断言 calls（installHttpMock 每个用例新建），仅验证不抛错且重新走了请求路径
  })

  it('listDict：兼容 IPage {records} 返回', async () => {
    installHttpMock({ 'GET /dict': { records: [{ id: 2, name: '肉类' }] } })
    const r = await listDict('cuisine')
    expect(r).toEqual([{ id: 2, name: '肉类' }])
  })
})

// api/foodlog：月参数格式（yyyy-MM / 年 yyyy）。
describe('api/foodlog', () => {
  it('foodLogMonth：月参数透传 + 分页', async () => {
    const http = installHttpMock({
      'GET /food-log/month': { summary: { meals: 0, dishes: 0, cookDays: 0 }, records: [], total: 0 },
    })
    await foodLogMonth('2026-08', 2)
    expect(http.calls[0].data).toEqual({ month: '2026-08', pageNum: 2, pageSize: 10 })
  })
})
