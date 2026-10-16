import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createApp, nextTick, type App } from 'vue'

// pages/dish/Create.vue（S14 线上 bug 回归）：
// 1. 从草稿继续编辑并发布成功后，必须调用 deleteDraft 删除该草稿（曾因漏 import 抛 ReferenceError）
// 2. 存草稿 / 发布时 difficulty 以数值传给后端（曾误传 Ref 对象）

let loadHook: ((opts?: Record<string, string>) => Promise<void>) | null = null
vi.mock('@dcloudio/uni-app', () => ({
  onLoad: (fn: any) => {
    loadHook = fn
  },
}))

const api = vi.hoisted(() => ({
  saveDish: vi.fn(),
  saveDraft: vi.fn(),
  draftDetail: vi.fn(),
  deleteDraft: vi.fn(),
  importDishByUrl: vi.fn(),
}))
vi.mock('@/api/dish', () => api)
vi.mock('@/api/common', () => ({ listDict: vi.fn().mockResolvedValue([]) }))
vi.mock('@/api/ingredient', () => ({
  listAllIngredients: vi.fn().mockResolvedValue([]),
  createIngredient: vi.fn(),
}))
vi.mock('@/api/upload', () => ({ chooseImages: vi.fn(), uploadImage: vi.fn() }))
vi.mock('@/store/preview', () => ({ usePreviewStore: () => ({ set: vi.fn() }) }))

import Create from '@/pages/dish/Create.vue'

const flush = async () => {
  for (let i = 0; i < 5; i++) {
    await Promise.resolve()
    await nextTick()
  }
}

function clickButton(root: HTMLElement, text: string) {
  const btn = Array.from(root.querySelectorAll('button')).find((b) => b.textContent?.trim() === text)
  if (!btn) throw new Error(`按钮「${text}」未找到`)
  btn.click()
}

describe('pages/dish/Create.vue', () => {
  let app: App | null = null
  let root: HTMLElement

  beforeEach(async () => {
    vi.useFakeTimers({ toFake: ['setTimeout'] })
    Object.values(api).forEach((f) => f.mockReset())
    api.draftDetail.mockResolvedValue({
      name: '番茄炒蛋',
      difficulty: 4,
      tagIds: [3],
      cuisineIds: [],
      ingredients: [],
      steps: [],
    })
    api.saveDish.mockResolvedValue(99)
    api.saveDraft.mockResolvedValue(7)
    api.deleteDraft.mockResolvedValue(undefined)

    root = document.createElement('div')
    document.body.appendChild(root)
    app = createApp(Create)
    app.config.warnHandler = () => {} // <view>/<ui-avatar> 等 uni 组件在 jsdom 下未注册，忽略告警
    app.mount(root)
    await loadHook!({ draftId: '7' })
    await flush()
  })

  afterEach(() => {
    app?.unmount()
    root.remove()
    vi.useRealTimers()
  })

  it('从草稿发布成功 → 调用 deleteDraft(草稿 id)', async () => {
    clickButton(root, '发布')
    await flush()
    expect(api.saveDish).toHaveBeenCalledTimes(1)
    expect(api.deleteDraft).toHaveBeenCalledWith(7)
  })

  it('发布：difficulty 以数值传递', async () => {
    clickButton(root, '发布')
    await flush()
    const body = api.saveDish.mock.calls[0][0]
    expect(body.dish.difficulty).toBe(4)
    expect(typeof body.dish.difficulty).toBe('number')
  })

  it('发布失败 → 不删除草稿', async () => {
    api.saveDish.mockRejectedValue(new Error('boom'))
    clickButton(root, '发布')
    await flush()
    expect(api.deleteDraft).not.toHaveBeenCalled()
  })

  it('存草稿：difficulty 以数值传递，并带上草稿 id', async () => {
    clickButton(root, '存草稿')
    await flush()
    const body = api.saveDraft.mock.calls[0][0]
    expect(body.id).toBe(7)
    expect(body.dish.difficulty).toBe(4)
    expect(typeof body.dish.difficulty).toBe('number')
  })
})
