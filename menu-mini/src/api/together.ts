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
