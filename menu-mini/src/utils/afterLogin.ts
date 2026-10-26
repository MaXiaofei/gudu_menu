import { listMembers } from '@/api/member'
import {
  getAccountId,
  invitePath,
  onboardKey,
  PENDING_INVITE_KEY,
  profileIsBlank,
  type LoginResult,
} from './family'

export const ONBOARD_PAGE = '/pkg-family/Onboard'
export const HOME_PAGE = '/pages/dish/List'

/**
 * 首次登录引导（multi-tenant-family）：后端已自动建「我的家」并建好本人档案；
 * 本人档案还没填身体 / 健康信息、且本账号没处理过引导 → 去引导页（可跳过，跳过也只弹一次）。
 */
export async function needOnboard(accountId?: number | null): Promise<boolean> {
  const id = accountId || getAccountId()
  if (!id || uni.getStorageSync(onboardKey(id))) return false
  try {
    const members = await listMembers()
    const mine: any = members.find((m: any) => m.accountId === id)
    if (mine && !profileIsBlank(mine.healthProfile)) {
      uni.setStorageSync(onboardKey(id), '1') // 老用户已有档案：不再打扰
      return false
    }
    return !!mine
  } catch {
    return false
  }
}

/** 登录页 redirect 参数只接受站内页面路径（防被拼成外链 / 协议跳转）。 */
export function safeRedirect(raw?: string | null): string {
  if (!raw) return ''
  let url = raw
  try {
    url = decodeURIComponent(raw)
  } catch {
    return ''
  }
  return /^\/(pages|pkg-[a-z]+)\/[A-Za-z/]+(\?.*)?$/.test(url) ? url : ''
}

/**
 * 登录成功后的去向：待加入邀请 > 登录页 redirect（登录回跳）> 首次登录引导 > 首页。
 * 待加入邀请优先：从邀请卡片进来的人，登录后回到落地页继续「加入」。
 */
export async function routeAfterLogin(r?: LoginResult | null, redirect?: string): Promise<string> {
  const pending = uni.getStorageSync(PENDING_INVITE_KEY)
  let url = ''
  if (pending) url = invitePath(String(pending))
  else if (safeRedirect(redirect)) url = safeRedirect(redirect)
  else if (await needOnboard(r?.accountId)) url = ONBOARD_PAGE
  if (url) uni.reLaunch({ url })
  else uni.switchTab({ url: HOME_PAGE })
  return url || HOME_PAGE
}
