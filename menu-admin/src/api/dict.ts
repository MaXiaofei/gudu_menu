import { request } from './request'

export interface DictItem {
  id: number
  dictGroup: string
  name: string
  sort: number
}

export interface DictSaveDTO {
  id?: number
  dictGroup: string
  name: string
  sort: number
}

/** 拉取某个 group 下的字典项 */
export function listByGroup(group: string) {
  return request<DictItem[]>({ url: '/dict', method: 'get', params: { group } })
}

export function createDict(data: DictSaveDTO) {
  return request<number>({ url: '/dict', method: 'post', data })
}

export function updateDict(data: DictSaveDTO) {
  return request<void>({ url: '/dict', method: 'put', data })
}

export function deleteDict(id: number) {
  return request<void>({ url: `/dict/${id}`, method: 'delete' })
}

// 营养指标（独立资源，挂在配置中心一起管）
export interface NutritionMetric {
  id: number
  name: string
  unit: string
  metricGroup: string
}

export function listNutritionMetrics() {
  return request<NutritionMetric[]>({ url: '/nutrition/metric', method: 'get' })
}
