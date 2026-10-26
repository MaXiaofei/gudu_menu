/**
 * 厨房圈紧急关停（mini-release-compliance A.12）：后端开关关闭时 /community/** 统一回业务码 5031「厨房圈暂停服务」。
 * request.ts 收到后置 suspended=true（不弹报错 toast），厨房圈各页由 ui-community-paused 显示暂停提示；
 * 之后任一厨房圈请求成功即恢复。
 */
import { reactive } from 'vue'

export const COMMUNITY_SUSPENDED = 5031
export const MSG_COMMUNITY_SUSPENDED = '厨房圈暂停服务'

export const communityStatus = reactive({ suspended: false })

export function isCommunityPath(url: string): boolean {
  return /^\/community(\/|\?|$)/.test(url || '')
}

/** request.ts 每次厨房圈响应后调用：5031 → 暂停；成功 → 恢复。 */
export function noteCommunityResponse(url: string, code: unknown): void {
  if (!isCommunityPath(url)) return
  if (Number(code) === COMMUNITY_SUSPENDED) communityStatus.suspended = true
  else if (Number(code) === 0) communityStatus.suspended = false
}
