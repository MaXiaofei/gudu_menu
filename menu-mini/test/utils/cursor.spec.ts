import { describe, it, expect, vi } from 'vitest'
import { createCursorList, splitColumns, normalizePage } from '@/utils/cursor'

describe('utils/cursor 游标分页', () => {
  it('首屏 → 下一页带 nextCursor；nextCursor=null 到底不再请求', async () => {
    const fetcher = vi.fn(async (c: string | null) =>
      c === null ? { items: [{ id: 1 }, { id: 2 }], nextCursor: 'c2' } : { items: [{ id: 3 }], nextCursor: null },
    )
    const l = createCursorList(fetcher, (x) => x.id)
    await l.refresh()
    await l.loadMore()
    await l.loadMore()
    expect(fetcher.mock.calls.map((c) => c[0])).toEqual([null, 'c2'])
    expect(l.items.map((x) => x.id)).toEqual([1, 2, 3])
    expect(l.done).toBe(true)
  })

  it('翻页期间插入新内容导致重复 → 按 key 去重', async () => {
    const fetcher = vi.fn(async (c: string | null) =>
      c === null ? { items: [{ id: 5 }, { id: 4 }], nextCursor: 'x' } : { items: [{ id: 4 }, { id: 3 }], nextCursor: null },
    )
    const l = createCursorList(fetcher, (x) => x.id)
    await l.refresh()
    await l.loadMore()
    expect(l.items.map((x) => x.id)).toEqual([5, 4, 3])
  })

  it('refresh 重置；失败置 error 不清空', async () => {
    let fail = false
    const l = createCursorList(async () => {
      if (fail) throw new Error('x')
      return { items: [{ id: 1 }], nextCursor: 'n' }
    }, (x) => x.id)
    await l.refresh()
    fail = true
    await l.loadMore()
    expect(l.error).toBe(true)
    expect(l.items).toHaveLength(1)
  })

  it('快速切换：旧请求晚返回被丢弃', async () => {
    let resolveOld: (v: any) => void = () => {}
    let n = 0
    const l = createCursorList(
      () =>
        n++ === 0
          ? new Promise<any>((r) => (resolveOld = r))
          : Promise.resolve({ items: [{ id: 9 }], nextCursor: null }),
      (x: any) => x.id,
    )
    const p1 = l.refresh()
    await l.refresh()
    resolveOld({ items: [{ id: 1 }], nextCursor: 'z' })
    await p1
    expect(l.items.map((x: any) => x.id)).toEqual([9])
  })

  it('normalizePage 兜底空响应', () => {
    expect(normalizePage(null)).toEqual({ items: [], nextCursor: null })
  })

  it('splitColumns 放进更矮的一列', () => {
    const [a, b] = splitColumns([3, 1, 1, 1, 2], (x) => x)
    expect(a).toEqual([3, 2]) // 等高时放左列
    expect(b).toEqual([1, 1, 1])
  })
})
