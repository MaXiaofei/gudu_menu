import { request } from '@/utils/request'
import {
  applyLoginResult,
  clearLocalSession,
  getAccountId,
  setAccountId,
  type FamilySummary,
  type LoginResult,
} from '@/utils/family'

/** 账号接口（menu-api AccountController，design §6 /account/**）。 */

export interface AccountView {
  id: number
  nickname: string
  avatarUrl?: string | null
  /** 脱敏手机号，未绑定为 null */
  maskedPhone?: string | null
  wechatBound: boolean
  hasPassword: boolean
  platformRole: string
  status: string
}

export interface AccountMe {
  account: AccountView
  families: FamilySummary[]
  currentFamilyId: number | null
  currentMemberId: number | null
}

export function accountMe(): Promise<AccountMe> {
  return request({ url: '/account/me', method: 'GET' })
}

/**
 * 微信账号并入手机号账号（以手机号账号为主，两边家庭都保留）。
 * 成功后服务端下线当前微信账号的全部 token，返回手机号账号的新 token → 本地整体替换。
 */
export async function mergePhoneAccount(phone: string, password: string): Promise<LoginResult> {
  const r = await request<LoginResult>({
    url: '/account/merge-phone',
    method: 'POST',
    data: { phone: String(phone).trim(), password },
  })
  applyLoginResult(r)
  return r
}

/**
 * 注销账号：成功后清本地登录态并标记主动退出（不再自动静默登录）。
 * 有其他成员的家庭的主理人会被后端拒绝（需先转让或解散），此时抛错、本地登录态不动。
 */
export async function deactivateAccount(): Promise<void> {
  await request({ url: '/account/me', method: 'DELETE' })
  clearLocalSession()
  uni.setStorageSync('logged_out', '1')
}

/** 注销前置检查：我是主理人且家里还有别人的家庭（必须先转让或解散）。 */
export function ownedFamiliesBlockingDeactivation(families: FamilySummary[]): FamilySummary[] {
  return families.filter((f) => f.role === 'OWNER' && (f.memberCount ?? 0) > 1)
}

/** 本地账号 id（升级前登录的旧会话没存 → 拉一次 /account/me 补上）。 */
export async function ensureAccountId(): Promise<number | null> {
  const cached = getAccountId()
  if (cached) return cached
  try {
    const r = await accountMe()
    setAccountId(r.account?.id)
    return r.account?.id ?? null
  } catch {
    return null
  }
}
