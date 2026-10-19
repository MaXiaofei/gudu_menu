import { request } from '@/utils/request'

export interface LastChange {
  source?: string | null // cook / cook_partial / purchase / manual / undo
  sourceNote?: string | null
  createTime?: string | null
}

export interface PantryGroupedItem {
  ingredientId: number
  ingredientName?: string | null
  level: string // ENOUGH / LOW / NONE
  lastChange?: LastChange | null
  bestBefore?: string | null // YYYY-MM-DD
  daysLeft?: number | null // 今天到期=0，已过期为负
  iconKey?: string | null
  /** 采购品类名（插画兜底；旧后端缺省） */
  categoryName?: string | null
}

export interface PantryGrouped {
  summary: { enough: number; low: number; none: number }
  items: PantryGroupedItem[]
}

/** 来源标签文案（用完了/用了一些/采购/手动；无变动空）。 */
export function sourceLabel(it: PantryGroupedItem): string {
  switch (it.lastChange?.source) {
    case 'cook': return '用完了'
    case 'cook_partial': return '用了一些'
    case 'purchase': return '采购'
    case 'manual': return '手动'
    case 'undo': return '撤回入库'
    default: return ''
  }
}

/** 来源副文案（备注或 M/D）。 */
export function sourceSub(it: PantryGroupedItem): string {
  const lc = it.lastChange
  if (!lc) return ''
  if (lc.sourceNote) return lc.sourceNote
  if (!lc.createTime) return ''
  const d = new Date(lc.createTime.replace(/-/g, '/'))
  return isNaN(d.getTime()) ? '' : `${d.getMonth() + 1}/${d.getDate()}`
}

/**
 * 分页三色分组列表：GET /pantry/grouped?level=&keyword=&pageNum=&pageSize=
 * summary 恒为搜索范围内三档总数；items 按 level 过滤 + 档位排序 + 切片。每页 10 条（DESIGN.md §12.2）。
 */
export function listGroupedPage(p: {
  level?: string
  keyword?: string
  pageNum: number
  pageSize?: number
}): Promise<PantryGrouped> {
  return request<PantryGrouped>({
    url: '/pantry/grouped',
    method: 'GET',
    data: {
      level: p.level || undefined,
      keyword: p.keyword || undefined,
      pageNum: p.pageNum,
      pageSize: p.pageSize ?? 10,
    },
  })
}

/** 全量分组（入库页建「食材→档位」map 用；全量理由见 DESIGN.md §12）。 */
export function listGroupedAll(): Promise<PantryGrouped> {
  return request<PantryGrouped>({ url: '/pantry/grouped', method: 'GET' })
}

export interface StockLogEntry {
  id: number
  action: string // cook / cook_partial / purchase / manual / undo
  beforeLevel?: string | null
  afterLevel?: string | null
  note?: string | null
  createTime?: string | null
}

export interface PantryItemDetail {
  ingredientId: number
  ingredientName?: string | null
  level: string
  changes: StockLogEntry[]
  bestBefore?: string | null
  daysLeft?: number | null
}

/** 食材详情：GET /pantry/item（档位 + 最近流水）。 */
export function pantryItemDetail(ingredientId: number): Promise<PantryItemDetail> {
  return request<PantryItemDetail>({
    url: '/pantry/item',
    method: 'GET',
    data: { ingredientId },
  })
}

/** 设档位（手动修正）：PUT /pantry/{ingredientId}/level；bestBefore 不传=不改。 */
export function setPantryLevel(ingredientId: number, level: string, bestBefore?: string): Promise<void> {
  return request({
    url: `/pantry/${ingredientId}/level`,
    method: 'PUT',
    data: bestBefore ? { level, bestBefore } : { level },
  })
}

/** 手动入库：POST /pantry/manual（选中已有传 ingredientId，新建传 name）。 */
export function pantryManualAdd(p: {
  ingredientId?: number
  name?: string
  level?: string
  sourceNote?: string
  bestBefore?: string
}): Promise<void> {
  return request({
    url: '/pantry/manual',
    method: 'POST',
    data: {
      ingredientId: p.ingredientId,
      name: p.name,
      level: p.level,
      sourceNote: p.sourceNote,
      bestBefore: p.bestBefore || undefined,
    },
  })
}

/** 临期项（三档库存 + 保质期）。 */
export interface ExpiringItem {
  ingredientId: number
  ingredientName: string
  level: string
  bestBefore: string
  daysLeft: number
  iconKey?: string | null
}

/** 余量统计（P0 契约 GET /pantry/summary）。 */
export interface PantrySummary {
  enough: number
  low: number
  out: number
  expiring3d: number
  byCategory: { categoryId: number; name: string; count: number }[]
}

/**
 * GET /pantry/summary；旧后端无此接口时回退为 grouped.summary + expiring 聚合（byCategory 为空）。
 */
export async function pantrySummary(): Promise<PantrySummary> {
  try {
    const r = await request<PantrySummary>({ url: '/pantry/summary', method: 'GET', silent: true })
    if (r && typeof r.enough === 'number') return { ...r, byCategory: r.byCategory ?? [] }
  } catch {
    /* 回退 */
  }
  const [g, exp] = await Promise.all([listGroupedPage({ pageNum: 1, pageSize: 1 }), listExpiring(3).catch(() => [])])
  return { enough: g.summary.enough, low: g.summary.low, out: g.summary.none, expiring3d: exp.length, byCategory: [] }
}

/** 临期列表：GET /pantry/expiring?days=3（daysLeft 升序，含已过期）。 */
export function listExpiring(days = 3): Promise<ExpiringItem[]> {
  return request<ExpiringItem[]>({ url: '/pantry/expiring', method: 'GET', data: { days } })
}

/** 默认保质天数建议：GET /pantry/shelf-life?ingredientId=（无建议为 null；失败也按 null 处理）。 */
export async function shelfLifeDays(ingredientId: number): Promise<number | null> {
  try {
    const r = await request<{ suggestedDays: number | null }>({
      url: '/pantry/shelf-life',
      method: 'GET',
      data: { ingredientId },
    })
    const d = r?.suggestedDays
    return typeof d === 'number' && d > 0 ? d : null
  } catch {
    return null
  }
}
