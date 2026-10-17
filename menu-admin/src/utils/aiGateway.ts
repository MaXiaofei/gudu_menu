import type { Capability, GatewayModel, PeakHours, Provider, RouteTarget, Thinking } from '@/api/aiGateway'

// 模型网关：供应商表单校验、降级链排序、key 掩码展示、路由候选（纯函数）

export const CAPABILITIES: Capability[] = ['CHAT', 'TOOLS', 'VISION']

export const CAPABILITY_META: Record<Capability, { label: string; desc: string }> = {
  CHAT: { label: '对话', desc: '咕嘟回复、文案生成、营养补全' },
  TOOLS: { label: '工具调用', desc: 'Agent 规划与 function calling、餐程排布' },
  VISION: { label: '视觉', desc: '拍冰箱 / 小票 / 菜谱截图识别' },
}

export const THINKING_OPTIONS: { value: Thinking; label: string }[] = [
  { value: 'disabled', label: '关闭' },
  { value: 'low', label: '低' },
  { value: 'high', label: '高' },
  { value: 'max', label: '最高' },
]

export const THINKING_HINT = '开启思考更准，但更慢、更贵'

export interface ModelDraft {
  model: string
  capabilities: Capability[]
  /** 表单里按「元 / 百万 tokens（高峰价）」录入，提交时换算成分 */
  cacheHitPrice: string
  inputPrice: string
  outputPrice: string
  peakHours: string
}

export interface ProviderDraft {
  name: string
  baseUrl: string
  apiKey: string
  enabled: boolean
  models: ModelDraft[]
}

const PRICE_RE = /^\d+(\.\d{1,2})?$/

