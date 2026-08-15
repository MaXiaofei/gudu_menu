import { describe, it, expect } from 'vitest'
import { installHttpMock } from '../helpers/http'
import { listGroupedPage, pantryManualAdd, sourceLabel, sourceSub } from '@/api/pantry'

// api/pantry：分页分组参数（level/keyword/pageNum/pageSize）+ 来源文案映射。
describe('api/pantry', () => {
  it('listGroupedPage：level/keyword 过滤 + 每页 10；空值不传', async () => {
    const http = installHttpMock({
      'GET /pantry/grouped': { summary: { enough: 0, low: 0, none: 0 }, items: [] },
    })
    await listGroupedPage({ level: 'NONE', keyword: '米', pageNum: 2 })
    expect(http.calls[0].data).toEqual({ level: 'NONE', keyword: '米', pageNum: 2, pageSize: 10 })

    await listGroupedPage({ pageNum: 1 })
    expect(http.calls[1].data).toEqual({ pageNum: 1, pageSize: 10 }) // 无 level/keyword 字段
  })

  it('pantryManualAdd：选中已有传 ingredientId，新建传 name', async () => {
    const http = installHttpMock({ 'POST /pantry/manual': null })
    await pantryManualAdd({ ingredientId: 5, level: 'LOW', sourceNote: '朋友送' })
    expect(http.calls[0].data).toEqual({ ingredientId: 5, name: undefined, level: 'LOW', sourceNote: '朋友送' })

    await pantryManualAdd({ name: '橙子' })
    expect(http.calls[1].data.ingredientId).toBeUndefined()
    expect(http.calls[1].data.name).toBe('橙子')
  })

  it('sourceLabel/sourceSub：来源映射 + 备注/日期副文案', () => {
    expect(sourceLabel({ ingredientId: 1, level: 'NONE', lastChange: { source: 'manual' } })).toBe('手动')
    expect(sourceLabel({ ingredientId: 1, level: 'NONE', lastChange: { source: 'cook' } })).toBe('用完了')
    expect(sourceLabel({ ingredientId: 1, level: 'NONE', lastChange: null })).toBe('')

    expect(sourceSub({ ingredientId: 1, level: 'NONE', lastChange: { source: 'manual', sourceNote: '朋友送' } })).toBe('朋友送')
    expect(sourceSub({ ingredientId: 1, level: 'NONE', lastChange: { createTime: '2026-08-10 12:00:00' } })).toBe('8/10')
  })
})
