// 运营后台展示规则（纯函数，便于单测）：状态 / 角色 / 用途文案、注销超期、查询参数清洗。

export const ACCOUNT_STATUS: Record<string, string> = {
  ACTIVE: '正常',
  DEACTIVATED: '已注销',
  MERGED: '已合并',
}

export const PLATFORM_ROLE: Record<string, string> = {
  USER: '用户',
  OPERATOR: '平台运营',
  SUPER_ADMIN: '超级管理员',
}

export const PURPOSE: Record<string, string> = {
  NICKNAME: '昵称',
  AVATAR: '头像',
  AGENT_INPUT: 'AI 对话输入',
  AGENT_OUTPUT: 'AI 对话输出',
  FEEDBACK: '意见反馈',
  POST: '厨房圈帖子',
  COMMENT: '评论',
  UPLOAD: '上传图片',
}

export const DOC_TYPE: Record<string, string> = {
  terms: '用户协议',
  privacy: '隐私政策',
  sensitive_health: '健康档案单独同意',
  child_info: '儿童信息单独同意',
}

export const DEACTIVATION_STATUS: Record<string, { label: string; type: 'success' | 'warning' | 'danger' | 'info' }> = {
  PENDING: { label: '处理中', type: 'warning' },
  DONE: { label: '已完成', type: 'success' },
  FAILED: { label: '失败待重试', type: 'danger' },
}

export function label(map: Record<string, string>, key: string | null | undefined): string {
  if (!key) return '—'
  return map[key] ?? key
}

/** 图片复核结论文案。 */
export function reviewLabel(reviewStatus: string | null | undefined): string {
  if (reviewStatus === 'RESTORED') return '已恢复（误判）'
  if (reviewStatus === 'CONFIRMED') return '已确认违规'
  return '待复核'
}

/** 图片可执行的复核动作：已恢复后不可再操作；已确认的仍可改判恢复。 */
export function mediaActions(reviewStatus: string | null | undefined): { restore: boolean; confirm: boolean } {
  if (reviewStatus === 'RESTORED') return { restore: false, confirm: false }
  if (reviewStatus === 'CONFIRMED') return { restore: true, confirm: false }
  return { restore: true, confirm: true }
}

/** 注销是否需要高亮：后端已算 overdue；兜底按截止时间判断（未完成且已过截止）。 */
export function deactivationOverdue(item: { status: string; overdue?: boolean; deadline?: string | null }, now = new Date()): boolean {
  if (item.status === 'DONE') return false
  if (item.overdue) return true
  if (!item.deadline) return false
  const d = new Date(item.deadline.replace(' ', 'T'))
  return !Number.isNaN(d.getTime()) && now.getTime() > d.getTime()
}

/** 剩余天数（截止前为正、超期为负，向上取整；无截止返回 null）。 */
export function daysLeft(deadline: string | null | undefined, now = new Date()): number | null {
  if (!deadline) return null
  const d = new Date(deadline.replace(' ', 'T'))
  if (Number.isNaN(d.getTime())) return null
  return Math.ceil((d.getTime() - now.getTime()) / 86400000)
}

/** 账号查询参数清洗：去空、openid 尾号不足 4 位不传、家庭 ID 非正整数不传。 */
export function cleanAccountQuery(q: { keyword?: string; openidTail?: string; familyId?: string | number | null; status?: string; bannedOnly?: boolean }) {
  const out: Record<string, string | number | boolean> = {}
  const kw = (q.keyword ?? '').trim()
  if (kw) out.keyword = kw
  const tail = (q.openidTail ?? '').trim()
  if (tail.length >= 4) out.openidTail = tail
  const fid = Number(q.familyId)
  if (Number.isInteger(fid) && fid > 0) out.familyId = fid
  if (q.status) out.status = q.status
  if (q.bannedOnly) out.bannedOnly = true
  return out
}

/** 原因校验（封禁 / 解封 / 恢复必填，≤200 字）：返回错误文案或 null。 */
export function reasonError(reason: string | null | undefined): string | null {
  const r = (reason ?? '').trim()
  if (!r) return '请填写原因'
  if (r.length > 200) return '原因不超过 200 字'
  return null
}
