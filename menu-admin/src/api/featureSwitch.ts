import { request } from './request'
import type { SwitchChange } from './communityAdmin'

/**
 * 咕嘟助手 / 问咕嘟紧急关停开关：只用于出现内容风险时一键关闭，默认开启。
 * 后端 GET/PUT /admin/feature-switches（运营 / 超管）。
 */
export type FeatureKey = 'agent.chat.enabled' | 'agent.cook_ask.enabled'

export interface FeatureSwitchState extends SwitchChange {
  key: FeatureKey
  /** 展示名：咕嘟助手 / 问咕嘟 */
  label: string
  recentChanges: SwitchChange[]
}

export function listFeatureSwitches() {
  return request<FeatureSwitchState[]>({ url: '/admin/feature-switches', method: 'get' })
}

export function setFeatureSwitch(body: { key: FeatureKey; enabled: boolean; reason?: string }) {
  return request<FeatureSwitchState>({ url: '/admin/feature-switches', method: 'put', data: body })
}
