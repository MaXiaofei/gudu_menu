import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createApp, nextTick, type App } from 'vue'

// 菜谱详情页 x 公共菜谱下架（V101）：已下架显示「已下架」标记，隐藏「加入食集」「复制到我家」；
// 做菜 / 跟做照常；上架的公共菜谱与私有菜谱不受影响。

let loadHook: ((opts?: Record<string, string>) => Promise<void>) | null = null
vi.mock('@dcloudio/uni-app', () => ({
  onLoad: (fn: any) => {
    loadHook = fn
  },
  onShow: vi.fn(),
  onShareAppMessage: vi.fn(),
}))

const dishApi = vi.hoisted(() => ({
  dishDetail: vi.fn(),
  forkDish: vi.fn(),
}))
vi.mock('@/api/dish', async (orig) => {
  const mod: any = await orig()
  return { ...mod, dishDetail: dishApi.dishDetail, forkDish: dishApi.forkDish }
})
vi.mock('@/api/cook', () => ({ dishCookMaterials: vi.fn(), cookDish: vi.fn() }))
vi.mock('@/api/pantry', () => ({ listGroupedAll: vi.fn().mockResolvedValue({ items: [] }) }))
vi.mock('@/api/shopping', () => ({ listShopping: vi.fn(), createEmptyList: vi.fn(), addCustomItem: vi.fn() }))
vi.mock('@/api/menu', () => ({ listMenus: vi.fn().mockResolvedValue({ records: [] }), saveMenu: vi.fn(), addMenuDishes: vi.fn() }))
vi.mock('@/utils/loginRedirect', () => ({ launchAuthDone: vi.fn().mockResolvedValue(undefined) }))
vi.mock('@/utils/guest', () => ({ isLoggedIn: () => false, requireLogin: () => true }))
vi.mock('@/utils/token', () => ({ navInset: () => 64 }))

import Detail from '@/pages/dish/Detail.vue'

const flush = async () => {
  for (let i = 0; i < 8; i++) {
    await Promise.resolve()
    await nextTick()
  }
}

let app: App | null = null
let root: HTMLElement

function detailOf(dish: Record<string, unknown>) {
  return {
    dish: { id: 1, name: '番茄炒蛋', prepTime: 5, cookTime: 10, ...dish },
    steps: [{ text: '热锅' }],
    ingredients: [],
    cuisineIds: [],
    tagIds: [],
    categoryIds: [],
  }
}

async function mountDetail(dish: Record<string, unknown>) {
  dishApi.dishDetail.mockResolvedValue(detailOf(dish))
  root = document.createElement('div')
  document.body.appendChild(root)
  app = createApp(Detail)
  app.config.warnHandler = () => {}
  app.mount(root)
  await flush()
  await loadHook?.({ id: '1' })
  await flush()
}

const q = (id: string) => root.querySelector(`[data-testid="${id}"]`)

beforeEach(() => {
  dishApi.dishDetail.mockReset()
  ;(globalThis as any).getCurrentPages = () => [{}]
})
afterEach(() => {
  app?.unmount()
  root?.remove()
  app = null
})

describe('菜谱详情 x 下架', () => {
  it('已下架的公共菜谱：显示「已下架」，隐藏加入食集与复制到我家，做菜 / 跟做仍在', async () => {
    await mountDetail({ familyId: null, offline: true })
    expect(q('dish-offline-tag')?.textContent).toContain('已下架')
    expect(q('dish-offline-tip')?.textContent).toContain('不受影响')
    expect(q('dish-add-menu')).toBeNull()
    expect(q('dish-fork')).toBeNull()
    expect(q('cook-mode')).not.toBeNull()
    expect(q('cook-now')).not.toBeNull()
  })

  it('上架的公共菜谱：没有下架标记，加入食集与复制到我家都在', async () => {
    await mountDetail({ familyId: null, offline: false })
    expect(q('dish-offline-tag')).toBeNull()
    expect(q('dish-offline-tip')).toBeNull()
    expect(q('dish-add-menu')).not.toBeNull()
    expect(q('dish-fork')).not.toBeNull()
  })

  it('旧后端没有 offline 字段 / 家庭私有菜谱：按未下架处理', async () => {
    await mountDetail({ familyId: 7 })
    expect(q('dish-offline-tag')).toBeNull()
    expect(q('dish-add-menu')).not.toBeNull()
    expect(q('dish-fork')).toBeNull() // 私有菜谱本来就没有「复制到我家」
  })
})
