import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createApp, nextTick, type App, type Component } from 'vue'
import UiOwnTag from '@/components/ui-own-tag.vue'

// 食材库列表（「我家」标签 + 分类筛选里的「我家」）、新建页（不带营养 / 命中公共时提示）、
// 选食材面板（标签 + 别名命中 + 新建提示）。

let showHook: (() => void) | null = null
let loadHook: ((opts?: Record<string, string>) => Promise<void>) | null = null
vi.mock('@dcloudio/uni-app', () => ({
  onShow: (fn: any) => {
    showHook = fn
  },
  onLoad: (fn: any) => {
    loadHook = fn
  },
}))

const api = vi.hoisted(() => ({
  listIngredients: vi.fn(),
  resolveIngredient: vi.fn(),
  searchIngredients: vi.fn(),
  upsertDict: vi.fn(),
}))
vi.mock('@/api/ingredient', () => api)
vi.mock('@/api/common', () => ({
  listDict: vi.fn().mockResolvedValue([{ id: 3, name: '蔬菜' }]),
}))

import List from '@/pages/ingredient/List.vue'
import Create from '@/pages/ingredient/Create.vue'
import Picker from '@/pages/dish/parts/IngredientPicker.vue'

const flush = async () => {
  for (let i = 0; i < 6; i++) {
    await Promise.resolve()
    await nextTick()
  }
}

let app: App | null = null
let root: HTMLElement

async function mount(comp: Component, props: Record<string, unknown> = {}) {
  root = document.createElement('div')
  document.body.appendChild(root)
  app = createApp(comp, props)
  app.config.warnHandler = () => {}
  app.component('ui-own-tag', UiOwnTag as Component)
  app.mount(root)
  await flush()
}

beforeEach(() => {
  Object.values(api).forEach((f) => f.mockReset())
  const uni = (globalThis as any).uni
  uni.showToast.mockClear()
})
afterEach(() => {
  app?.unmount()
  root?.remove()
  app = null
})

describe('pages/ingredient/List.vue', () => {
  beforeEach(() => {
    api.listIngredients.mockResolvedValue({
      records: [
        { id: 1, name: '番茄', categoryName: '蔬菜', familyOwned: false },
        { id: 2, name: '家传酱', categoryName: '调味料', familyOwned: true },
        { id: 3, name: '马铃薯', categoryName: '蔬菜', matchedAlias: '土豆' },
      ],
      total: 3,
    })
  })

  it('本家食材带「我家」小标签，公共没有；别名命中显示别名', async () => {
    await mount(List)
    showHook!()
    await flush()
    const rows = Array.from(root.querySelectorAll('.row'))
    expect(rows.length).toBe(3)
    expect(rows[0].querySelector('[data-testid="own-tag"]')).toBeNull()
    expect(rows[1].querySelector('[data-testid="own-tag"]')?.textContent).toBe('我家')
    expect(rows[2].textContent).toContain('别名：土豆')
  })

  it('分类筛选里有「我家」，点击后只看我家（familyOnly）且不带分类', async () => {
    await mount(List)
    showHook!()
    await flush()
    const chip = Array.from(root.querySelectorAll('.chip')).find((c) => c.textContent === '我家') as HTMLElement
    expect(chip).toBeTruthy()
    expect(root.textContent).not.toContain('我家新建')
    chip.click()
    await flush()
    const last = api.listIngredients.mock.calls.at(-1)![0]
    expect(last.familyOnly).toBe(true)
    expect(last.purchaseCategoryId).toBeUndefined()
  })

  it('选真实分类时不带 familyOnly', async () => {
    await mount(List)
    showHook!()
    await flush()
    ;(Array.from(root.querySelectorAll('.chip')).find((c) => c.textContent === '蔬菜') as HTMLElement).click()
    await flush()
    const last = api.listIngredients.mock.calls.at(-1)![0]
    expect(last.purchaseCategoryId).toBe(3)
    expect(last.familyOnly).toBeUndefined()
  })
})

describe('pages/ingredient/Create.vue', () => {
  it('保存：走 resolve（不发营养）；命中公共食材时提示「已有：马铃薯」', async () => {
    api.resolveIngredient.mockResolvedValue({ id: 9, name: '马铃薯', existed: 'public', familyOwned: false })
    await mount(Create)
    await loadHook!()
    await flush()
    const ipt = root.querySelector('.field-ipt') as HTMLInputElement
    ipt.value = '土豆'
    ipt.dispatchEvent(new Event('input'))
    await flush()
    ;(root.querySelector('button') as HTMLElement).click()
    await flush()
    expect(api.resolveIngredient).toHaveBeenCalledWith('土豆', undefined)
    expect((globalThis as any).uni.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '已有：马铃薯' }))
  })
})

describe('pages/dish/parts/IngredientPicker.vue', () => {
  const all = [{ id: 1, name: '番茄' }, { id: 2, name: '家传酱', familyOwned: true }]

  it('匹配行：本家带「我家」标签', async () => {
    api.searchIngredients.mockResolvedValue([])
    await mount(Picker, { all, hot: [] })
    const ipt = root.querySelector('.sheet-search') as HTMLInputElement
    ipt.value = '酱'
    ipt.dispatchEvent(new Event('input'))
    await flush()
    const rows = root.querySelectorAll('.match-row')
    expect(rows.length).toBe(1)
    expect(rows[0].querySelector('[data-testid="own-tag"]')?.textContent).toBe('我家')
  })

  it('新建时后端返回已有公共食材：提示并发出 created 直接选中', async () => {
    api.searchIngredients.mockResolvedValue([])
    api.resolveIngredient.mockResolvedValue({ id: 9, name: '马铃薯', existed: 'public', familyOwned: false })
    const created = vi.fn()
    await mount(Picker, { all, hot: [], onCreated: created })
    ;(root.querySelector('.sheet-bottom-row button') as HTMLElement).click()
    await flush()
    const ipt = root.querySelector('.dialog-ipt') as HTMLInputElement
    ipt.value = '土豆'
    ipt.dispatchEvent(new Event('input'))
    await flush()
    ;(root.querySelector('.dialog-btn.primary') as HTMLElement).click()
    await flush()
    expect(created).toHaveBeenCalledWith({ id: 9, name: '马铃薯', familyOwned: false })
    expect((globalThis as any).uni.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '已有：马铃薯' }))
  })
})
