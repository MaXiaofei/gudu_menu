import { describe, it, expect } from 'vitest'
import { isAllActive, pickTag, pickCuisine, toSearchParams, createLatestGuard, type DishFilterState } from '@/utils/dishFilter'

const base: DishFilterState = { tagId: 0, cuisineId: 0, sort: 'coverage', keyword: '' }

describe('菜谱页筛选 + 排序', () => {
  it('「全部」仅在没选标签也没选菜系时高亮', () => {
    expect(isAllActive(base)).toBe(true)
    expect(isAllActive({ ...base, tagId: 3 })).toBe(false)
    expect(isAllActive({ ...base, cuisineId: 5 })).toBe(false)
  })

  it('点「全部」同时清空标签与菜系，排序不变', () => {
    const s = pickTag({ ...base, tagId: 3, cuisineId: 5, sort: 'cooked' }, 0)
    expect(s).toEqual({ ...base, sort: 'cooked' })
  })

  it('标签再点一次取消；菜系再点一次取消（此前无法取消）', () => {
    expect(pickTag({ ...base, tagId: 3 }, 3).tagId).toBe(0)
    expect(pickCuisine({ ...base, cuisineId: 5 }, 5).cuisineId).toBe(0)
  })

  it('标签与菜系叠加，切换其一不影响另一个', () => {
    let s = pickTag(base, 3)
    s = pickCuisine(s, 5)
    expect(s).toMatchObject({ tagId: 3, cuisineId: 5 })
    s = pickTag(s, 4)
    expect(s).toMatchObject({ tagId: 4, cuisineId: 5 })
  })

  it('请求参数同时带上筛选与排序（叠加而非互斥）', () => {
    const p = toSearchParams({ tagId: 3, cuisineId: 5, sort: 'coverage', keyword: '蛋' }, 2)
    expect(p).toEqual({ keyword: '蛋', tagIds: '3', cuisineIds: '5', sort: 'coverage', pageNum: 2 })
    const q = toSearchParams(base, 1)
    expect(q.tagIds).toBeUndefined()
    expect(q.cuisineIds).toBeUndefined()
    expect(q.sort).toBe('coverage')
  })

  it('竞态守卫：只有最后一次请求算最新', () => {
    const g = createLatestGuard()
    const a = g.next()
    const b = g.next()
    expect(g.isLatest(a)).toBe(false)
    expect(g.isLatest(b)).toBe(true)
  })
})
