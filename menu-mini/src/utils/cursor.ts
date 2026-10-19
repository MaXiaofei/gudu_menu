/**
 * 游标分页（contracts：请求 cursor/size，响应 { items, nextCursor }）。
 * 去重按 key（翻页期间有新内容插入也不重复）；nextCursor 为 null 即到底。
 */
import { reactive } from 'vue'

export interface CursorPage<T> {
  items: T[]
  nextCursor: string | null
}

export type CursorFetcher<T> = (cursor: string | null) => Promise<CursorPage<T>>

export interface CursorList<T> {
  items: T[]
  nextCursor: string | null
  loading: boolean
  done: boolean
  error: boolean
  /** 加载下一页；正在加载或已到底时不重复请求 */
  loadMore(): Promise<void>
  /** 从头加载（下拉刷新 / 切 Tab） */
  refresh(): Promise<void>
}

export function normalizePage<T>(p: Partial<CursorPage<T>> | null | undefined): CursorPage<T> {
  return { items: Array.isArray(p?.items) ? (p!.items as T[]) : [], nextCursor: p?.nextCursor ?? null }
}

export function createCursorList<T>(fetcher: CursorFetcher<T>, keyOf: (t: T) => string | number): CursorList<T> {
  let seq = 0
  const state = reactive({
    items: [] as T[],
    nextCursor: null as string | null,
    loading: false,
    done: false,
    error: false,
    async loadMore() {
      if (state.loading || state.done) return
      await run(state.nextCursor, false)
    },
    async refresh() {
      state.done = false
      await run(null, true)
    },
  })

  async function run(cursor: string | null, reset: boolean) {
    const my = ++seq
    state.loading = true
    state.error = false
    try {
      const page = normalizePage(await fetcher(cursor))
      if (my !== seq) return // 已被更新的请求取代（快速切 Tab）
      const base = reset ? [] : (state.items as T[])
      const seen = new Set(base.map(keyOf))
      const fresh = page.items.filter((it) => {
        const k = keyOf(it)
        if (seen.has(k)) return false
        seen.add(k)
        return true
      })
      state.items = [...base, ...fresh] as any
      state.nextCursor = page.nextCursor
      state.done = !page.nextCursor
    } catch {
      if (my === seq) state.error = true
    } finally {
      if (my === seq) state.loading = false
    }
  }

  return state as CursorList<T>
}

/** 瀑布流分两列：依次放进当前更矮的一列（heightOf 返回相对高度，如 1/coverRatio + 文字行）。 */
export function splitColumns<T>(items: T[], heightOf: (t: T) => number): [T[], T[]] {
  const cols: [T[], T[]] = [[], []]
  const h = [0, 0]
  for (const it of items) {
    const i = h[0] <= h[1] ? 0 : 1
    cols[i].push(it)
    h[i] += heightOf(it)
  }
  return cols
}
