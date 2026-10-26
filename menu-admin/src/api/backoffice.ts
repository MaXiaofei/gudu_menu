import { request } from './request'

/**
 * 运营后台接口（mini-release-compliance F.1–F.4，后端 /admin/**，OPERATOR / SUPER_ADMIN）。
 * 只涉及账号元数据、注销处理、内容安全违规与同意记录，不含任何家庭私有数据。
 */

export interface Page<T> {
  records: T[]
  total: number
}

// ---------------------------------------------------------------- F.1 账号

export interface AccountItem {
  id: number
  nickname: string | null
  maskedPhone: string | null
  openidTail: string | null
  status: 'ACTIVE' | 'DEACTIVATED' | 'MERGED' | string
  platformRole: 'USER' | 'OPERATOR' | 'SUPER_ADMIN' | string
  familyCount: number
  banned: boolean
  banReason: string | null
  bannedAt: string | null
  createdAt: string | null
  deactivatedAt: string | null
}

export interface AccountQuery {
  keyword?: string
  openidTail?: string
  familyId?: number | null
  status?: string
  bannedOnly?: boolean
  pageNum?: number
  pageSize?: number
}

export function listAccounts(params: AccountQuery) {
  return request<Page<AccountItem>>({ url: '/admin/accounts', method: 'get', params })
}

export function banAccount(id: number, reason: string) {
  return request<{ accountId: number; reason: string; bannedAt: string }>({
    url: `/admin/accounts/${id}/ban`, method: 'post', data: { reason },
  })
}

export function unbanAccount(id: number, reason: string) {
  return request<boolean>({ url: `/admin/accounts/${id}/unban`, method: 'post', data: { reason } })
}

// ---------------------------------------------------------------- F.4 同意记录

export interface ConsentItem {
  id: number | null
  accountId: number
  /** terms / privacy / sensitive_health / child_info */
  docType: string
  docVersion: string
  granted: boolean
  channel: string | null
  clientIp: string | null
  createdAt: string
}

export function accountConsents(id: number) {
  return request<ConsentItem[]>({ url: `/admin/accounts/${id}/consents`, method: 'get' })
}

// ---------------------------------------------------------------- F.2 注销

export interface DeactivationStep {
  name: string
  ok: boolean
  detail: string | null
}

export interface DeactivationItem {
  id: number
  accountId: number
  requestedAt: string
  deadline: string
  status: 'PENDING' | 'DONE' | 'FAILED' | string
  steps: DeactivationStep[]
  attempts: number
  lastError: string | null
  completedAt: string | null
  overdue: boolean
}

export interface DeactivationPage extends Page<DeactivationItem> {
  overdueTotal: number
}

export function listDeactivations(params: { status?: string; overdue?: boolean; pageNum?: number; pageSize?: number }) {
  return request<DeactivationPage>({ url: '/admin/deactivations', method: 'get', params })
}

export function retryDeactivation(id: number) {
  return request<DeactivationItem>({ url: `/admin/deactivations/${id}/retry`, method: 'post' })
}

// ---------------------------------------------------------------- F.3 违规复核

export interface MediaViolation {
  id: number
  mediaUrl: string
  accountId: number | null
  purpose: string
  status: string
  label: number
  createTime: string
  /** null 待复核 / CONFIRMED / RESTORED */
  reviewStatus: 'CONFIRMED' | 'RESTORED' | null
  reviewedBy: number | null
  reviewedAt: string | null
}

export interface TextViolation {
  id: number
  accountId: number | null
  purpose: string
  suggest: string
  label: number
  source: string
  excerpt: string | null
  createdAt: string
  confirmed: boolean
  reviewedBy: number | null
  reviewedAt: string | null
}

export function listMediaViolations(params: { review?: string; purpose?: string; accountId?: number | null; pageNum?: number; pageSize?: number }) {
  return request<Page<MediaViolation>>({ url: '/admin/content-violations/media', method: 'get', params })
}

/** 隔离区原图（data URL）；不在隔离区为 null。 */
export function mediaViolationImage(id: number) {
  return request<string | null>({ url: `/admin/content-violations/media/${id}/image`, method: 'get' })
}

export function restoreMedia(id: number, reason: string) {
  return request<{ violation: MediaViolation; filesRestored: boolean; notices?: string[] }>({
    url: `/admin/content-violations/media/${id}/restore`, method: 'post', data: { reason },
  })
}

export function confirmMedia(id: number, reason?: string) {
  return request<MediaViolation>({ url: `/admin/content-violations/media/${id}/confirm`, method: 'post', data: { reason } })
}

export function listTextViolations(params: { confirmed?: boolean | null; purpose?: string; accountId?: number | null; pageNum?: number; pageSize?: number }) {
  return request<Page<TextViolation>>({ url: '/admin/content-violations/text', method: 'get', params })
}

export function confirmText(id: number, reason?: string) {
  return request<TextViolation>({ url: `/admin/content-violations/text/${id}/confirm`, method: 'post', data: { reason } })
}

// ---------------------------------------------------------------- 操作日志

export interface OperationLogItem {
  id: number
  operatorId: number
  operatorName: string | null
  action: string
  actionLabel: string
  targetType: string
  targetId: number
  reason: string | null
  detail: string | null
  createdAt: string
}

export function listOperationLogs(params: { targetType?: string; targetId?: number | null; pageNum?: number; pageSize?: number }) {
  return request<Page<OperationLogItem>>({ url: '/admin/operation-logs', method: 'get', params })
}
