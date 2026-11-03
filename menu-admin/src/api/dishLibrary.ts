import { request } from './request'

/**
 * 公共菜谱库运营（后端 /admin/dish-library，仅平台运营）：按上架状态列表、下架（必填原因）、重新上架。
 * 下架后新入口（搜索 / 推荐 / 加入食集 / 餐程候选 / 咕嘟推荐 / 照着做）隐藏，家庭已用的保留。
 */

export type LibraryStatus = 'online' | 'offline' | ''

export interface LibraryRow {
  id: number
  name: string
  coverUrl?: string | null
  prepTime?: number | null
  cookTime?: number | null
  source?: string | null
  sourceName?: string | null
  createTime?: string | null
  offline: boolean
  offlineAt?: string | null
  offlineReason?: string | null
}

export interface LibraryPage {
  records: LibraryRow[]
  total: number
}

const BASE = '/admin/dish-library'

export function listLibrary(params: { keyword?: string; status?: LibraryStatus; pageNum?: number; pageSize?: number }) {
  return request<LibraryPage>({ url: BASE, method: 'get', params })
}

export function offlineDish(id: number, reason: string) {
  return request<LibraryRow>({ url: `${BASE}/${id}/offline`, method: 'post', data: { reason } })
}

export function onlineDish(id: number, reason?: string) {
  return request<LibraryRow>({ url: `${BASE}/${id}/online`, method: 'post', data: { reason } })
}
