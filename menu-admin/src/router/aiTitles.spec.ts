import { describe, it, expect } from 'vitest'
import router from './index'

const meta = (name: string) => router.getRoutes().find((r) => r.name === name)?.meta

describe('AI 相关菜单名与权限', () => {
  it('两个 AI 页名字区分开：调用明细（超管）/ 额度护栏（运营可见）', () => {
    expect(meta('AiLog')?.title).toBe('AI 调用明细')
    expect(meta('AiLog')?.superAdmin).toBe(true)
    expect(meta('AiGuard')?.title).toBe('AI 额度护栏')
    expect(meta('AiGuard')?.superAdmin).toBeFalsy()
  })
  it('菜单名不重复', () => {
    const titles = router.getRoutes().map((r) => r.meta?.title).filter(Boolean)
    expect(new Set(titles).size).toBe(titles.length)
  })
})
