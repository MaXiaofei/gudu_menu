import { request } from '@/utils/request'

export interface TogetherMember {
  memberId?: number
  nickname?: string | null
  lastActiveAt?: string | null
}

export interface TogetherDish {
  id: number // menu_dish id
  dishId?: number | null
  dishName?: string | null
  note?: string | null
  addedByNickname?: string | null
}

export interface TogetherActivity {
  nickname?: string | null
  action?: string | null // add / remove
  dishName?: string | null
  createTime?: string | null
}

export interface TogetherVO {
  members: TogetherMember[]
  dishes: TogetherDish[]
  activities: TogetherActivity[]
  invite?: { code?: string | null; token?: string | null } | null
}

/** 生成邀请：POST /menu/{id}/invite → {code, token}。 */
export function createInvite(menuId: number): Promise<{ code: string; token: string }> {
  return request({ url: `/menu/${menuId}/invite`, method: 'POST' })
}

/** 撤销邀请：DELETE /menu/{id}/invite（本家成员；旧口令/链接立即失效）。 */
export function revokeInvite(menuId: number): Promise<void> {
  return request({ url: `/menu/${menuId}/invite`, method: 'DELETE' })
}

/**
 * 凭邀请凭证加入聚餐：POST /invite/{token}/join（须登录）。
 * 非本家用户必须先 join 才能访问该食集聚餐；token 过期 / 已撤销 / 伪造都会被拒。
 */
export function joinInvite(token: string, nickname?: string): Promise<{ menuId: number; menuName?: string }> {
  return request({ url: `/invite/${encodeURIComponent(token)}/join`, method: 'POST', data: { nickname: nickname || '' } })
}

/** 分享路径（小程序卡片 / 小程序码）：menuId + 邀请 token，打开须登录并先 join。 */
export function togetherSharePath(menuId: number, token: string): string {
  return `/pages/together/Pick?menuId=${menuId}&token=${encodeURIComponent(token)}`
}

/** 聚餐清单（发起端登录态 / 朋友端传 guestKey）。 */
export function getTogether(menuId: number, guestKey?: string): Promise<TogetherVO> {
  return request<TogetherVO>({
    url: `/menu/${menuId}/together`,
    method: 'GET',
    ...(guestKey ? { guestKey } : {}),
  })
}

/** 加菜（朋友端）：dishId 或 customName 二选一 + 可选 note。 */
export function addTogetherItem(
  menuId: number,
  body: { dishId?: number; customName?: string; note?: string },
  guestKey?: string,
): Promise<void> {
  return request({
    url: `/menu/${menuId}/together/items`,
    method: 'POST',
    data: body,
    ...(guestKey ? { guestKey } : {}),
  })
}

/** 删菜（已加入成员可删任意菜）。 */
export function removeTogetherItem(menuId: number, menuDishId: number, guestKey?: string): Promise<void> {
  return request({
    url: `/menu/${menuId}/together/items/${menuDishId}`,
    method: 'DELETE',
    ...(guestKey ? { guestKey } : {}),
  })
}

/** 加入聚餐（登录身份，A.10）：POST /invite/{token}/join → {menuId, menuName}；后端校验邀请 token，按账号 upsert。 */
export function joinTogether(token: string): Promise<{ menuId: number; menuName?: string | null }> {
  return request({ url: `/invite/${encodeURIComponent(token)}/join`, method: 'POST', data: {} })
}

/** 改自己在这场聚餐里显示的昵称（完善资料后同步）。 */
export function updateTogetherNickname(menuId: number, nickname: string): Promise<void> {
  return request({ url: `/menu/${menuId}/together/nickname`, method: 'PUT', data: { nickname }, silent: true })
}
