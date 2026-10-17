import { request } from './request'

// 模型网关：供应商 / 能力路由 / 用量（contracts.md §6）

export type Capability = 'CHAT' | 'TOOLS' | 'VISION'

export type Thinking = 'disabled' | 'low' | 'high' | 'max'

export interface ModelPrice {
  /** 每百万 token，高峰价（分）；空闲时段 = 高峰 / 2 */
  inputCacheHitFen: number
  inputFen: number
  outputFen: number
}

export interface GatewayModel {
  model: string
  capabilities: Capability[]
  price: ModelPrice
  /** null = 不分峰谷 */
  peakHours: PeakHours | null
}

/** 峰谷计价：ranges 为高峰时段（如 "09:00-12:00"），空闲价 = 高峰价 × offPeakRatio */
export interface PeakHours {
  timezone: string
  weekdaysOnly: boolean
  ranges: string[]
  offPeakRatio: number
}

export interface Provider {
  providerId: number
  name: string
  type: 'OPENAI_COMPATIBLE' | 'MOCK'
  baseUrl: string
  apiKeyMasked: string | null
  keySource: 'DB' | 'ENV' | 'NONE'
  enabled: boolean
  models: GatewayModel[]
  health: { ok: boolean; latencyMs: number | null; checkedAt: string | null }
}

export interface RouteTarget {
  providerId: number
  model: string
}

export interface GatewayRoute {
  capability: Capability
  primary: RouteTarget
  fallbacks: RouteTarget[]
  timeoutMs: number
  maxRetries: number
  thinking: Thinking
  updatedAt: string
  updatedBy: string
}

export interface ProviderBody {
  name: string
  baseUrl: string
  /** 编辑时留空 = 不修改 */
  apiKey: string
  enabled: boolean
  models: GatewayModel[]
}

export interface UsageRow {
  capability: Capability
  provider: string
  model: string
  calls: number
  failRate: number
  p95Ms: number
  costFen: number
}

export function getGateway() {
  return request<{ providers: Provider[]; routes: GatewayRoute[] }>({ url: '/admin/ai/gateway', method: 'get' })
}

export function createProvider(body: ProviderBody) {
  return request<Provider>({ url: '/admin/ai/gateway/providers', method: 'post', data: body })
}

export function updateProvider(id: number, body: ProviderBody) {
  return request<Provider>({ url: `/admin/ai/gateway/providers/${id}`, method: 'put', data: body })
}

/** 被路由引用时后端返回 409；调用方需自行提示（request 层已统一弹出 msg）。 */
export function deleteProvider(id: number) {
  return request<unknown>({ url: `/admin/ai/gateway/providers/${id}`, method: 'delete' })
}

export function testProvider(id: number, body: { model: string; capability: Capability }) {
  return request<{ ok: boolean; latencyMs: number | null; error: string | null; sample: string | null }>({
    url: `/admin/ai/gateway/providers/${id}/test`,
    method: 'post',
    data: body,
    timeout: 60000,
  })
}

export function saveRoute(capability: Capability, body: Pick<GatewayRoute, 'primary' | 'fallbacks' | 'timeoutMs' | 'maxRetries' | 'thinking'>) {
  return request<GatewayRoute>({ url: `/admin/ai/gateway/routes/${capability}`, method: 'put', data: body })
}

export function getGatewayUsage(range: 'today' | '7d') {
  return request<{ byRoute: UsageRow[] }>({ url: '/admin/ai/gateway/usage', method: 'get', params: { range } })
}
