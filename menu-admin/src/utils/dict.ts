/** 配置中心：字典名称 / 营养指标校验与删除影响提示（纯函数，便于单测）。 */

/** 字典名称最长字数（sys_dict.name VARCHAR(64)）。 */
export const DICT_NAME_MAX = 64

/**
 * 字典名称校验：非空、不超长、同分组内不重名（忽略首尾空格与大小写）。
 * @param existing 同分组全部现有名称
 * @param selfName 编辑时自己的原名称（不算重名）
 * @returns 错误文案，通过返回 null
 */
export function validateDictName(name: string, existing: string[], selfName?: string): string | null {
  const n = (name ?? '').trim()
  if (!n) return '请填写名称'
  if (n.length > DICT_NAME_MAX) return `名称不超过 ${DICT_NAME_MAX} 个字`
  const key = (s: string) => s.trim().toLowerCase()
  const self = selfName == null ? null : key(selfName)
  if (existing.some((e) => key(e) === key(n) && key(e) !== self)) return `「${n}」已存在，请换一个名称`
  return null
}

/** 各字典分组被谁引用（删除前提示影响；后端暂无引用数接口，只提示影响范围）。 */
const DICT_USAGE: Record<string, string> = {
  cuisine: '已选用该菜系的菜谱',
  tag: '已打上该标签的菜谱',
  menu_type: '该类型的菜单',
  audience: '已标记该人群的成员档案与菜谱',
  unit: '以该单位记录的用料、库存与采购项',
  purchase_category: '归在该采购分类的食材与采购清单',
  role: '使用该角色的家庭成员',
}

/** 删除确认文案：说明影响范围，不承诺具体引用数。 */
export function dictDeleteWarning(group: string, name: string): string {
  const usage = DICT_USAGE[group] ?? '正在使用该项的数据'
  return `确定删除「${name}」？删除后${usage}会失去这个选项（显示为空或需要重新选择），且无法恢复。`
}

export const METRIC_UNITS = ['g', 'mg', 'kcal', 'index'] as const
export const METRIC_GROUPS: { value: string; label: string }[] = [
  { value: 'macro', label: '宏量' },
  { value: 'micro', label: '微量' },
  { value: 'gi', label: '升糖' },
]

/** 营养指标校验（name 为英文 key，库内唯一；unit / 分组必选）。 */
export function validateMetric(
  m: { name: string; unit: string; metricGroup: string },
  existingNames: string[],
  selfName?: string,
): string | null {
  const name = (m.name ?? '').trim()
  if (!name) return '请填写指标标识'
  if (!/^[a-z][a-z0-9_]{0,31}$/.test(name)) return '指标标识为英文小写字母 / 数字 / 下划线，以字母开头，≤32 位'
  if (existingNames.some((e) => e === name && e !== selfName)) return `指标「${name}」已存在`
  if (!(m.unit ?? '').trim()) return '请选择单位'
  if ((m.unit ?? '').trim().length > 16) return '单位不超过 16 个字符'
  if (!METRIC_GROUPS.some((g) => g.value === m.metricGroup)) return '请选择分组'
  return null
}
