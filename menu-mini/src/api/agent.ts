/**
 * 咕嘟 Agent（contracts §1）：会话、SSE 消息、待确认动作、反馈、首屏建议。
 */
import { request, BASE, authHeaders } from '@/utils/request'
import { streamSse, type SseEvent, type SseHandle } from '@/utils/sse'
import { normalizePage, type CursorPage } from '@/utils/cursor'
import type { CDishCard, MemberLite } from './contract'
import { contentSecurityMessage } from '@/utils/contentSecurity'

export interface AgentTool {
  name: string
  label: string
  summary: string
  ms: number
  ok: boolean
  /** 客户端态：tool_start 后、tool_end 前 */
  running?: boolean
}

export type AgentCard =
  | { type: 'recipes'; items: CDishCard[] }
  | { type: 'menu'; menuId: number; name: string; date: string; dishCount: number; coverUrls: string[] }
  | {
      type: 'pantry'
      items: { name: string; iconKey: string | null; status: 'ENOUGH' | 'LOW' | 'OUT'; daysLeft: number | null }[]
    }
  | { type: 'plan'; draftId: number; range: string; days: number }

export type ActionTool = 'add_to_menu' | 'add_to_shopping' | 'mark_cooked' | 'add_pantry'
export type ActionStatus = 'PENDING' | 'DONE' | 'CANCELLED' | 'EXPIRED'

export interface CookedLine {
  name: string
  iconKey: string | null
  delta: string
  after: string
  status: 'USED_UP' | 'LEFT' | 'SKIP'
}

export interface MarkCookedPreview {
  dish: CDishCard
  servings: number
  eaters: MemberLite[]
  lines: CookedLine[]
}

export interface PendingAction {
  actionId: string
  tool: ActionTool
  title: string
  preview: any
  expiresAt: string
  status: ActionStatus
  /** 客户端态：确认后返回的撤销凭证 */
  undoToken?: string | null
}

export interface AgentMessage {
  messageId: string
  role: 'user' | 'assistant'
  text: string
  tools: AgentTool[]
  cards: AgentCard[]
  actions: PendingAction[]
  traceId: string | null
  createdAt: string
  /** 客户端态 */
  streaming?: boolean
  error?: { code: number | string; message: string; errorId: string } | null
  degraded?: boolean
  rating?: 1 | -1 | 0
}

export interface AgentSessionItem {
  sessionId: string
  title: string
  lastMessage: string
  updatedAt: string
  coverUrl: string | null
}

export interface AgentSuggestions {
  greeting: string
  tonight: CDishCard | null
  expiring: { name: string; iconKey: string | null; daysLeft: number }[]
  prompts: string[]
}

export interface MessageContext {
  page: string
  dishId?: number
  step?: number
}

export function createSession(title?: string): Promise<{ sessionId: string }> {
  return request({ url: '/agent/sessions', method: 'POST', data: title ? { title } : {} })
}

/** 删除单个 AI 对话（隐私政策 §三.2；后端只允许删本人档案的会话，别人的会话 404）。 */
export function deleteSession(sessionId: string): Promise<unknown> {
  return request({ url: `/agent/sessions/${encodeURIComponent(sessionId)}`, method: 'DELETE' })
}

/** 清空本人全部 AI 对话记录。 */
export function clearSessions(): Promise<{ deleted: number }> {
  return request({ url: '/agent/sessions', method: 'DELETE' })
}

export async function listSessions(cursor: string | null = null, size = 10): Promise<CursorPage<AgentSessionItem>> {
  return normalizePage(await request({ url: '/agent/sessions', method: 'GET', data: { cursor, size } }))
}

export async function listMessages(sessionId: string, cursor: string | null = null, size = 20): Promise<CursorPage<AgentMessage>> {
  return normalizePage(
    await request({ url: `/agent/sessions/${encodeURIComponent(sessionId)}/messages`, method: 'GET', data: { cursor, size } }),
  )
}

export async function agentSuggestions(): Promise<AgentSuggestions> {
  const r = await request<Partial<AgentSuggestions> | null>({ url: '/agent/suggestions', method: 'GET', silent: true })
  return {
    greeting: r?.greeting || '',
    tonight: r?.tonight ?? null,
    expiring: r?.expiring ?? [],
    prompts: r?.prompts ?? [],
  }
}

export function confirmAction(actionId: string): Promise<{ status: 'DONE'; result: any; undoToken: string | null }> {
  return request({ url: `/agent/actions/${encodeURIComponent(actionId)}/confirm`, method: 'POST' })
}

export function cancelAction(actionId: string): Promise<{ status: 'CANCELLED' }> {
  return request({ url: `/agent/actions/${encodeURIComponent(actionId)}/cancel`, method: 'POST' })
}

/**
 * 修改待确认动作（如做完回写改人份 / 吃的人）：POST /agent/actions/{id}/revise { args }
 * → 新 PendingAction，替换旧卡（旧动作作废）。
 */
export function reviseAction(actionId: string, args: Record<string, unknown>): Promise<PendingAction> {
  return request({ url: `/agent/actions/${encodeURIComponent(actionId)}/revise`, method: 'POST', data: { args } })
}

export function undoAction(undoToken: string): Promise<{ status: 'UNDONE' }> {
  return request({ url: '/agent/actions/undo', method: 'POST', data: { undoToken } })
}

export function rateMessage(messageId: string, rating: 1 | -1, comment?: string): Promise<unknown> {
  return request({
    url: `/agent/messages/${encodeURIComponent(messageId)}/feedback`,
    method: 'POST',
    data: comment ? { rating, comment } : { rating },
  })
}

