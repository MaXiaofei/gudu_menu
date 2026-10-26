import { request } from '@/utils/request'

/** 用户偏好（A.8，个人信息保护法第 24 条）：按登录账号存，默认开启个性化推荐。 */
export interface Preference {
  personalizedRecommend: boolean
}

/** GET /preference（缺字段按默认开启）。 */
export async function getPreference(): Promise<Preference> {
  const r = await request<Partial<Preference> | null>({ url: '/preference', method: 'GET' })
  return { personalizedRecommend: r?.personalizedRecommend !== false }
}

/** PUT /preference。 */
export async function setPersonalizedRecommend(on: boolean): Promise<Preference> {
  const r = await request<Partial<Preference> | null>({
    url: '/preference',
    method: 'PUT',
    data: { personalizedRecommend: on },
  })
  return { personalizedRecommend: r?.personalizedRecommend ?? on }
}

/** 设置页说明文案（与隐私政策 §三.5 一致）。 */
export const PERSONALIZED_HINT_ON =
  '开启后，智荐和咕嘟会根据家庭成员档案、做饭记录、家里余量和口味，为你挑选更合适的菜。'
export const PERSONALIZED_HINT_OFF =
  '已关闭：智荐和咕嘟首屏只展示时令菜和大家常做的菜，不使用你的成员档案、饮食记录和口味画像。'
