// 营养数据判定（纯函数）。后端 /ingredient 列表的 nutrition 为 metric name → 每 100g 数值。

/** 至少有一项有效数值才算「有营养数据」。 */
export function hasNutrition(n: Record<string, unknown> | null | undefined): boolean {
  if (!n || typeof n !== 'object') return false
  return Object.values(n).some((v) => v !== null && v !== undefined && v !== '' && Number.isFinite(Number(v)))
}

export interface NutritionCounts {
  total: number
  withData: number
  missing: number
  /** 非食用（如纸巾），不计营养 */
  inedible: number
}

export function countNutrition(list: { nutrition?: Record<string, unknown> | null; edible?: unknown }[]): NutritionCounts {
  let withData = 0
  let missing = 0
  let inedible = 0
  for (const i of list) {
    if (i.edible === false) inedible++
    else if (hasNutrition(i.nutrition)) withData++
    else missing++
  }
  return { total: list.length, withData, missing, inedible }
}