export function isValidBaseUrl(url: string): boolean {
  const s = (url ?? '').trim()
  if (!/^https?:\/\//i.test(s)) return false
  try {
    const u = new URL(s)
    return !!u.hostname && !u.search && !u.hash
  } catch {
    return false
  }
}

/**
 * 供应商表单校验。新增时 key 必填（MOCK 除外由后端处理，这里只管 OPENAI 兼容）；编辑时留空表示不修改。
 * 每个模型：名称必填且不重复、至少一个能力、单价为非负数（最多两位小数）。
 */
export function validateProvider(d: ProviderDraft, opts: { editing: boolean }): Record<string, string> {
  const e: Record<string, string> = {}
  if (!d.name?.trim()) e.name = '请填写供应商名称'
  if (!isValidBaseUrl(d.baseUrl)) e.baseUrl = 'Base URL 需以 http(s):// 开头，且不带查询参数'
  if (!opts.editing && !d.apiKey?.trim()) e.apiKey = '新增供应商需填写 API Key'
  if (!d.models?.length) e.models = '至少添加一个模型'
  const seen = new Set<string>()
  ;(d.models ?? []).forEach((m, i) => {
    const name = m.model?.trim()
    if (!name) e[`models.${i}.model`] = '请填写模型名'
    else if (seen.has(name)) e[`models.${i}.model`] = '模型名重复'
    else seen.add(name)
    if (!m.capabilities?.length) e[`models.${i}.capabilities`] = '至少选择一个能力'
    if (m.peakHours?.trim() && !parsePeakHours(m.peakHours)) e[`models.${i}.peakHours`] = '格式如：Mon-Fri 09:00-12:00,14:00-18:00 Asia/Shanghai'
    for (const k of ['cacheHitPrice', 'inputPrice', 'outputPrice'] as const) {
      if (!PRICE_RE.test(String(m[k] ?? '').trim())) e[`models.${i}.${k}`] = '单价需为非负数，最多两位小数'
    }
  })
  return e
}

/** 元 → 分（四舍五入到整数分）。 */
export function yuanToFen(s: string): number {
  return Math.round(Number(s) * 100)
}

export function fenToYuanStr(fen: number | null | undefined): string {
  if (fen == null || !Number.isFinite(fen)) return ''
  const y = fen / 100
  return Number.isInteger(y) ? String(y) : y.toFixed(2).replace(/0$/, '')
}

export function toModelBody(m: ModelDraft): GatewayModel {
  return {
    model: m.model.trim(),
    capabilities: CAPABILITIES.filter((c) => m.capabilities.includes(c)),
    price: {
      inputCacheHitFen: yuanToFen(m.cacheHitPrice),
      inputFen: yuanToFen(m.inputPrice),
      outputFen: yuanToFen(m.outputPrice),
    },
    peakHours: parsePeakHours(m.peakHours),
  }
}

export function toModelDraft(m: GatewayModel): ModelDraft {
  return {
    model: m.model,
    capabilities: [...m.capabilities],
    cacheHitPrice: fenToYuanStr(m.price?.inputCacheHitFen),
    inputPrice: fenToYuanStr(m.price?.inputFen),
    outputPrice: fenToYuanStr(m.price?.outputFen),
    peakHours: formatPeakHours(m.peakHours),
  }
}

/** 表单里的峰谷写法：「[Mon-Fri ]HH:MM-HH:MM,HH:MM-HH:MM 时区」，空闲价固定为高峰价的一半。 */
export const DEFAULT_PEAK_HOURS = 'Mon-Fri 09:00-12:00,14:00-18:00 Asia/Shanghai'

const RANGE_RE = /^([01]\d|2[0-3]):[0-5]\d-([01]\d|2[0-3]):[0-5]\d$/

export function parsePeakHours(s: string | null | undefined): PeakHours | null {
  const parts = (s ?? '').trim().split(/\s+/).filter(Boolean)
  if (!parts.length) return null
  const weekdaysOnly = parts[0] === 'Mon-Fri'
  if (weekdaysOnly) parts.shift()
  if (parts.length !== 2) return null
  const ranges = parts[0].split(',')
  if (!ranges.every((r) => RANGE_RE.test(r))) return null
  return { timezone: parts[1], weekdaysOnly, ranges, offPeakRatio: 0.5 }
}

export function formatPeakHours(p: PeakHours | null | undefined): string {
  if (!p?.ranges?.length) return ''
  return `${p.weekdaysOnly ? 'Mon-Fri ' : ''}${p.ranges.join(',')} ${p.timezone}`
}

/** 峰谷说明，如「高峰时段：工作日 09:00–12:00、14:00–18:00，空闲 5 折」。 */
export function peakHoursText(peak: PeakHours | string | null | undefined): string {
  const p = typeof peak === 'string' ? parsePeakHours(peak) : peak
  if (!p?.ranges?.length) return typeof peak === 'string' && peak.trim() ? '高峰时段格式有误' : '不分峰谷'
  const ratio = Math.round((p.offPeakRatio ?? 0.5) * 10)
  const off = ratio === 5 ? '空闲半价' : `空闲 ${ratio} 折`
  return `高峰时段：${p.weekdaysOnly ? '工作日 ' : ''}${p.ranges.map((r) => r.replace('-', '–')).join('、')}，${off}`
}

/** 单价展示：分 → 「¥2 / ¥8」形式的元（每百万 token）。 */
export function priceText(fen: number | null | undefined): string {
  const y = fenToYuanStr(fen)
  return y === '' ? '—' : `¥${y}`
}

/** key 展示：后端只给掩码；无 key 时按来源说明。 */
export function keyDisplay(p: Pick<Provider, 'apiKeyMasked' | 'keySource' | 'type'>): { text: string; source: string } {
  const source = p.keySource === 'DB' ? '后台配置' : p.keySource === 'ENV' ? '环境变量' : '未配置'
  if (p.type === 'MOCK') return { text: '无需 key', source: '内置' }
  const masked = (p.apiKeyMasked ?? '').trim()
  return { text: masked || '—', source }
}

/** 在本地对掩码再做一次兜底：只露前 3 后 4，防止后端误返回明文时直接显示。 */
export function ensureMasked(key: string | null | undefined): string {
  const s = (key ?? '').trim()
  if (!s) return ''
  if (s.includes('*')) return s
  if (s.length <= 8) return '****'
  return `${s.slice(0, 3)}****${s.slice(-4)}`
}

/** 降级链移动：dir=-1 上移、+1 下移；越界不变。返回新数组。 */
export function moveItem<T>(list: T[], index: number, dir: -1 | 1): T[] {
  const to = index + dir
  if (index < 0 || index >= list.length || to < 0 || to >= list.length) return [...list]
  const next = [...list]
  ;[next[index], next[to]] = [next[to], next[index]]
  return next
}

/** 拖拽排序：from 移到 to 位置。 */
export function reorder<T>(list: T[], from: number, to: number): T[] {
  if (from === to || from < 0 || from >= list.length || to < 0 || to >= list.length) return [...list]
  const next = [...list]
  const [it] = next.splice(from, 1)
  next.splice(to, 0, it)
  return next
}

export const targetKey = (t: RouteTarget) => `${t.providerId}:${t.model}`

/** 某能力可选的模型：只列已启用供应商下、声明了该能力的模型（VISION 路由因此只能选带视觉能力的模型）。 */
export function candidatesFor(providers: Provider[], cap: Capability) {
  const out: { providerId: number; model: string; label: string; mock: boolean }[] = []
  for (const p of providers ?? []) {
    if (!p.enabled) continue
    for (const m of p.models ?? []) {
      if (m.capabilities.includes(cap)) {
        out.push({ providerId: p.providerId, model: m.model, label: `${p.name} · ${m.model}`, mock: p.type === 'MOCK' })
      }
    }
  }
  return out
}

/** 路由校验：主模型必选；降级链不得重复、不得包含主模型；超时 1–120 秒；重试 0–3 次。 */
export function validateRoute(r: { primary: RouteTarget | null; fallbacks: RouteTarget[]; timeoutMs: number; maxRetries: number }): string | null {
  if (!r.primary || !r.primary.model) return '请选择主模型'
  const keys = r.fallbacks.map(targetKey)
  if (keys.includes(targetKey(r.primary))) return '降级链不能包含主模型'
  if (new Set(keys).size !== keys.length) return '降级链有重复模型'
  if (!Number.isFinite(r.timeoutMs) || r.timeoutMs < 1000 || r.timeoutMs > 120000) return '超时需在 1–120 秒之间'
  if (!Number.isInteger(r.maxRetries) || r.maxRetries < 0 || r.maxRetries > 3) return '重试次数需在 0–3 之间'
  return null
}

/** 路由里引用的模型是否仍具备该能力（供应商停用、模型被改掉能力时提示）。 */
export function targetSupports(providers: Provider[], t: RouteTarget | null | undefined, cap: Capability): boolean {
  if (!t) return false
  const p = (providers ?? []).find((x) => x.providerId === t.providerId)
  const m = p?.models?.find((x) => x.model === t.model)
  return !!p && p.enabled && !!m && m.capabilities.includes(cap)
}
