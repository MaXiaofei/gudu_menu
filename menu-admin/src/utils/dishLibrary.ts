// 公共菜谱库展示规则（纯函数，便于单测）。

export const REASON_MAX = 200

export const LIBRARY_STATUS_TABS = [
  { value: '', label: '全部' },
  { value: 'online', label: '已上架' },
  { value: 'offline', label: '已下架' },
] as const

/** 下架原因校验：必填，≤200 字。返回错误文案，合法返回 ''。 */
export function offlineReasonError(reason: string | null | undefined): string {
  const r = (reason ?? '').trim()
  if (!r) return '请填写下架原因'
  if (r.length > REASON_MAX) return `下架原因不能超过 ${REASON_MAX} 字`
  return ''
}

export function statusText(offline: boolean | undefined | null): string {
  return offline ? '已下架' : '已上架'
}
