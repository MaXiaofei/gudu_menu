import { request } from './request'
import type { SwitchChange } from './communityAdmin'

/**
 * 生成式 AI 紧急关停开关（4 类 × 小程序 / App）：只用于出现内容风险 / 审核要求时一键关闭，默认开启。
 * 后端 GET/PUT /admin/feature-switches（运营 / 超管）；开关键带端后缀，如 agent.chat.enabled.mp。
 */
export type FeatureCategory = 'agentChat' | 'cookAsk' | 'vision' | 'mealPlan'
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
