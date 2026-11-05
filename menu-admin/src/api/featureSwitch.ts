import { request } from './request'
import type { SwitchChange } from './communityAdmin'

/**
 * 紧急关停开关（小程序 / App）：AI 六类 + 与 AI 独立的页面入口三项。
 * 只用于出现内容风险 / 审核要求时一键关闭；AI 小程序默认开启、App 默认关闭，页面入口两端默认开启。
 * 后端 GET/PUT /admin/feature-switches（运营 / 超管）；开关键带端后缀，如 agent.chat.enabled.mp。
 */
export type FeatureCategory =
  | 'agentChat'
  | 'cookAsk'
  | 'vision'
  | 'mealPlan'
  | 'estimate'
  | 'postText'
  | 'recipeWrite'
  | 'dishDrafts'
  | 'shoppingShare'
export type FeaturePlatform = 'mp' | 'app'
export type FeatureKey =
  | 'agent.chat.enabled.mp'
  | 'agent.chat.enabled.app'
  | 'agent.cook_ask.enabled.mp'
  | 'agent.cook_ask.enabled.app'
  | 'ai.vision.enabled.mp'
  | 'ai.vision.enabled.app'
  | 'ai.mealplan.enabled.mp'
  | 'ai.mealplan.enabled.app'
  | 'ai.estimate.enabled.mp'
  | 'ai.estimate.enabled.app'
  | 'ai.post_text.enabled.mp'
  | 'ai.post_text.enabled.app'
  | 'entry.recipe_write.enabled.mp'
  | 'entry.recipe_write.enabled.app'
  | 'entry.dish_drafts.enabled.mp'
  | 'entry.dish_drafts.enabled.app'
  | 'entry.shopping_share.enabled.mp'
  | 'entry.shopping_share.enabled.app'

export interface FeatureSwitchState extends SwitchChange {
  key: FeatureKey
  category: FeatureCategory
  /** 类别展示名：咕嘟助手 / 问咕嘟 / 拍照识别 / 一周餐程 */
  label: string
  platform: FeaturePlatform
  recentChanges: SwitchChange[]
}

export function listFeatureSwitches() {
  return request<FeatureSwitchState[]>({ url: '/admin/feature-switches', method: 'get' })
}

export function setFeatureSwitch(body: { key: FeatureKey; enabled: boolean; reason?: string }) {
  return request<FeatureSwitchState>({ url: '/admin/feature-switches', method: 'put', data: body })
}

/** 某端一键切换全部类别（关停必填原因，后端逐个记日志）。 */
export function setPlatformSwitches(body: { platform: FeaturePlatform; enabled: boolean; reason?: string }) {
  return request<FeatureSwitchState[]>({ url: '/admin/feature-switches/batch', method: 'put', data: body })
}
