import { describe, it, expect } from 'vitest'
import { installHttpMock } from '../helpers/http'
import {
  apiTypeOf, feedbackStatusColor, feedbackStatusText, feedbackTypeText, getMyFeedback, listMyFeedback, submitFeedback,
} from '@/api/feedback'
import { noticeGroup, noticeTarget, NOTICE_FEEDBACK_REPLY } from '@/api/notification'
import { getPreference, setPersonalizedRecommend, PERSONALIZED_HINT_OFF } from '@/api/preference'
import { heroCandidates, recommendHome } from '@/api/recommend'
import type { DishCard } from '@/api/dish'

// mini-release-compliance A.11 意见反馈闭环 + A.8 个性化推荐开关
describe('A.11 api/feedback', () => {
  it('AI 反馈：type=ai + sourceRef 独立字段（不再塞 clientInfo 前缀）', async () => {
    const http = installHttpMock({ 'POST /feedback': 9 })
    await submitFeedback({ content: 'AI 的做法不对哦', type: 'ai', sourceRef: ' agent:m1 ', clientInfo: 'v1 / mp-weixin' })
    expect(http.calls[0].data).toEqual({
      content: 'AI 的做法不对哦', type: 'ai', sourceRef: 'agent:m1', clientInfo: 'v1 / mp-weixin',
    })
  })

  it('空 sourceRef 不传；超长截到 128', async () => {
    const http = installHttpMock({ 'POST /feedback': 1 })
    await submitFeedback({ content: '想要导出菜谱', type: 'general', sourceRef: '' })
    expect(http.calls[0].data).toEqual({ content: '想要导出菜谱', type: 'general' })
    await submitFeedback({ content: '想要导出菜谱', type: 'ai', sourceRef: 'x'.repeat(200) })
    expect((http.calls[1].data as any).sourceRef).toHaveLength(128)
  })

  it('页面分类 → 后端类型：出错了/想要功能/菜谱有误/其他 = general', () => {
    expect(apiTypeOf('bug')).toBe('general')
    expect(apiTypeOf('other')).toBe('general')
    expect(apiTypeOf('ai')).toBe('ai')
    expect(apiTypeOf('complaint')).toBe('complaint')
    expect(apiTypeOf(undefined)).toBe('general')
  })

  it('我的反馈：分页参数、缺省补齐；详情按 id', async () => {
    const http = installHttpMock({
      'GET /feedback/mine': { records: [{ id: 3, type: 'ai', content: '内容够长了', status: 'REPLIED', reply: '已修复', createTime: '2026-10-26T10:00:00' }], total: 1 },
      'GET /feedback/mine/3': { id: 3, content: '内容够长了', status: 'DONE', createTime: '2026-10-26T10:00:00' },
    })
    const p = await listMyFeedback(2, 10)
    expect(http.calls[0].data).toEqual({ pageNum: 2, pageSize: 10 })
    expect(p.records[0].reply).toBe('已修复')
    expect(p.total).toBe(1)
    expect((await getMyFeedback(3)).status).toBe('DONE')
  })

  it('我的反馈：后端返回空 → 空列表', async () => {
    installHttpMock({ 'GET /feedback/mine': null })
    expect(await listMyFeedback()).toEqual({ records: [], total: 0 })
  })

  it('状态 / 类型文案与颜色（颜色走 CSS 变量）', () => {
    expect(feedbackStatusText('NEW')).toBe('处理中')
    expect(feedbackStatusText('REPLIED')).toBe('已回复')
    expect(feedbackStatusText('DONE')).toBe('已处理')
    expect(feedbackStatusColor('REPLIED')).toBe('var(--info)')
    expect(feedbackStatusColor('NEW')).toMatch(/^var\(--/)
    expect(feedbackTypeText('complaint')).toBe('投诉举报')
    expect(feedbackTypeText(null)).toBe('意见建议')
  })
})

describe('A.11 api/notification 反馈回复消息', () => {
  it('feedback_reply → 反馈详情；refId 非数字退到我的反馈；归系统 Tab', () => {
    expect(noticeTarget({ type: NOTICE_FEEDBACK_REPLY, refId: '12' }))
      .toEqual({ tab: false, url: '/pages/profile/FeedbackDetail?id=12' })
    expect(noticeTarget({ type: NOTICE_FEEDBACK_REPLY, refId: null }))
      .toEqual({ tab: false, url: '/pages/profile/FeedbackMine' })
    expect(noticeGroup(NOTICE_FEEDBACK_REPLY)).toBe('system')
  })

  it('原有类型跳转不变；未知类型不跳', () => {
    expect(noticeTarget({ type: 'expiry' })).toEqual({ tab: true, url: '/pages/agent/Index' })
    expect(noticeTarget({ type: 'shopping' })).toEqual({ tab: false, url: '/pages/shopping/List' })
    expect(noticeTarget({ type: 'other' })).toBeNull()
  })
})

describe('A.8 api/preference', () => {
  it('GET /preference：缺字段按默认开启', async () => {
    installHttpMock({ 'GET /preference': null })
    expect(await getPreference()).toEqual({ personalizedRecommend: true })
    installHttpMock({ 'GET /preference': { personalizedRecommend: false } })
    expect(await getPreference()).toEqual({ personalizedRecommend: false })
  })

  it('PUT /preference 透传开关', async () => {
    const http = installHttpMock({ 'PUT /preference': { personalizedRecommend: false } })
    expect(await setPersonalizedRecommend(false)).toEqual({ personalizedRecommend: false })
    expect(http.calls[0].data).toEqual({ personalizedRecommend: false })
    expect(PERSONALIZED_HINT_OFF).toContain('不使用你的成员档案')
  })
})

describe('A.8 api/recommend 通用推荐', () => {
  const card = (id: number): DishCard => ({ dishId: id, name: `菜${id}` } as DishCard)

  it('personalized 缺省为 true，popular 缺省为空', async () => {
    installHttpMock({ 'GET /recommend/home': { seasonal: [card(1)] } })
    const h = await recommendHome()
    expect(h.personalized).toBe(true)
    expect(h.popular).toEqual([])
  })

  it('关闭个性化：首推用大家常做，其次应季；不看临期/常做', async () => {
    installHttpMock({ 'GET /recommend/home': { personalized: false, seasonal: [card(1)], popular: [card(2)] } })
    const h = await recommendHome()
    expect(h.personalized).toBe(false)
    expect(heroCandidates(h).map((d) => d.dishId)).toEqual([2])
    expect(heroCandidates({ ...h, popular: [] }).map((d) => d.dishId)).toEqual([1])
  })

  it('开启个性化：临期主推 → 你家常做（不拿应季当首推）', () => {
    const base = { expiringPicks: [], frequent: [card(5)], seasonal: [card(1)], popular: [], personalized: true }
    expect(heroCandidates(base).map((d) => d.dishId)).toEqual([5])
    expect(heroCandidates({ ...base, expiringPicks: [card(7)] }).map((d) => d.dishId)).toEqual([7])
    expect(heroCandidates({ ...base, frequent: [] })).toEqual([])
  })
})
