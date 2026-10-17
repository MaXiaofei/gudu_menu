/**
 * 一周餐程 + 营养报告（contracts §4）。周 = 周一至周日。
 */
import { request } from '@/utils/request'
import { defaultPlanWeekStart, isoDate } from '@/utils/week'
import type { CDishCard, MemberLite } from './contract'

export type Meal = 'LUNCH' | 'DINNER'

export interface PlanSlot {
  slotId: number
  meal: Meal
  dish: CDishCard
  badges: ('EXPIRING' | 'BABY')[]
}

export interface MealPlanDraft {
  draftId: number
  weekStart: string
  weekEnd: string
  constraints: string[]
  check: { passed: boolean; rounds: number; notes: string[] }
  days: { date: string; weekday: string; note: string | null; slots: PlanSlot[] }[]
  summary: { estCostFen: number; newItems: number; usesExpiring: number }
}

export interface DraftRequest {
  weekStart?: string
  meals: Meal[]
  servings: number
  memberIds: number[]
  weekdayMaxMinutes?: number
  budgetFen?: number
  preferExpiring: boolean
}

export type NutrientKey = 'energy' | 'protein' | 'fat' | 'sodium' | 'fiber'

export interface NutritionReport {
  member: MemberLite & { profile: string }
  range: { from: string; to: string }
  recordCount: number
  radar: { key: NutrientKey; label: string; actual: number; target: number; unit: string; ratio: number }[]
  alerts: {
    key: NutrientKey
    label: string
    dailyAvg: number
    limit: number
    unit: string
    sources: CDishCard[]
    suggestions: CDishCard[]
  }[]
  trend: { key: 'sodium'; points: { date: string; value: number }[]; limit: number }
  goods: string[]
}

/** 默认 weekStart = 下周一（今天周一则本周一）。 */
export function createDraft(b: DraftRequest, today = new Date()): Promise<MealPlanDraft> {
  return request({
    url: '/mealplan/drafts',
    method: 'POST',
    data: { ...b, weekStart: b.weekStart || isoDate(defaultPlanWeekStart(today)) },
    timeout: 60000,
  })
}

export function getDraft(id: number): Promise<MealPlanDraft> {
  return request({ url: `/mealplan/drafts/${id}`, method: 'GET' })
}

export async function slotAlternatives(draftId: number, slotId: number): Promise<(CDishCard & { sodiumMg: number })[]> {
  const r = await request<any>({ url: `/mealplan/drafts/${draftId}/slots/${slotId}/alternatives`, method: 'POST' })
  return r?.items ?? []
}

export function replaceSlot(draftId: number, slotId: number, dishId: number): Promise<MealPlanDraft> {
  return request({ url: `/mealplan/drafts/${draftId}/slots/${slotId}`, method: 'PUT', data: { dishId } })
}

export function applyDraft(draftId: number): Promise<{ menuIds: number[]; shoppingItemCount: number }> {
  return request({ url: `/mealplan/drafts/${draftId}/apply`, method: 'POST' })
}

export function nutritionReport(memberId: number, range: 'week' | 'month' = 'week'): Promise<NutritionReport> {
  return request({ url: '/nutrition/report', method: 'GET', data: { memberId, range } })
}

/** 草稿按周一→周日排序（后端乱序也稳）。 */
export function sortedDays(d: MealPlanDraft): MealPlanDraft['days'] {
  return [...d.days].sort((a, b) => a.date.localeCompare(b.date))
}

/** 某天某餐的格子（没有返回 null）。 */
export function slotOf(day: MealPlanDraft['days'][number], meal: Meal): PlanSlot | null {
  return day.slots.find((s) => s.meal === meal) || null
}

/** 不重样道数。 */
export function distinctDishCount(d: MealPlanDraft): number {
  return new Set(d.days.flatMap((x) => x.slots.map((s) => s.dish.id))).size
}

// ---- 图表几何（营养报告 SVG）----

/** 雷达图顶点：n 轴从正上方顺时针；ratio 截断到 [0, maxRatio]。返回 "x,y x,y …"。 */
export function radarPoints(ratios: number[], cx: number, cy: number, r: number, maxRatio = 1.5): string {
  const n = ratios.length
  return ratios
    .map((v, i) => {
      const a = -Math.PI / 2 + (2 * Math.PI * i) / n
      const rr = (Math.min(Math.max(v, 0), maxRatio) / maxRatio) * r
      return `${round(cx + rr * Math.cos(a))},${round(cy + rr * Math.sin(a))}`
    })
    .join(' ')
}

/** 折线：points → "x,y …"，y 轴 0 ~ max(值, limit)*1.15。 */
export function linePoints(values: number[], w: number, h: number, pad = 8, yMax?: number): string {
  if (!values.length) return ''
  const max = yMax ?? Math.max(...values) * 1.15
  const step = values.length > 1 ? (w - pad * 2) / (values.length - 1) : 0
  return values.map((v, i) => `${round(pad + i * step)},${round(h - pad - (v / (max || 1)) * (h - pad * 2))}`).join(' ')
}

function round(n: number): number {
  return Math.round(n * 10) / 10
}
