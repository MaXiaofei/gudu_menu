import { request, ApiError, CODE_CONSENT_REQUIRED } from '@/utils/request'
import { ensureSensitiveConsent } from '@/utils/sensitiveConsent'
import type { SensitiveDocType } from '@/api/privacy'

export interface HealthProfile {
  height?: number | null
  weight?: number | null
  age?: number | null
  /** male / female（营养目标 BMR 计算用） */
  gender?: string | null
  /** 特殊人群：高血压 / 高血糖 / 高血脂 / 宝宝辅食 */
  audiences?: string[] | null
  allergies?: string[] | null
  [k: string]: any
}

/**
 * 本家成员档案（multi-tenant-family：/member 只返回当前家庭）。
 * accountId 为空 = 无账号成员（宝宝、老人），由主理人 / 管理员代建。
 */
export interface Member {
  id: number
  name: string
  accountId?: number | null
  healthProfile?: HealthProfile | null
  goal?: string | null
  activityLevel?: string | null
  roleTags?: string[] | string | null
  audienceTags?: string[] | null
}

export interface MemberSave {
  id?: number
  name: string
  healthProfile?: HealthProfile
  goal?: string | null
  activityLevel?: string | null
}

/** 新增本家无账号成员档案（OWNER/ADMIN），返回档案 id。 */
export async function addMember(m: MemberSave): Promise<number> {
  // 走 saveMemberProfile：含健康 / 儿童信息时先弹单独同意窗，428 自动补同意后重试（B.2）
  return (await saveMemberProfile({ ...m, id: undefined })) as number
}

/** 编辑本家成员档案（OWNER/ADMIN 任意；普通成员只能改本人）。 */
export async function updateMember(m: MemberSave & { id: number }): Promise<void> {
  await saveMemberProfile(m)
}

/** 删除本家无账号成员档案（有账号的成员在家庭页移除）。 */
export function deleteMember(id: number): Promise<void> {
  return request({ url: `/member/${id}`, method: 'DELETE' })
}

/** 成员列表（兼容数组 / IPage 两种返回）。 */
export async function listMembers(): Promise<Member[]> {
  const data = await request<any>({ url: '/member', method: 'GET', data: { pageNum: 1, pageSize: 100 } })
  return Array.isArray(data) ? data : (data?.records ?? [])
}

/** 当前成员 id（无则 0）。 */
export function getCurrentMember(): Promise<number> {
  return request<number>({ url: '/member/current', method: 'GET' })
}

/** 切换当前就餐成员（写后端 session）。 */
export function setCurrentMember(memberId: number): Promise<void> {
  return request({
    url: `/member/current?memberId=${memberId}`,
    method: 'POST',
  })
}

/** 当前登录成员资料（GET/PUT /member/me）。phone 已脱敏。 */
export interface MemberMe {
  id: number
  name: string
  avatarUrl?: string | null
  phone?: string | null
  isAdmin?: boolean
}

export function getMe(): Promise<MemberMe> {
  return request<MemberMe>({ url: '/member/me', method: 'GET' })
}

/** 改昵称 / 头像（只传改动字段；avatarUrl 必须是本站 /uploads/ 地址）。 */
export function updateMe(p: { name?: string; avatarUrl?: string }): Promise<void> {
  const data: Record<string, string> = {}
  if (p.name !== undefined) data.name = p.name.trim()
  if (p.avatarUrl) data.avatarUrl = p.avatarUrl
  // 内容安全（A.6）：后端只检有变化的昵称/头像；按本次提交内容给场景文案
  const contentScene = p.avatarUrl ? (p.name !== undefined ? 'profile' : 'avatar') : 'nickname'
  return request({ url: '/member/me', method: 'PUT', data, contentScene })
}

/** 「我的」统计（P0 契约 GET /me/stats）；旧后端无此接口返回 null（页面隐藏或回退本地聚合）。 */
export interface MeStats {
  cookedThisMonth: number
  familyDishes: number
  savedFromWaste: number
}

export async function meStats(): Promise<MeStats | null> {
  try {
    const r = await request<MeStats>({ url: '/me/stats', method: 'GET', silent: true })
    return r && typeof r.cookedThisMonth === 'number' ? r : null
  } catch {
    return null
  }
}

/** 成员档案保存入参（POST/PUT /member 白名单字段）。 */
export interface MemberProfilePayload {
  id?: number
  name?: string
  healthProfile?: Record<string, any> | null
  goal?: string | null
  activityLevel?: string | null
}

/** 用户拒绝单独同意时抛出（页面据此保持编辑态，不提示「保存失败」）。 */
export class ConsentDeclinedError extends Error {
  constructor() {
    super('未同意，未保存健康信息')
    this.name = 'ConsentDeclinedError'
  }
}

/**
 * 保存成员档案（新增无 id / 编辑有 id），返回新增的档案 id（编辑返回 id 原值）。
 * B.2：含健康档案 / 不满 14 周岁儿童信息时，先弹单独同意窗（缺哪项弹哪项），同意后才提交；
 * 后端兜底返回 428（如同意已在别处被撤回）时，按返回的 docTypes 再弹一次并重试一次。
 */
export async function saveMemberProfile(p: MemberProfilePayload): Promise<number | undefined> {
  if (!(await ensureSensitiveConsent(p))) throw new ConsentDeclinedError()
  const submit = () =>
    request<number | null>({ url: '/member', method: p.id ? 'PUT' : 'POST', data: { ...p }, contentScene: 'nickname' })
  try {
    const r = await submit()
    return p.id ?? (r ?? undefined)
  } catch (e) {
    if (!(e instanceof ApiError) || e.code !== CODE_CONSENT_REQUIRED) throw e
    const types = (e.data?.docTypes ?? []) as SensitiveDocType[]
    if (!(await ensureSensitiveConsent(p, types))) throw new ConsentDeclinedError()
    const r = await submit()
    return p.id ?? (r ?? undefined)
  }
}
