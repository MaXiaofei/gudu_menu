import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createApp, h, nextTick, type App, type Component } from 'vue'
import UiOwnTag from '@/components/ui-own-tag.vue'

// pages/ingredient/Edit.vue：按归属显示权限——
// 本家私有食材可改名 / 改分类 / 改食用属性 / 删除；公共食材只读，提示「平台食材，由平台维护」。

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
vi.mock('@/api/common', () => ({ listDict: vi.fn().mockResolvedValue([{ id: 3, name: '蔬菜' }, { id: 4, name: '调味料' }]) }))

import Edit from '@/pages/ingredient/Edit.vue'

const flush = async () => {
  for (let i = 0; i < 5; i++) {
    await Promise.resolve()
    await nextTick()
  }
}

describe('pages/ingredient/Edit.vue 按归属的权限', () => {
  let app: App | null = null
  let root: HTMLElement

  async function mount(detail: Record<string, unknown>) {
    api.ingredientDetail.mockResolvedValue(detail)
    root = document.createElement('div')
    document.body.appendChild(root)
    app = createApp(Edit)
    app.config.warnHandler = () => {}
    app.component('ui-own-tag', UiOwnTag as Component)
    // 返回头：只需要把 action 插槽渲染出来（删除入口在里面）
    app.component('ui-back-header', { render() { return h('div', (this as any).$slots.action?.()) } })
    app.mount(root)
    await loadHook!({ id: '1' })
    await flush()
  }

  const saveBtn = () => Array.from(root.querySelectorAll('button')).find((b) => b.textContent?.includes('保存'))

  beforeEach(() => {
    Object.values(api).forEach((f) => f.mockReset())
    api.updateIngredient.mockResolvedValue(undefined)
    api.deleteIngredient.mockResolvedValue(undefined)
  })

  afterEach(() => {
    app?.unmount()
    root.remove()
  })

  it('公共食材：只读，无保存 / 删除 / 改名，提示由平台维护', async () => {
    await mount({ id: 1, name: '鸡蛋', edible: 1, categoryName: '蛋类', familyOwned: false })
    expect(saveBtn()).toBeUndefined()
    expect(root.querySelector('[data-testid="ing-delete"]')).toBeNull()
    expect(root.querySelector('[data-testid="ing-name"]')).toBeNull()
    expect(root.querySelector('[data-testid="readonly-tip"]')?.textContent).toBe('平台食材，由平台维护')
    expect(root.querySelector('[data-testid="platform-tag"]')).not.toBeNull()
    expect(api.updateIngredient).not.toHaveBeenCalled()
  })

  it('本家食材：带「我家」标签，可改名 / 分类 / 食用属性，保存提交整包', async () => {
    await mount({ id: 1, name: '家传酱', edible: 2, purchaseCategoryId: 4, categoryName: '调味料', familyOwned: true })
    expect(root.querySelector('[data-testid="own-tag"]')?.textContent).toBe('我家')
    expect(root.querySelector('[data-testid="readonly-tip"]')).toBeNull()
    const ipt = root.querySelector('[data-testid="ing-name"]') as HTMLInputElement
    expect(ipt).not.toBeNull()
    ipt.value = '家传辣酱'
    ipt.dispatchEvent(new Event('input'))
    await flush()
    saveBtn()!.click()
    await flush()
    expect(api.updateIngredient).toHaveBeenCalledWith(1, { name: '家传辣酱', purchaseCategoryId: 4, edible: 2 })
  })

  it('本家食材：删除入口存在，确认后调用删除', async () => {
    await mount({ id: 1, name: '家传酱', edible: 1, familyOwned: true })
    const showModal = (globalThis as any).uni.showModal as ReturnType<typeof vi.fn>
    showModal.mockImplementation(({ success }: any) => success({ confirm: true }))
    ;(root.querySelector('[data-testid="ing-delete"]') as HTMLElement).click()
    await flush()
    expect(api.deleteIngredient).toHaveBeenCalledWith(1)
  })

  it('本家食材：删除在用被后端拒绝时不关闭页面（提示由请求层展示）', async () => {
    await mount({ id: 1, name: '家传酱', edible: 1, familyOwned: true })
    api.deleteIngredient.mockRejectedValue(new Error('这个食材正在使用（库存/采购），不能删除'))
    const showModal = (globalThis as any).uni.showModal as ReturnType<typeof vi.fn>
    showModal.mockImplementation(({ success }: any) => success({ confirm: true }))
    const toast = (globalThis as any).uni.showToast as ReturnType<typeof vi.fn>
    toast.mockClear()
    ;(root.querySelector('[data-testid="ing-delete"]') as HTMLElement).click()
    await flush()
    expect(toast).not.toHaveBeenCalledWith(expect.objectContaining({ title: '已删除' }))
  })
})
