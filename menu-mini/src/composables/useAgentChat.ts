/**
 * 咕嘟对话状态（对话页、语音页、跟做模式「问咕嘟」共用）：
 * 发送 → SSE 事件归并到助手消息 → 待确认动作的确认/取消/撤销/过期。
 */
import { reactive, ref } from 'vue'
import {
  applyAgentEvent,
  cancelAction,
  confirmAction,
  emptyAssistantMessage,
  ensureSession,
  listMessages,
  rateMessage,
  reviseAction,
  sendMessage,
  undoAction,
  type AgentMessage,
  type MessageContext,
  type PendingAction,
} from '@/api/agent'
import type { SseHandle } from '@/utils/sse'

export function useAgentChat(opts: { context?: MessageContext } = {}) {
  const messages = reactive<AgentMessage[]>([])
  const sessionId = ref('')
  const sending = ref(false)
  const busyAction = ref('')
  let handle: SseHandle | null = null

  async function init(id?: string, loadHistory = false) {
    sessionId.value = id || (await ensureSession())
    if (loadHistory) {
      try {
        const page = await listMessages(sessionId.value, null, 30)
        messages.splice(0, messages.length, ...page.items.slice().reverse())
      } catch {
        // 历史加载失败不影响继续对话
      }
    }
  }

  async function send(text: string, extra: { audioFileId?: number } = {}): Promise<AgentMessage | null> {
    const q = text.trim()
    if ((!q && !extra.audioFileId) || sending.value) return null
    if (!sessionId.value) await init()
    messages.push({
      messageId: 'local-' + Date.now(),
      role: 'user',
      text: q || '（语音）',
      tools: [],
      cards: [],
      actions: [],
      traceId: null,
      createdAt: new Date().toISOString(),
    })
    messages.push(emptyAssistantMessage())
    const reply = messages[messages.length - 1] // 响应式代理
    sending.value = true
    return new Promise((resolve) => {
      const finish = () => {
        sending.value = false
        handle = null
        resolve(reply)
      }
      handle = sendMessage(
        sessionId.value,
        { ...(q ? { text: q } : {}), ...extra, ...(opts.context ? { context: opts.context } : {}) },
        (e) => {
          applyAgentEvent(reply, e)
          if (e.event === 'done' || e.event === 'error') finish()
        },
        {
          onError: () => {
            if (reply.streaming) {
              reply.streaming = false
              reply.error = { code: -1, message: '网络不太好，咕嘟没收到', errorId: '' }
            }
            finish()
          },
          onDone: () => {
            if (reply.streaming) reply.streaming = false
            finish()
          },
        },
      )
    })
  }

  function stop() {
    handle?.abort()
    handle = null
    sending.value = false
    const last = messages[messages.length - 1]
    if (last?.streaming) last.streaming = false
  }

  async function confirm(a: PendingAction) {
    if (busyAction.value) return
    busyAction.value = a.actionId
    try {
      const r = await confirmAction(a.actionId)
      a.status = 'DONE'
      a.undoToken = r.undoToken
    } catch (e: any) {
      if (/过期/.test(e?.message || '')) a.status = 'EXPIRED'
    } finally {
      busyAction.value = ''
    }
  }

  async function cancel(a: PendingAction) {
    try {
      await cancelAction(a.actionId)
    } finally {
      a.status = 'CANCELLED'
    }
  }

  async function undo(a: PendingAction) {
    if (!a.undoToken) return
    await undoAction(a.undoToken)
    a.undoToken = null
    a.status = 'CANCELLED'
    uni.showToast({ title: '已撤销', icon: 'none' })
  }

  /** 修改待确认动作 → 新动作原位替换旧卡（旧卡作废）。 */
  async function revise(a: PendingAction, args: Record<string, unknown>) {
    if (busyAction.value) return
    busyAction.value = a.actionId
    try {
      const next = await reviseAction(a.actionId, args)
      for (const m of messages) {
        const i = m.actions.findIndex((x) => x.actionId === a.actionId)
        if (i >= 0) {
          m.actions.splice(i, 1, next)
          break
        }
      }
    } finally {
      busyAction.value = ''
    }
  }

  function expire(a: PendingAction) {
    if (a.status === 'PENDING') a.status = 'EXPIRED'
  }

  async function rate(m: AgentMessage, r: 1 | -1) {
    if (!m.messageId || m.rating === r) return
    m.rating = r
    try {
      await rateMessage(m.messageId, r)
      if (r === -1) uni.showToast({ title: '收到，咕嘟会改进', icon: 'none' })
    } catch {
      m.rating = 0
    }
  }

  return { messages, sessionId, sending, busyAction, init, send, stop, confirm, cancel, undo, revise, expire, rate }
}
