import { describe, it, expect, vi, beforeEach } from 'vitest'
import { __testUni } from '../setup'
import { request } from '@/utils/request'
import {
  anyAiEnabled,
  canSendFor,
  entryOn,
  entryStatus,
  featureState,
  featureStatus,
  FEATURE_CACHE_KEY,
  loadFeatureSwitches,
  noteSuspendedCode,
  restoreFeatureSwitches,
} from '@/utils/featureSwitch'
import { applyAgentEvent, emptyAssistantMessage } from '@/api/agent'
import { useAgentChat } from '@/composables/useAgentChat'

// 咕嘟助手 / 问咕嘟紧急关停：5032 / 5033 切暂停状态（不弹报错），状态接口失败按开处理
describe('utils/featureSwitch', () => {
  beforeEach(() => {
    featureStatus.agentChat = true
    featureStatus.cookAsk = true
    featureStatus.vision = true
    featureStatus.mealPlan = true
    Object.assign(entryStatus, { recipeWrite: true, dishDrafts: true, shoppingShare: true })
    featureState.known = true
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

  it('loadFeatureSwitches：读取状态；游客（无 token）也请求；失败/异常数据按开处理', async () => {
    // 游客（无 token）也会请求，拿到真实状态
    reply(0, 'ok', { agentChat: false, cookAsk: false, vision: false, mealPlan: false })
    await loadFeatureSwitches()
    expect(__testUni.mocks.request).toHaveBeenCalledTimes(1)
    expect((vi.mocked(__testUni.mocks.request).mock.calls[0][0] as any).url).toContain('/feature-switches')
    expect(featureStatus).toMatchObject({ agentChat: false, cookAsk: false, vision: false, mealPlan: false })
    featureStatus.agentChat = featureStatus.cookAsk = featureStatus.vision = featureStatus.mealPlan = true

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

  it('canSendFor：咕嘟助手关停全拦；cook_mode 需问咕嘟也开', () => {
    featureStatus.agentChat = false
    expect(canSendFor({ page: 'agent' })).toBe(false)
    expect(canSendFor(undefined)).toBe(false)
    expect(canSendFor({ page: 'cook_mode' })).toBe(false)
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
  it('request 收到 5034 / 5035：只置对应类别暂停、不弹 toast', async () => {
    reply(5034, '拍照识别暂停服务')
    await expect(request({ url: '/vision/jobs', method: 'POST' })).rejects.toThrow('拍照识别暂停服务')
    expect(featureStatus).toMatchObject({ vision: false, mealPlan: true, agentChat: true, cookAsk: true })
    reply(5035, '一周餐程暂停服务')
    await expect(request({ url: '/mealplan/drafts', method: 'POST' })).rejects.toThrow('一周餐程暂停服务')
    expect(featureStatus).toMatchObject({ vision: false, mealPlan: false, agentChat: true })
    expect(__testUni.mocks.showToast).not.toHaveBeenCalled()
    expect(noteSuspendedCode(5036)).toBe(false)
  })

  it('页面入口三项独立于 AI：读取、未知隐藏、旧缓存缺字段按开', async () => {
    featureState.known = false
    expect(entryOn('recipeWrite')).toBe(false) // 未知时隐藏
    Object.assign(entryStatus, { recipeWrite: true, dishDrafts: true, shoppingShare: true })
    reply(0, 'ok', { recipeWrite: false, dishDrafts: false, shoppingShare: false })
    await loadFeatureSwitches()
    expect(entryStatus).toEqual({ recipeWrite: false, dishDrafts: false, shoppingShare: false })
    expect(entryOn('recipeWrite')).toBe(false)
    expect(featureStatus.agentChat).toBe(true) // AI 不受影响
    expect(anyAiEnabled()).toBe(true)

    // 恢复后重新读取即解除
    reply(0, 'ok', { recipeWrite: true, dishDrafts: true, shoppingShare: true })
    await loadFeatureSwitches()
    expect(entryOn('recipeWrite')).toBe(true)

    // 旧版本缓存只有四类 AI：三个入口缺省按开
    entryStatus.recipeWrite = entryStatus.dishDrafts = entryStatus.shoppingShare = false
    featureState.known = false
    __testUni.setStorage(FEATURE_CACHE_KEY, { agentChat: true, cookAsk: true, vision: true, mealPlan: true })
    restoreFeatureSwitches()
    expect(entryStatus).toEqual({ recipeWrite: true, dishDrafts: true, shoppingShare: true })
    expect(entryOn('shoppingShare')).toBe(true)
  })

  it('loadFeatureSwitches 读取 vision / mealPlan；旧后端缺字段按开', async () => {
    __testUni.setStorage('token', 't')
    reply(0, 'ok', { agentChat: true, cookAsk: true, vision: false, mealPlan: false })
    await loadFeatureSwitches()
    expect(featureStatus).toMatchObject({ vision: false, mealPlan: false })
    reply(0, 'ok', { agentChat: true, cookAsk: true })
    await loadFeatureSwitches()
    expect(featureStatus).toMatchObject({ vision: true, mealPlan: true })
  })
})
