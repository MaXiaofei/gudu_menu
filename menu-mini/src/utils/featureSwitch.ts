/**
 * 生成式 AI 紧急关停（出现内容风险 / 审核要求时由后台按类别、按端一键关闭，默认开）。
 * 类别：咕嘟助手 agentChat、问咕嘟 cookAsk、拍照识别 vision、一周餐程 mealPlan；后端按 X-Client 判端，
 * 小程序拿到的就是「小程序」这一端的状态。
 *
 * - 启动 / 进入相关页时 GET /feature-switches 拉取状态，失败按「开」处理；
 * - 收到业务码 5032（咕嘟助手）/ 5033（问咕嘟）/ 5034（拍照识别）/ 5035（一周餐程）时不弹通用报错，直接切到暂停状态；
 * - 关闭的类别：入口隐藏，对应页面由 ui-feature-paused 显示「XX暂停服务，其他功能不受影响」；
 * - 只拦生成 / 发新消息，查看已有内容、删除历史、确认 / 撤销动作等不受影响。
 */
import { reactive } from 'vue'

export const AGENT_SUSPENDED = 5032
export const COOK_ASK_SUSPENDED = 5033
export const VISION_SUSPENDED = 5034
export const MEALPLAN_SUSPENDED = 5035
export const MSG_AGENT_SUSPENDED = '咕嘟助手暂停服务，其他功能不受影响'
export const MSG_COOK_ASK_SUSPENDED = '问咕嘟暂停服务，跟做模式其他功能照常使用'
export const MSG_VISION_SUSPENDED = '拍照识别暂停服务，其他功能不受影响'
export const MSG_MEALPLAN_SUSPENDED = '一周餐程暂停服务，其他功能不受影响'

export type FeatureName = 'agentChat' | 'cookAsk' | 'vision' | 'mealPlan'

/** 各类别暂停时的提示语。 */
export const SUSPENDED_MESSAGE: Record<FeatureName, string> = {
  agentChat: MSG_AGENT_SUSPENDED,
  cookAsk: MSG_COOK_ASK_SUSPENDED,
  vision: MSG_VISION_SUSPENDED,
  mealPlan: MSG_MEALPLAN_SUSPENDED,
}

const CODE_TO_FEATURE: Record<number, FeatureName> = {
  [AGENT_SUSPENDED]: 'agentChat',
  [COOK_ASK_SUSPENDED]: 'cookAsk',
  [VISION_SUSPENDED]: 'vision',
  [MEALPLAN_SUSPENDED]: 'mealPlan',
}

/** true = 可用（默认开）。 */
export const featureStatus = reactive<Record<FeatureName, boolean>>({
  agentChat: true,
  cookAsk: true,
  vision: true,
  mealPlan: true,
})

/** 5032 ~ 5035 → 切到对应暂停状态；返回是否识别为暂停码。 */
export function noteSuspendedCode(code: unknown): boolean {
  const f = CODE_TO_FEATURE[Number(code)]
  if (!f) return false
  featureStatus[f] = false
  return true
}

/** 某个对话上下文当前是否可发消息（cook_mode 看问咕嘟，其余看咕嘟助手）。 */
export function canSendFor(context?: { page?: string } | null): boolean {
  return context?.page === 'cook_mode' ? featureStatus.cookAsk : featureStatus.agentChat
}

/**
 * 本端是否还有任意一类 AI 在开放（咕嘟助手 / 问咕嘟 / 拍照识别 / 一周餐程）。
 * 协议、成员档案说明据此选版本：四类全关才显示「无 AI 版」，任一开着显示完整版（宁可多披露）。
 */
export function anyAiEnabled(): boolean {
  return featureStatus.agentChat || featureStatus.cookAsk || featureStatus.vision || featureStatus.mealPlan
}

/**
 * 拉取本端状态。游客（无 token）也拉取：后端 GET /feature-switches 免登录、只返回四个布尔值，
 * 审核员未登录读协议时也要拿到真实状态；失败保持原状（按开处理）。
 */
export async function loadFeatureSwitches(): Promise<void> {
  try {
    // 动态引入避免与 request.ts 循环依赖
    const { request } = await import('./request')
    const r = await request<Partial<Record<FeatureName, boolean>> | null>({
      url: '/feature-switches',
      method: 'GET',
      silent: true,
    })
    featureStatus.agentChat = r?.agentChat !== false
    featureStatus.cookAsk = r?.cookAsk !== false
    featureStatus.vision = r?.vision !== false
    featureStatus.mealPlan = r?.mealPlan !== false
  } catch {
    // 拉取失败不阻塞：按已知状态（默认开）
  }
}
