import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import LoadError from './LoadError.vue'

describe('LoadError 加载失败 + 重试', () => {
  it('显示标题与原因，点重试触发 retry', async () => {
    const w = mount(LoadError, { props: { message: '网络异常' }, global: { plugins: [ElementPlus] } })
    expect(w.text()).toContain('加载失败')
    expect(w.get('[data-testid="load-error-msg"]').text()).toBe('网络异常')
    await w.get('[data-testid="load-error-retry"]').trigger('click')
    expect(w.emitted('retry')).toHaveLength(1)
  })
  it('无原因时不渲染原因行；可自定义标题与紧凑样式', () => {
    const w = mount(LoadError, { props: { title: '统计加载失败', compact: true }, global: { plugins: [ElementPlus] } })
    expect(w.find('[data-testid="load-error-msg"]').exists()).toBe(false)
    expect(w.text()).toContain('统计加载失败')
    expect(w.classes()).toContain('compact')
  })
})
