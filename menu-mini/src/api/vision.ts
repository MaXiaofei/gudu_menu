/**
 * 拍照识别（contracts §3）：冰箱 / 小票 / 菜谱截图 → 识别任务 → 确认后入库。
 */
import { request } from '@/utils/request'
import type { IngredientLine } from './contract'

export type VisionMode = 'FRIDGE' | 'RECEIPT' | 'RECIPE'

export interface VisionItem {
  name: string
  rawName: string
  ingredientId: number | null
  iconKey: string | null
  amount: number
  unit: string
  confidence: number
  level: 'AUTO' | 'CONFIRM'
  alternatives: string[]
  suggestedBestBefore: string | null
}

export interface VisionJob {
  jobId: number
  mode: VisionMode
  status: 'PENDING' | 'DONE' | 'FAILED'
  error?: string
  imageExpiresAt: string
  items: VisionItem[]
  recipe?: { title: string; ingredients: IngredientLine[]; steps: string[] }
}

export const MODE_LABEL: Record<VisionMode, string> = { FRIDGE: '冰箱', RECEIPT: '小票', RECIPE: '菜谱截图' }

export function createJob(b: { imageUrl?: string; fileId?: number; mode: VisionMode }): Promise<{ jobId: number; status: string }> {
  return request({ url: '/vision/jobs', method: 'POST', data: b })
}

export function getJob(id: number): Promise<VisionJob> {
  return request({ url: `/vision/jobs/${id}`, method: 'GET' })
}

/** 轮询到 DONE/FAILED（间隔 intervalMs，最多 maxTries 次）。 */
export async function pollJob(
  id: number,
  opts: { intervalMs?: number; maxTries?: number; onTick?: (j: VisionJob) => void; sleep?: (ms: number) => Promise<void> } = {},
): Promise<VisionJob> {
  const sleep = opts.sleep || ((ms: number) => new Promise<void>((r) => setTimeout(r, ms)))
  const max = opts.maxTries ?? 30
  for (let i = 0; i < max; i++) {
    const j = await getJob(id)
    opts.onTick?.(j)
    if (j.status !== 'PENDING') return j
    await sleep(opts.intervalMs ?? 1000)
  }
  throw new Error('识别超时，请重拍一张')
}

export function applyJob(
  id: number,
  items: { name: string; ingredientId?: number | null; amount: number; unit: string; bestBefore?: string | null }[],
): Promise<{ added: number }> {
  return request({
    url: `/vision/jobs/${id}/apply`,
    method: 'POST',
    data: {
      items: items.map((i) => ({
        name: i.name.trim(),
        ...(i.ingredientId ? { ingredientId: i.ingredientId } : {}),
        amount: i.amount,
        unit: i.unit,
        ...(i.bestBefore ? { bestBefore: i.bestBefore } : {}),
      })),
    },
  })
}

/** 置信度分级（spec：≥0.8 默认勾选、0.5–0.8 待确认、<0.5 不展示）。 */
export function confidenceLevel(c: number): 'auto' | 'confirm' | 'hidden' {
  if (c >= 0.8) return 'auto'
  if (c >= 0.5) return 'confirm'
  return 'hidden'
}

export interface ReviewRow extends VisionItem {
  checked: boolean
  tier: 'auto' | 'confirm'
}

/** 结果 → 确认行：隐藏 <0.5；待确认在前；高置信默认勾选，待确认默认不勾（后端 level=AUTO 也视为勾）。 */
export function toReviewRows(items: VisionItem[]): ReviewRow[] {
  const rows: ReviewRow[] = []
  for (const it of items) {
    const lv = confidenceLevel(it.confidence)
    if (lv === 'hidden') continue
    rows.push({ ...it, tier: lv, checked: lv === 'auto' })
  }
  return rows.sort((a, b) => (a.tier === b.tier ? b.confidence - a.confidence : a.tier === 'confirm' ? -1 : 1))
}

/** 「西红柿 → 番茄」对齐提示（rawName 与 name 不同时）。 */
export function aliasHint(it: Pick<VisionItem, 'rawName' | 'name'>): string {
  return it.rawName && it.rawName !== it.name ? `${it.rawName} → ${it.name}` : ''
}

/** 保质期距今天数 → 「3 天后到期」 */
export function bestBeforeText(iso: string | null, today = new Date()): string {
  if (!iso) return '保质期未知'
  const [y, m, d] = iso.slice(0, 10).split('-').map(Number)
  const t0 = new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime()
  const days = Math.round((new Date(y, m - 1, d).getTime() - t0) / 86400000)
  if (days < 0) return '已过期'
  if (days === 0) return '今天到期'
  return `${days} 天后到期`
}
