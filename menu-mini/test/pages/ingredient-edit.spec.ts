import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createApp, nextTick, type App } from 'vue'

// pages/ingredient/Edit.vue：全局食材库由平台运营维护，普通用户只读（无保存/删除入口），运营可改删。

let loadHook: ((opts?: Record<string, string>) => Promise<void>) | null = null
vi.mock('@dcloudio/uni-app', () => ({
  onLoad: (fn: any) => {
    loadHook = fn
  },
}))

const api = vi.hoisted(() => ({
  ingredientDetail: vi.fn(),
  updateIngredient: vi.fn(),
  deleteIngredient: vi.fn(),
}))
vi.mock('@/api/ingredient', () => api)
const account = vi.hoisted(() => ({ accountMe: vi.fn() }))
vi.mock('@/api/account', () => account)

import Edit from '@/pages/ingredient/Edit.vue'

const flush = async () => {
  for (let i = 0; i < 5; i++) {
    await Promise.resolve()
    await nextTick()
  }
}

describe('pages/ingredient/Edit.vue 权限', () => {
  let app: App | null = null
  let root: HTMLElement

  async function mount(role: string) {
    account.accountMe.mockResolvedValue({ account: { platformRole: role } })
    root = document.createElement('div')
    document.body.appendChild(root)
    app = createApp(Edit)
    app.config.warnHandler = () => {}
    app.mount(root)
    await loadHook!({ id: '1' })
    await flush()
  }

  beforeEach(() => {
    Object.values(api).forEach((f) => f.mockReset())
    api.ingredientDetail.mockResolvedValue({ id: 1, name: '鸡蛋', edible: 1, categoryName: '蛋类' })
    api.updateIngredient.mockResolvedValue(undefined)
  })

  afterEach(() => {
    app?.unmount()
    root.remove()
  })

  it('普通用户：无保存按钮、无删除入口，显示平台维护说明', async () => {
    await mount('USER')
    expect(Array.from(root.querySelectorAll('button')).some((b) => b.textContent?.includes('保存'))).toBe(false)
    expect(root.textContent).toContain('食材库由平台维护')
    expect(api.updateIngredient).not.toHaveBeenCalled()
  })

  it('运营：有保存按钮与删除入口', async () => {
    await mount('OPERATOR')
    const save = Array.from(root.querySelectorAll('button')).find((b) => b.textContent?.includes('保存'))
    expect(save).toBeTruthy()
    expect(root.textContent).not.toContain('食材库由平台维护')
    save!.click()
    await flush()
    expect(api.updateIngredient).toHaveBeenCalledWith(1, 1)
  })
})
