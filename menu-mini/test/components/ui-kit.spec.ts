import { describe, it, expect, afterEach } from 'vitest'
import { createApp, h, nextTick, ref, type App, type Component } from 'vue'
import UiDishImage from '@/components/ui-dish-image.vue'
import UiIngIcon from '@/components/ui-ing-icon.vue'
import UiSegment from '@/components/ui-segment.vue'
import UiState from '@/components/ui-state.vue'
import UiAvatarStack from '@/components/ui-avatar-stack.vue'
import UiStatGrid from '@/components/ui-stat-grid.vue'
import UiCookSheet from '@/components/ui-cook-sheet.vue'
import UiDishCollage from '@/components/ui-dish-collage.vue'

// prototype-ui-rollout 0.5：小程序共享组件（jsdom 下 uni 内置组件按自定义元素渲染，断言 DOM 结构与行为）。
let app: App | null = null
let root: HTMLElement

async function mount(comp: Component, props: Record<string, unknown> = {}) {
  root = document.createElement('div')
  document.body.appendChild(root)
  app = createApp({ render: () => h(comp, props) })
  app.config.warnHandler = () => {}
  app.mount(root)
  await nextTick()
  return root
}
afterEach(() => {
  app?.unmount()
  root?.remove()
  app = null
})

describe('ui-dish-image', () => {
  it('有图：渲染缩略图地址（/original/ → /thumbnail/）', async () => {
    const r = await mount(UiDishImage, { src: 'https://x.test/original/a.jpg', name: '红烧肉' })
    expect(r.querySelector('image')?.getAttribute('src')).toBe('https://x.test/thumbnail/a.jpg')
  })
  it('原图位：original=true 不转缩略图', async () => {
    const r = await mount(UiDishImage, { src: 'https://x.test/original/a.jpg', original: true })
    expect(r.querySelector('image')?.getAttribute('src')).toBe('https://x.test/original/a.jpg')
  })
  it('无图：回退菜名首字，不留白', async () => {
    const r = await mount(UiDishImage, { src: '', name: '红烧肉' })
    expect(r.querySelector('image')).toBeNull()
    expect(r.textContent).toContain('红')
  })
  it('比例：4:3 用 padding-top 撑高', async () => {
    const r = await mount(UiDishImage, { src: 'a.jpg', ratio: '4:3' })
    expect((r.firstElementChild as HTMLElement).style.paddingTop).toBe('75%')
  })
})

describe('ui-ing-icon', () => {
  it('iconKey 命中素材', async () => {
    const r = await mount(UiIngIcon, { iconKey: 'tomato', name: '番茄' })
    expect(r.querySelector('image')?.getAttribute('src')).toBe('/static/ing/tomato.png')
  })
  it('无 key 按品类兜底；都不命中显示首字', async () => {
    let r = await mount(UiIngIcon, { name: '秋葵', category: '蔬菜' })
    expect(r.querySelector('image')?.getAttribute('src')).toBe('/static/ing/cabbage.png')
    app!.unmount()
    root.remove()
    r = await mount(UiIngIcon, { name: '秋葵' })
    expect(r.querySelector('image')).toBeNull()
    expect(r.textContent).toContain('秋')
  })
  it('用完：去色态 class', async () => {
    const r = await mount(UiIngIcon, { iconKey: 'egg', out: true })
    expect(r.firstElementChild!.className).toContain('out')
  })
})

describe('ui-segment', () => {
  it('点击切换 emit update:modelValue；徽标显示', async () => {
    const v = ref('dish')
    root = document.createElement('div')
    document.body.appendChild(root)
    app = createApp({
      render: () =>
        h(UiSegment, {
          modelValue: v.value,
          'onUpdate:modelValue': (x: string) => (v.value = x),
          items: [{ key: 'dish', label: '菜', badge: 4 }, { key: 'prep', label: '备菜', badge: '5/8' }],
        }),
    })
    app.mount(root)
    await nextTick()
    expect(root.textContent).toContain('5/8')
    ;(root.querySelector('[data-testid="seg-prep"]') as HTMLElement).click()
    await nextTick()
    expect(v.value).toBe('prep')
    expect(root.querySelector('[data-testid="seg-prep"]')!.className).toContain('on')
  })
})

describe('ui-state', () => {
  it('错误态：重试按钮 + 指定报错编号', async () => {
    const r = await mount(UiState, { mode: 'error', retry: true, errorId: '7f3a-91c2' })
    expect(r.querySelector('[data-testid="state-retry"]')).not.toBeNull()
    expect(r.querySelector('[data-testid="state-error-id"]')!.textContent).toContain('7f3a-91c2')
  })
  it('空态：插画 + 文案 + 下一步按钮', async () => {
    const r = await mount(UiState, { mode: 'empty', text: '冰箱里还是空的', actionText: '手动添加', illus: ['egg'] })
    expect(r.textContent).toContain('冰箱里还是空的')
    expect(r.textContent).toContain('手动添加')
    expect(r.querySelector('image')?.getAttribute('src')).toBe('/static/ing/egg.png')
  })
  it('加载态：骨架屏而非转圈', async () => {
    const r = await mount(UiState, { mode: 'loading' })
    expect(r.querySelector('.usk')).not.toBeNull()
  })
})

describe('ui-avatar-stack / ui-stat-grid / ui-dish-collage', () => {
  it('头像叠放：超出 max 显示 +N；宝宝档案用宝宝插画', async () => {
    const r = await mount(UiAvatarStack, { items: [{ name: '朵朵', tags: ['宝宝辅食'] }, { name: 'a' }, { name: 'b' }, { name: 'c' }, { name: 'd' }], max: 3 })
    expect(r.textContent).toContain('+2')
    expect(r.querySelector('image')?.getAttribute('src')).toBe('/static/avatar/duoduo.png')
  })
  it('统计格：值与标签', async () => {
    const r = await mount(UiStatGrid, { items: [{ value: 24, label: '充足', tone: 'success' }, { value: 3, label: '用完', tone: 'error' }] })
    expect(r.textContent).toContain('24')
    expect(r.querySelectorAll('.cell').length).toBe(2)
  })
  it('拼图：≥4 张时最后一格显示 +N', async () => {
    const r = await mount(UiDishCollage, { items: [{ cover: 'a.jpg' }, { cover: 'b.jpg' }, { cover: 'c.jpg' }, { cover: 'd.jpg' }, { cover: 'e.jpg' }] })
    expect(r.textContent).toContain('+2')
  })
})

describe('ui-cook-sheet', () => {
  it('默认：食材「用完」、调料「剩一些」、家里没有的「没动」；提交带人份与食记', async () => {
    let payload: any = null
    const r = await mount(UiCookSheet, {
      items: [
        { ingredientId: 1, ingredientName: '鲈鱼', level: 'ENOUGH' },
        { ingredientId: 2, ingredientName: '豉油', level: 'ENOUGH', isCondiment: true },
        { ingredientId: 3, ingredientName: '小葱', level: 'NONE' },
      ],
      resultSub: '清蒸鲈鱼',
      showServings: true,
      onSubmit: (p: any) => (payload = p),
    })
    expect(r.textContent).toContain('清蒸鲈鱼做好了')
    ;(r.querySelector('[aria-label="增加"]') as HTMLElement).click()
    await nextTick()
    const ok = Array.from(r.querySelectorAll('button')).find((b) => b.textContent?.includes('记一笔')) as HTMLElement
    ok.click()
    expect(payload).toEqual({ usedUp: [1], partiallyUsed: [2], servings: 2, note: '' })
  })
})
