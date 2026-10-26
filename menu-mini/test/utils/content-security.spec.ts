import { describe, it, expect, vi, beforeEach } from 'vitest'
import { __testUni } from '../setup'
import { request } from '@/utils/request'
import {
  CONTENT_BLOCKED,
  CONTENT_CHECK_UNAVAILABLE,
  MSG_CONTENT_UNAVAILABLE,
  contentSecurityMessage,
  isContentSecurityCode,
} from '@/utils/contentSecurity'
import { updateMe } from '@/api/member'
import { submitFeedback } from '@/api/feedback'
import { applyAgentEvent, emptyAssistantMessage } from '@/api/agent'
import { postReviewText } from '@/api/community'

// 内容安全（A.6）：后端 451 违规 / 452 检测不可用 → 前端按场景给友好文案，不展示后端原文。
describe('utils/contentSecurity', () => {
  it('451 按场景给文案，452 统一稍后再试，其它码返回 null', () => {
    expect(contentSecurityMessage(CONTENT_BLOCKED, 'nickname')).toContain('昵称')
    expect(contentSecurityMessage(CONTENT_BLOCKED, 'avatar')).toContain('头像')
    expect(contentSecurityMessage(CONTENT_BLOCKED, 'feedback')).toContain('反馈')
    expect(contentSecurityMessage(CONTENT_BLOCKED, 'agent')).toContain('换个说法')
    expect(contentSecurityMessage(CONTENT_BLOCKED)).toContain('内容安全')
    expect(contentSecurityMessage('451', 'nickname')).toContain('昵称')
    expect(contentSecurityMessage(CONTENT_CHECK_UNAVAILABLE, 'nickname')).toBe(MSG_CONTENT_UNAVAILABLE)
    expect(contentSecurityMessage(1)).toBeNull()
    expect(contentSecurityMessage(undefined)).toBeNull()
    expect(isContentSecurityCode(452)).toBe(true)
    expect(isContentSecurityCode(429)).toBe(false)
  })
})

describe('request 处理内容安全错误码', () => {
  beforeEach(() => {
    __testUni.resetStorage()
    vi.mocked(__testUni.mocks.showToast).mockClear()
    vi.mocked(__testUni.mocks.request).mockReset()
  })

  function reply(code: number, msg: string) {
    vi.mocked(__testUni.mocks.request).mockResolvedValue({ data: { code, msg, data: null }, statusCode: 200 } as any)
  }

  it('451：toast 与抛错都用场景文案，不露后端原文', async () => {
    reply(451, '规则命中：网赌')
    await expect(request({ url: '/x', method: 'PUT', contentScene: 'nickname' })).rejects.toThrow(/昵称未通过/)
    expect(__testUni.mocks.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: expect.stringContaining('昵称') }))
    expect(JSON.stringify(vi.mocked(__testUni.mocks.showToast).mock.calls)).not.toContain('网赌')
  })

  it('452：提示稍后再试', async () => {
    reply(452, 'errcode 45009')
    await expect(request({ url: '/x', method: 'PUT', contentScene: 'avatar' })).rejects.toThrow(MSG_CONTENT_UNAVAILABLE)
  })

  it('silent 时不弹 toast 但仍抛友好错误', async () => {
    reply(451, 'x')
    await expect(request({ url: '/x', method: 'GET', silent: true })).rejects.toThrow(/内容安全/)
    expect(__testUni.mocks.showToast).not.toHaveBeenCalled()
  })

  it('updateMe：只改昵称 → 昵称文案；只改头像 → 头像文案；都改 → 资料文案', async () => {
    reply(451, 'x')
    await expect(updateMe({ name: '坏昵称' })).rejects.toThrow(/昵称未通过/)
    await expect(updateMe({ avatarUrl: '/gudu/uploads/a.jpg' })).rejects.toThrow(/头像未通过/)
    await expect(updateMe({ name: 'a', avatarUrl: '/gudu/uploads/a.jpg' })).rejects.toThrow(/昵称或头像/)
  })

  it('submitFeedback：反馈文案', async () => {
    reply(451, 'x')
    await expect(submitFeedback({ content: '一段违规的反馈' })).rejects.toThrow(/反馈内容未通过/)
  })
})

describe('厨房圈帖子处置提示', () => {
  it('图片违规原因优先，其次审核中 / 已隐藏', () => {
    expect(postReviewText({ status: 'HIDDEN', statusReason: '图片未通过审核' })).toBe('图片未通过审核 · 仅自己可见')
    expect(postReviewText({ status: 'HIDDEN', statusReason: null })).toBe('已被隐藏')
    expect(postReviewText({ status: 'REVIEWING' })).toContain('审核中')
  })
})

describe('咕嘟对话 SSE error 事件', () => {
  it('内容安全错误码用友好文案，其它保留后端 message', () => {
    const m = applyAgentEvent(emptyAssistantMessage(), { event: 'error', data: { code: 451, message: 'raw', errorId: '' } })
    expect(m.error?.message).toContain('换个说法')
    expect(m.streaming).toBe(false)
    const m2 = applyAgentEvent(emptyAssistantMessage(), { event: 'error', data: { code: 500, message: '咕嘟出了点问题' } })
    expect(m2.error?.message).toBe('咕嘟出了点问题')
  })
})
