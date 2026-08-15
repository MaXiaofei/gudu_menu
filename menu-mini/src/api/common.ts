import { request } from '@/utils/request'

/** 字典项（sys_dict：unit / purchase_category / tag / cuisine / review_dimension…）。 */
export interface DictItem {
  id: number
  name: string
}

// 字典缓存（5 分钟，对齐 Flutter IngredientService._dictCache）
const cache = new Map<string, { at: number; list: DictItem[] }>()
const TTL = 5 * 60 * 1000

export async function listDict(group: string): Promise<DictItem[]> {
  const hit = cache.get(group)
  if (hit && Date.now() - hit.at < TTL) return hit.list
  const data = await request<any>({
    url: '/dict',
    method: 'GET',
    data: { group, pageNum: 1, pageSize: 1000 },
  })
  // 兼容数组 / IPage 两种返回
  const list: DictItem[] = Array.isArray(data)
    ? data
    : (data?.records ?? [])
  cache.set(group, { at: Date.now(), list })
  return list
}

/** 分页结构（后端 IPage，DESIGN.md §12.3）。 */
export interface Page<T> {
  records: T[]
  total: number
}
