/** AI 用量与额度页的展示与表单逻辑（纯函数，便于单测）。 */
import type {
  AiGuardCategoryKey,
  AiGuardLevel,
  AiGuardLog,
  AiGuardOverview,
  AiGuardUpdate,
} from '@/api/aiGuard'

export const CATEGORY_KEYS: AiGuardCategoryKey[] = ['vision', 'mealplan', 'agent', 'other']

export interface AiGuardForm {
  dailyBudgetYuan: number
  warnPercent: number
  limits: Record<AiGuardCategoryKey, number>
}

/** 状态文案：≥100% 已暂停；达到提醒比例提醒；否则正常。 */
export function levelText(level: AiGuardLevel, percent: number): string {
  if (level === 'PAUSED') return '今日额度已用完，AI 已暂停（明天自动恢复）'
  if (level === 'WARN') return `今日额度已用 ${percent}%，请关注`
  return '运行正常'
}

/** 进度条颜色。 */
export function levelColor(level: AiGuardLevel): string {
  return level === 'PAUSED' ? 'var(--yh-error)' : level === 'WARN' ? 'var(--yh-warning)' : 'var(--yh-success)'
}

/** 「¥12.30」。 */
export function yuan(v: number): string {
  return `¥${Number(v).toFixed(2)}`
}

/** 上限展示：0 = 不限。 */
export function limitText(v: number, unit: string): string {
  return v > 0 ? `${v} ${unit}` : '不限'
}

export function formFromOverview(o: AiGuardOverview): AiGuardForm {
  const limits = { vision: 0, mealplan: 0, agent: 0, other: 0 } as Record<AiGuardCategoryKey, number>
  for (const c of o.categories) limits[c.key] = c.limitPerAccount
  return { dailyBudgetYuan: o.limitYuan, warnPercent: o.warnPercent, limits }
}

/** 表单校验，返回第一条错误；null 表示通过。 */
export function validateForm(f: AiGuardForm): string | null {
  if (!Number.isFinite(f.dailyBudgetYuan) || f.dailyBudgetYuan < 0 || f.dailyBudgetYuan > 1_000_000) {
    return '每日花费上限需在 0～1000000 元之间（0 = 不限）'
  }
  if (!Number.isInteger(f.warnPercent) || f.warnPercent < 1 || f.warnPercent > 100) return '提醒比例需在 1～100 之间'
  for (const k of CATEGORY_KEYS) {
    const v = f.limits[k]
    if (!Number.isInteger(v) || v < 0 || v > 100_000) return `${CATEGORY_LABEL[k]}次数上限需是 0～100000 的整数（0 = 不限）`
  }
  return null
}

/** 只提交改过的字段；没有改动返回 null。 */
export function buildUpdate(before: AiGuardForm, after: AiGuardForm): AiGuardUpdate | null {
  const body: AiGuardUpdate = {}
  if (after.dailyBudgetYuan !== before.dailyBudgetYuan) body.dailyBudgetYuan = after.dailyBudgetYuan
  if (after.warnPercent !== before.warnPercent) body.warnPercent = after.warnPercent
  const limits: AiGuardUpdate['limits'] = {}
  for (const k of CATEGORY_KEYS) if (after.limits[k] !== before.limits[k]) limits[k] = after.limits[k]
  if (Object.keys(limits).length) body.limits = limits
  return Object.keys(body).length ? body : null
}

export const CATEGORY_LABEL: Record<AiGuardCategoryKey, string> = {
  vision: '拍照识别',
  mealplan: '一周餐程',
  agent: '咕嘟对话',
  other: '其他 AI',
}

/** 日志键 → 中文名。 */
export function logKeyName(key: string): string {
  if (key === 'budget.daily_yuan') return '每日花费上限（元）'
  if (key === 'budget.warn_percent') return '提醒比例（%）'
  if (key.startsWith('limit.')) {
    const k = key.slice(6) as AiGuardCategoryKey
    return `${CATEGORY_LABEL[k] ?? k}次数上限`
  }
  return key
}

/** 「每日花费上限（元）：20 → 30」。 */
export function logText(l: AiGuardLog): string {
  return `${logKeyName(l.key)}：${l.oldValue ?? '默认'} → ${l.newValue}`
}
