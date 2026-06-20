import { request } from '@/utils/request'
import { nutritionMetrics } from './dish'

// 食材库（家庭自建：名 + 单位 + 采购分类 + 6 项 per100g 营养）
// V1 小程序简化录入（ingredient/Create.vue）：名 + AI 补全营养 + 保存

export interface IngredientNutrition {
  metricId: number
  value: number
}
export interface IngredientSaveDTO {
  ingredient: {
    name: string
    unitId: number
    purchaseCategoryId: number
  }
  nutritions: IngredientNutrition[]
}

/** 字典项：单位 / 采购分类（复用配置中心 /dict?group=...） */
export interface DictItem {
  id: number
  name: string
}
export const listDictByGroup = (group: string) =>
  request<{ records: DictItem[] }>({ url: '/dict', method: 'GET', data: { group, pageNum: 1, pageSize: 1000 } }).then(
    (p: any) => p.records || [],
  )

/** 营养指标字典：复用 dish.ts 的 nutritionMetrics（[{id,name,unit,...}]） */
export { nutritionMetrics }

/** 新建食材（对齐后端 POST /ingredient { ingredient, nutritions }） */
export const createIngredient = (data: IngredientSaveDTO) =>
  request<number>({ url: '/ingredient', method: 'POST', data })

/** 全部食材（name + id，供「反向找菜」多选）：后端 GET /ingredient → IPage<IngredientVO>，取 records。 */
export const listAllIngredients = () =>
  request<any>({ url: '/ingredient', method: 'GET', data: { pageNum: 1, pageSize: 1000 } }).then(
    (p: any) => (p && p.records) || [],
  )