/** 新的空助手消息（流式填充）。 */
export function emptyAssistantMessage(): AgentMessage {
  return {
    messageId: '',
    role: 'assistant',
    text: '',
    tools: [],
    cards: [],
    actions: [],
    traceId: null,
    createdAt: new Date().toISOString(),
    streaming: true,
    error: null,
  }
}

/** 把一条 SSE 事件归并进助手消息（纯函数式修改，返回同一对象便于响应式）。 */
export function applyAgentEvent(msg: AgentMessage, e: SseEvent): AgentMessage {
  const d = e.data || {}
  switch (e.event) {
    case 'meta':
      if (d.messageId) msg.messageId = d.messageId
      if (d.traceId) msg.traceId = d.traceId
      break
    case 'token':
      msg.text += typeof d === 'string' ? d : d.text || ''
      break
    case 'tool_start':
      msg.tools.push({ name: d.name, label: d.label || d.name, summary: '', ms: 0, ok: true, running: true })
      break
    case 'tool_end': {
      const t = [...msg.tools].reverse().find((x) => x.name === d.name && x.running)
      if (t) Object.assign(t, { summary: d.summary || '', ms: d.ms || 0, ok: d.ok !== false, running: false })
      else msg.tools.push({ name: d.name, label: d.name, summary: d.summary || '', ms: d.ms || 0, ok: d.ok !== false })
      break
    }
    case 'card':
      if (d && d.type) msg.cards.push(d as AgentCard)
      break
    case 'action':
      if (d && d.actionId) {
        const i = msg.actions.findIndex((a) => a.actionId === d.actionId)
        if (i >= 0) msg.actions[i] = { ...msg.actions[i], ...d }
        else msg.actions.push(d as PendingAction)
      }
      break
    case 'done':
      msg.streaming = false
      msg.degraded = !!d.degraded
      msg.tools.forEach((t) => (t.running = false))
      break
    case 'error':
      msg.streaming = false
      msg.error = {
        code: d.code ?? -1,
        // 内容安全拒绝（451/452）用前端文案，不展示后端原文
        message: contentSecurityMessage(d.code, 'agent') || d.message || '咕嘟开小差了',
        errorId: d.errorId || '',
      }
      break
  }
  return msg
}

/** 工具调用总耗时（秒，一位小数）。 */
export function toolsSeconds(tools: AgentTool[]): string {
  return (tools.reduce((s, t) => s + (t.ms || 0), 0) / 1000).toFixed(1)
}

/** 待确认动作剩余秒数（过期为 0）。 */
export function actionSecondsLeft(a: PendingAction, now = Date.now()): number {
  const t = Date.parse(a.expiresAt)
  return Number.isFinite(t) ? Math.max(0, Math.floor((t - now) / 1000)) : 0
}

/** 发送消息并流式接收（返回可中断句柄）。 */
export function sendMessage(
  sessionId: string,
  body: { text?: string; audioFileId?: number; context?: MessageContext },
  onEvent: (e: SseEvent) => void,
  handlers: { onError?: (e: Error) => void; onDone?: () => void } = {},
): SseHandle {
  return streamSse({
    url: `/agent/sessions/${encodeURIComponent(sessionId)}/messages`,
    method: 'POST',
    data: body,
    onEvent,
    ...handlers,
  })
}

/** 当前会话 id 缓存（首屏 → 对话页 → 跟做问咕嘟复用）。 */
const SESSION_KEY = 'gd_agent_session'
export async function ensureSession(fresh = false): Promise<string> {
  const cached = fresh ? '' : uni.getStorageSync(SESSION_KEY)
  if (cached) return cached
  const { sessionId } = await createSession()
  uni.setStorageSync(SESSION_KEY, sessionId)
  return sessionId
}
export function forgetCurrentSession(): void {
  uni.removeStorageSync(SESSION_KEY)
}
export function setCurrentSession(id: string): void {
  uni.setStorageSync(SESSION_KEY, id)
}

/**
 * 上传语音（小程序录音 mp3）→ audioFileId。
 * 契约只约定 audioFileId；复用 POST /file/upload（multipart，kind=audio），返回 data.id / data.fileId。
 */
export function uploadAudio(filePath: string): Promise<number> {
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: `${BASE}/file/upload`,
      filePath,
      name: 'file',
      formData: { filename: 'voice.mp3', kind: 'audio' },
      header: authHeaders(),
      success: (res) => {
        try {
          const body = JSON.parse(res.data as any)
          const id = body?.data?.id ?? body?.data?.fileId
          if (body.code !== 0 || !id) return reject(new Error(body.msg || '语音上传失败'))
          resolve(Number(id))
        } catch (e) {
          reject(e)
        }
      },
      fail: (e) => reject(new Error(e.errMsg)),
    })
  })
}

/** 跟做/语音里的本地指令（不走模型）：下一步 / 上一步 / 暂停 / 计时。 */
export type VoiceCommand = 'next' | 'prev' | 'pause' | 'timer' | null
export function parseVoiceCommand(text: string): VoiceCommand {
  const t = (text || '').replace(/[\s，。！!？?、]/g, '')
  if (!t || t.length > 6) return null
  if (/^(下一步|下一个|继续|好了)$/.test(t)) return 'next'
  if (/^(上一步|上一个|返回|回去)$/.test(t)) return 'prev'
  if (/^(暂停|停一下|等一下)$/.test(t)) return 'pause'
  if (/^(计时|开始计时|定时)$/.test(t)) return 'timer'
  return null
}
