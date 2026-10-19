import { describe, it, expect, beforeEach, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { TABS, PAGES, isTabPath, go, goProfile, withQuery } from '@/utils/nav'
import { TAB_ICONS } from '@/utils/theme'
import { __testUni } from '../setup'

const pagesJson = JSON.parse(readFileSync(resolve(__dirname, '../../src/pages.json'), 'utf8'))

describe('utils/nav 主导航改版', () => {
  beforeEach(() => {
    vi.mocked(__testUni.mocks.switchTab).mockClear()
    vi.mocked(__testUni.mocks.navigateTo).mockClear()
  })

  it('五个 Tab：菜谱 / 食集 / 咕嘟 / 厨房圈 / 我家余量，咕嘟居中', () => {
    expect(TABS.map((t) => t.text)).toEqual(['菜谱', '食集', '咕嘟', '厨房圈', '我家余量'])
    expect(TABS[2].key).toBe('agent')
  })

  it('pages.json tabBar 与 TABS、TAB_ICONS 顺序一致', () => {
    const list = pagesJson.tabBar.list
    expect(list.map((x: any) => '/' + x.pagePath)).toEqual(TABS.map((t) => t.path))
    expect(list.map((x: any) => x.text)).toEqual(TABS.map((t) => t.text))
    expect([...TAB_ICONS]).toEqual(TABS.map((t) => t.key))
    list.forEach((x: any, i: number) => expect(x.iconPath).toContain(`/${TAB_ICONS[i]}-`))
  })

  it('「我的」「智荐」不再是 Tab；Tab 页都在主包', () => {
    expect(isTabPath(PAGES.profile)).toBe(false)
    expect(isTabPath(PAGES.smart)).toBe(false)
    const main = pagesJson.pages.map((p: any) => '/' + p.path)
    TABS.forEach((t) => expect(main).toContain(t.path))
  })

  it('新模块页面都在分包里登记', () => {
    const sub = (pagesJson.subPackages || []).flatMap((s: any) => s.pages.map((p: any) => `/${s.root}/${p.path}`))
    for (const k of ['agentChat', 'agentVoice', 'post', 'postCreate', 'topic', 'scan', 'scanResult', 'weekPlan', 'nutrition', 'cookMode'] as const) {
      expect(sub).toContain(PAGES[k])
    }
  })

  it('go：Tab 页 switchTab（去掉参数），其他 navigateTo', () => {
    go('/pages/community/Index?x=1')
    expect(__testUni.mocks.switchTab).toHaveBeenCalledWith({ url: '/pages/community/Index' })
    go(withQuery(PAGES.post, { id: 12 }))
    expect(__testUni.mocks.navigateTo).toHaveBeenCalledWith({ url: '/pkg-community/Post?id=12' })
  })

  it('右上角头像 → 我的（navigateTo）', () => {
    goProfile()
    expect(__testUni.mocks.navigateTo).toHaveBeenCalledWith({ url: '/pages/profile/Profile' })
  })

  it('withQuery 跳过空值并编码', () => {
    expect(withQuery('/a', { q: '冰箱里的', n: null, e: '' })).toBe('/a?q=%E5%86%B0%E7%AE%B1%E9%87%8C%E7%9A%84')
  })
})
