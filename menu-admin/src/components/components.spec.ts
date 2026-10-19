import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import DishThumb from './DishThumb.vue'
import IngIcon from './IngIcon.vue'
import AvatarStack from './AvatarStack.vue'
import StatCard from './StatCard.vue'
import HealthCard from './HealthCard.vue'
import PendingApi from './PendingApi.vue'

const stubs = { 'el-icon': { template: '<i><slot /></i>' } }

describe('DishThumb', () => {
  it('有图 → 缩略图地址；加载失败 → 首字占位', async () => {
    const w = mount(DishThumb, { props: { src: '/gudu/uploads/original/a.jpg', name: '滑蛋虾仁' } })
    const img = w.find('img')
    expect(img.attributes('src')).toBe('/gudu/uploads/thumbnail/a.jpg')
    await img.trigger('error')
    expect(w.find('img').exists()).toBe(false)
    expect(w.get('[data-testid="dish-thumb-initial"]').text()).toBe('滑')
  })
  it('无图 → 首字；original 用原图', () => {
    expect(mount(DishThumb, { props: { name: '红烧肉' } }).text()).toBe('红')
    const w = mount(DishThumb, { props: { src: '/x/original/b.jpg', name: 'b', original: true } })
    expect(w.find('img').attributes('src')).toBe('/x/original/b.jpg')
  })
})

describe('IngIcon', () => {
  it('按名称匹配插画；匹配不到退首字', () => {
    const w = mount(IngIcon, { props: { name: '鲈鱼' } })
    expect(w.attributes('data-icon')).toBe('fish')
    expect(w.find('img').exists()).toBe(true)
    const none = mount(IngIcon, { props: { name: '秋葵' } })
    expect(none.attributes('data-icon')).toBe('initial')
    expect(none.text()).toBe('秋')
  })
  it('品类兜底与去色', () => {
    const w = mount(IngIcon, { props: { name: '秋葵', categoryName: '蔬菜', muted: true } })
    expect(w.attributes('data-icon')).toBe('cabbage')
    expect(w.classes()).toContain('muted')
  })
})

describe('AvatarStack', () => {
  it('最多 max 个，超出 +N；无头像首字', () => {
    const people = ['小王', '林林', '妈妈', '朵朵', '爸爸'].map((name) => ({ name }))
    const w = mount(AvatarStack, { props: { people, max: 3 } })
    const avs = w.findAll('.av')
    expect(avs).toHaveLength(4)
    expect(avs[0].text()).toBe('小')
    expect(avs[3].text()).toBe('+2')
  })
  it('有头像 → img', () => {
    const w = mount(AvatarStack, { props: { people: [{ name: 'a', avatarUrl: '/a.png' }] } })
    expect(w.find('img').attributes('src')).toBe('/a.png')
  })
})

describe('StatCard / HealthCard / PendingApi', () => {
  it('StatCard 可点击时触发 click，不可点击不触发', async () => {
    const w = mount(StatCard, { props: { label: '账号', value: '12', hint: '近 7 天 +1', clickable: true }, global: { stubs } })
    expect(w.text()).toContain('账号')
    expect(w.text()).toContain('近 7 天 +1')
    await w.trigger('click')
    expect(w.emitted('click')).toHaveLength(1)
    const n = mount(StatCard, { props: { label: 'x', value: 1 }, global: { stubs } })
    await n.trigger('click')
    expect(n.emitted('click')).toBeUndefined()
  })
  it('HealthCard pending 显示「待接入」', () => {
    const w = mount(HealthCard, { props: { title: 'Redis', status: 'pending' } })
    expect(w.text()).toContain('待接入')
    expect(w.text()).toContain('接口待上线')
  })
  it('PendingApi 列出依赖与任务号', () => {
    const w = mount(PendingApi, { props: { needs: 'audit_log', task: '3.9' } })
    expect(w.text()).toContain('接口待上线')
    expect(w.text()).toContain('需要：audit_log')
    expect(w.text()).toContain('计划：3.9')
  })
})
