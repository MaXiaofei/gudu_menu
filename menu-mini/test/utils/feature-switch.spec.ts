import { describe, it, expect, vi, beforeEach } from 'vitest'
import { __testUni } from '../setup'
import { request } from '@/utils/request'
import {
  canSendFor,
  featureStatus,
  loadFeatureSwitches,
  noteSuspendedCode,
} from '@/utils/featureSwitch'
import { applyAgentEvent, emptyAssistantMessage } from '@/api/agent'
import { useAgentChat } from '@/composables/useAgentChat'

// 咕嘟助手 / 问咕嘟紧急关停：5032 / 5033 切暂停状态（不弹报错），状态接口失败按开处理
describe('utils/featureSwitch', () => {
  beforeEach(() => {
    featureStatus.agentChat = true
    featureStatus.cookAsk = true
    __testUni.resetStorage()
    vi.mocked(__testUni.mocks.showToast).mockClear()
    vi.mocked(__testUni.mocks.request).mockReset()
  })

  const reply = (code: number, msg = 'ok', data: any = null) =>
    vi.mocked(__testUni.mocks.request).mockResolvedValueOnce({ statusCode: 200, data: { code, msg, data } } as any)

  it('request 收到 5032：置咕嘟助手暂停、抛错、不弹 toast；问咕嘟不受影响', async () => {
    reply(5032, '咕嘟助手暂停服务')
    await expect(request({ url: '/agent/suggestions', method: 'GET' })).rejects.toThrow('咕嘟助手暂停服务')
    expect(featureStatus.agentChat).toBe(false)
    expect(featureStatus.cookAsk).toBe(true)
    expect(__testUni.mocks.showToast).not.toHaveBeenCalled()
  })

  it('request 收到 5033：只置问咕嘟暂停', async () => {
    reply(5033, '问咕嘟暂停服务')
    await expect(request({ url: '/agent/x', method: 'GET' })).rejects.toThrow('问咕嘟暂停服务')
    expect(featureStatus.cookAsk).toBe(false)
    expect(featureStatus.agentChat).toBe(true)
    expect(__testUni.mocks.showToast).not.toHaveBeenCalled()
  })

  it('SSE error 事件携带 5032 / 5033：切暂停状态；其他错误码不影响', () => {
    const m1 = applyAgentEvent(emptyAssistantMessage(), { event: 'error', data: { code: 5032, message: '咕嘟助手暂停服务', errorId: '' } })
    expect(m1.error?.code).toBe(5032)
    expect(featureStatus.agentChat).toBe(false)
    applyAgentEvent(emptyAssistantMessage(), { event: 'error', data: { code: 5033, message: '问咕嘟暂停服务' } })
    expect(featureStatus.cookAsk).toBe(false)
    expect(noteSuspendedCode(500)).toBe(false)
  })

  it('loadFeatureSwitches：读取状态；未登录不请求；失败/异常数据按开处理', async () => {
    await loadFeatureSwitches()
    expect(__testUni.mocks.request).not.toHaveBeenCalled() // 无 token

    __testUni.setStorage('token', 't')
    reply(0, 'ok', { agentChat: false, cookAsk: true })
    await loadFeatureSwitches()
    expect(featureStatus).toMatchObject({ agentChat: false, cookAsk: true })

    reply(0, 'ok', { agentChat: true, cookAsk: false })
    await loadFeatureSwitches()
    expect(featureStatus).toMatchObject({ agentChat: true, cookAsk: false })

    // 恢复后重新读取即解除
    reply(0, 'ok', { agentChat: true, cookAsk: true })
    await loadFeatureSwitches()
    expect(featureStatus).toMatchObject({ agentChat: true, cookAsk: true })

    // 网络失败：保持原状（默认开），不抛错
    vi.mocked(__testUni.mocks.request).mockRejectedValueOnce(new Error('network'))
    await expect(loadFeatureSwitches()).resolves.toBeUndefined()
    expect(featureStatus).toMatchObject({ agentChat: true, cookAsk: true })
  })

  it('canSendFor：cook_mode 看问咕嘟，其余看咕嘟助手', () => {
    featureStatus.agentChat = false
    expect(canSendFor({ page: 'agent' })).toBe(false)
    expect(canSendFor(undefined)).toBe(false)
    expect(canSendFor({ page: 'cook_mode' })).toBe(true)
    featureStatus.agentChat = true
    featureStatus.cookAsk = false
    expect(canSendFor({ page: 'cook_mode' })).toBe(false)
    expect(canSendFor({ page: 'agent' })).toBe(true)
  })

  it('useAgentChat.send：暂停时不发送、不追加消息', async () => {
    featureStatus.agentChat = false
    const chat = useAgentChat()
    expect(await chat.send('今晚吃什么')).toBeNull()
    expect(chat.messages).toHaveLength(0)

    featureStatus.cookAsk = false
    const cook = useAgentChat({ context: { page: 'cook_mode' } as any })
    expect(await cook.send('没有豆瓣酱怎么办')).toBeNull()
    expect(cook.messages).toHaveLength(0)
  })
})
