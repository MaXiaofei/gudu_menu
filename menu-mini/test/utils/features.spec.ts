import { describe, it, expect } from 'vitest'
import { FEATURES, resolveFeatures } from '@/config/features'
import { tabsFor } from '@/utils/nav'
import { clientHeader } from '@/utils/request'
import { loadPagesJson } from '../helpers/pagesJson'

// A.1 小程序关闭厨房圈 / A.9 隐藏语音：构建期开关与 pages.json 条件编译保持一致
describe('config/features 构建期开关', () => {
  it('微信小程序关闭厨房圈与语音；H5 / 其他保持开启', () => {
    expect(resolveFeatures('mp-weixin')).toEqual({ community: false, voice: false })
    expect(resolveFeatures('h5')).toEqual({ community: true, voice: true })
    expect(resolveFeatures(undefined)).toEqual({ community: true, voice: true })
  })

  it('单测环境（无 UNI_PLATFORM）等同 H5，且开关不可运行时篡改', () => {
    expect(FEATURES).toEqual({ community: true, voice: true })
    expect(Object.isFrozen(FEATURES)).toBe(true)
  })

  it('小程序 Tab 只剩 4 个（无厨房圈），与 mp-weixin 的 pages.json tabBar 一致', () => {
    const tabs = tabsFor(resolveFeatures('mp-weixin'))
    expect(tabs.map((t) => t.text)).toEqual(['菜谱', '食集', '咕嘟', '我家余量'])
    const mp = loadPagesJson('MP-WEIXIN')
    expect(mp.tabBar.list.map((x: any) => '/' + x.pagePath)).toEqual(tabs.map((t) => t.path))
  })

  it('mp-weixin 的 pages.json：不含厨房圈页面、pkg-community 分包、语音页与其预加载', () => {
    const mp = loadPagesJson('MP-WEIXIN')
    const all = [
      ...mp.pages.map((p: any) => p.path),
      ...mp.subPackages.flatMap((s: any) => s.pages.map((p: any) => `${s.root}/${p.path}`)),
    ]
    expect(all.some((p: string) => p.includes('community'))).toBe(false)
    expect(all).not.toContain('pkg-agent/Voice')
    expect(all).toContain('pkg-agent/Chat')
    expect(mp.subPackages.map((s: any) => s.root)).not.toContain('pkg-community')
    expect(Object.keys(mp.preloadRule)).toEqual(['pages/agent/Index'])
  })

  it('H5 的 pages.json 保持现状：厨房圈与语音都在', () => {
    const h5 = loadPagesJson('H5')
    expect(h5.pages.map((p: any) => p.path)).toContain('pages/community/Index')
    expect(h5.subPackages.map((s: any) => s.root)).toContain('pkg-community')
    expect(h5.subPackages.find((s: any) => s.root === 'pkg-agent').pages.map((p: any) => p.path)).toContain('Voice')
    expect(h5.tabBar.list).toHaveLength(5)
  })

  it('请求来源头：仅小程序带 X-Client: mp-weixin', () => {
    expect(clientHeader('mp-weixin')).toEqual({ 'X-Client': 'mp-weixin' })
    expect(clientHeader('h5')).toEqual({})
    expect(clientHeader(undefined)).toEqual({})
  })
})
