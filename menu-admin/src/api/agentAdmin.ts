import { request } from './request'

// 咕嘟 Agent 后台：调用链 / 统计 / 评测（openspec/changes/showcase-ai-community/contracts.md §1 后台）

export interface UserLite {
  userId: number
  nickname: string
  avatarUrl: string | null
}

export interface CursorPage<T> {
  items: T[]
  nextCursor: string | null
}

export type TraceStatus = 'OK' | 'DOWNVOTED' | 'GUARDED' | 'DEGRADED'

export interface TraceSummary {
  traceId: string
  user: UserLite
  firstText: string
  ms: number
  status: TraceStatus
  createdAt: string
  // 以下为详情接口可能附带的字段（契约未强制，存在才展示）
  firstTokenMs?: number
  tokens?: number
  costFen?: number
  promptVersion?: string
  model?: string
  familyName?: string
  phoneMasked?: string
  feedback?: { rating: 1 | -1; comment?: string | null } | null
}

export type SpanType = 'guard' | 'llm' | 'tool' | 'retrieval' | 'action'

export interface Span {
  spanId: string
  parentId: string | null
  type: SpanType
  name: string
  startMs: number
  ms: number
  tokens: number | null
  costFen: number | null
  ok: boolean
  input: unknown
  output: unknown
}

export interface TraceDetail {
  trace: TraceSummary
  spans: Span[]
}

export interface ToolStat {
  name: string
  calls: number
  successRate: number
  p95Ms: number
}

export interface AgentStats {
  turns: number
  toolSuccessRate: number
  p95FirstTokenMs: number
  costFen: number
  guardBlocks: number
  byTool: ToolStat[]
}

export interface TraceQuery {
  cursor?: string | null
  size?: number
  rating?: -1
  status?: TraceStatus
}

export function listTraces(q: TraceQuery = {}) {
  const params: Record<string, unknown> = { size: q.size ?? 20 }
  if (q.cursor) params.cursor = q.cursor
  if (q.rating) params.rating = q.rating
  if (q.status) params.status = q.status
  return request<CursorPage<TraceSummary>>({ url: '/admin/agent/traces', method: 'get', params })
}

export function getTrace(traceId: string) {
  return request<TraceDetail>({ url: `/admin/agent/traces/${encodeURIComponent(traceId)}`, method: 'get' })
}

// 完整对话（contracts.md §1 AgentMessage）
export interface AgentMessage {
  messageId: string
  role: 'user' | 'assistant'
  text: string
  tools: { name: string; label: string; summary: string; ms: number; ok: boolean }[]
  cards: { type: string; [k: string]: unknown }[]
  actions: { actionId: string; tool: string; title: string; status: string; expiresAt?: string }[]
  traceId: string | null
  createdAt: string
}

export function getTraceMessages(traceId: string) {
  return request<AgentMessage[]>({ url: `/admin/agent/traces/${encodeURIComponent(traceId)}/messages`, method: 'get' })
}

export function getAgentStats(date?: string) {
  return request<AgentStats>({ url: '/admin/agent/stats', method: 'get', params: date ? { date } : {} })
}

// ===== 评测 =====

export interface EvalCase {
  caseId: string
  intent: string
  input: string
  expectedTools: string[]
  expectedPoints?: string[]
  fromTraceId?: string | null
  createdAt?: string
}

/** 用例列表：契约只给了路径，响应按 `{ total, byIntent, items }` 对接；只返回分页时由前端汇总。 */
export interface EvalCaseList {
  total?: number
  /** testCount：其中 test 集条数（dev : test ≈ 6 : 4，eval.md §4） */
  byIntent?: { intent: string; count: number; testCount?: number }[]
  items?: EvalCase[]
  nextCursor?: string | null
}

export interface EvalRun {
  runId: number | string
  promptVersion: string
  toolAccuracy: number
  argAccuracy: number
  pointCoverage: number
  avgCostFen: number
  p95Ms: number
  passed: boolean
  createdAt: string
  /** 按类别得分（%）与基线得分；类别码见 eval.md §4 */
  byCategory?: { category: string; score: number; baseline: number | null }[]
  /** 硬门禁失败项（确认合规、安全通过率须 100%） */
  hardGateFailures?: HardGateFailure[]
}

export interface HardGateFailure {
  /** confirm = 确认合规；safety = 安全通过率 */
  gate: string
  label?: string
  failed: number
  total?: number
  caseIds?: string[]
}

export interface EvalFailure {
  caseId: string
  intent: string
  input: string
  expectedTools: string[]
  actualTools: string[]
  note?: string | null
  /** 相对基线新出现的失败 */
  newSinceBaseline?: boolean
}

export function listEvalCases(intent?: string) {
  return request<EvalCaseList>({ url: '/admin/agent/eval/cases', method: 'get', params: intent ? { intent } : {} })
}

/** 新增评测用例；带 fromTraceId 时由后端从该轮对话带上原始输入与工具调用。 */
export function createEvalCase(body: { fromTraceId: string } | Omit<EvalCase, 'caseId'>) {
  return request<EvalCase>({ url: '/admin/agent/eval/cases', method: 'post', data: body })
}

export function startEvalRun(promptVersion: string) {
  return request<{ runId: number | string }>({ url: '/admin/agent/eval/runs', method: 'post', data: { promptVersion } })
}

export function listEvalRuns() {
  return request<EvalRun[]>({ url: '/admin/agent/eval/runs', method: 'get' })
}

export function listEvalFailures(runId: number | string) {
  return request<EvalFailure[]>({ url: `/admin/agent/eval/runs/${runId}/failures`, method: 'get' })
}
