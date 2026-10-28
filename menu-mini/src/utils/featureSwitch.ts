/**
 * 咕嘟助手 / 问咕嘟紧急关停（出现内容风险时由后台一键关闭，默认开）。
 *
 * - 启动 / 进入相关页时 GET /feature-switches 拉取状态，失败按「开」处理；
 * - 发消息收到业务码 5032（咕嘟助手暂停）/ 5033（问咕嘟暂停）时不弹通用报错，直接切到暂停状态；
 * - 咕嘟页与对话页由 ui-agent-paused 显示暂停提示，跟做模式隐藏问咕嘟输入区；
 * - 只拦发新消息，查看 / 删除历史、确认 / 撤销动作等不受影响。
 */
import { reactive } from 'vue'

export const AGENT_SUSPENDED = 5032
export const COOK_ASK_SUSPENDED = 5033
export const MSG_AGENT_SUSPENDED = '咕嘟助手暂停服务，其他功能不受影响'
export const MSG_COOK_ASK_SUSPENDED = '问咕嘟暂停服务，跟做模式其他功能照常使用'

/** true = 可用（默认开）。 */
export const featureStatus = reactive({ agentChat: true, cookAsk: true })

/** 5032 / 5033 → 切到暂停状态；返回是否识别为暂停码。 */
export function noteSuspendedCode(code: unknown): boolean {
  const c = Number(code)
  if (c === AGENT_SUSPENDED) {
    featureStatus.agentChat = false
    return true
  }
  if (c === COOK_ASK_SUSPENDED) {
    featureStatus.cookAsk = false
    return true
  }
  return false
}

/** 某个对话上下文当前是否可发消息（cook_mode 看问咕嘟，其余看咕嘟助手）。 */
export function canSendFor(context?: { page?: string } | null): boolean {
  return context?.page === 'cook_mode' ? featureStatus.cookAsk : featureStatus.agentChat
}

/** 拉取用户侧状态；未登录不发请求，失败保持原状（按开处理）。 */
export async function loadFeatureSwitches(): Promise<void> {
  if (!uni.getStorageSync('token')) return
  try {
    // 动态引入避免与 request.ts 循环依赖
    const { request } = await import('./request')
    const r = await request<{ agentChat?: boolean; cookAsk?: boolean } | null>({
      url: '/feature-switches',
      method: 'GET',
      silent: true,
    })
    featureStatus.agentChat = r?.agentChat !== false
    featureStatus.cookAsk = r?.cookAsk !== false
  } catch {
    // 拉取失败不阻塞：按已知状态（默认开）
  }
}
