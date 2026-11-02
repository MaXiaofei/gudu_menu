/**
 * 生成式 AI 紧急关停（出现内容风险 / 审核要求时由后台按类别、按端一键关闭，默认开）。
 * 类别：咕嘟助手 agentChat、问咕嘟 cookAsk、拍照识别 vision、一周餐程 mealPlan；后端按 X-Client 判端，
 * 小程序拿到的就是「小程序」这一端的状态。
 *
 * - 启动 / 进入相关页时 GET /feature-switches 拉取状态，失败按「开」处理；
 * - 收到业务码 5032（咕嘟助手）/ 5033（问咕嘟）/ 5034（拍照识别）/ 5035（一周餐程）时不弹通用报错，直接切到暂停状态；
 * - 关闭的类别：入口隐藏（页面用 entryOn 判断，未知时也隐藏），对应页面由 ui-feature-paused 显示「XX暂停服务，其他功能不受影响」；
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

/** 本地缓存键：上次从服务端拿到（或收到暂停码确认）的四类开关，启动时先用它，再以服务端为准。 */
export const FEATURE_CACHE_KEY = 'feature_switches_cache'

/**
 * 开关状态是否「已知」：本次启动已从服务端拉到过，或本地有上次的缓存。
 * 未知时标签保守显示「推荐」（宁可晚一步出现「咕嘟」，也不能在关停期间闪出「咕嘟」）。
 */
export const featureState = reactive({ known: false, failed: false })

/**
 * AI 入口（按钮 / 卡片 / 快捷提问）是否显示：状态已知且该类别开才显示，未知时隐藏。
 * featureStatus 默认全是 true，页面绝不能直接用它判断入口显隐，否则开关关闭时入口会在状态拉到前先闪出来。
 */
export function entryOn(name: FeatureName): boolean {
  return featureState.known && featureStatus[name]
}

/**
 * AI 页面盖层状态：未知 -> 'loading'（骨架屏，不闪可操作按钮也不闪暂停提示）；已知关 -> 'paused'；已知开 -> null。
 * 拉取失败且没有缓存时撤掉盖层（null），页面照常可用，后端请求拦截兜底。
 */
export function featureCover(name: FeatureName): 'loading' | 'paused' | null {
  if (!featureState.known) return featureState.failed ? null : 'loading'
  return featureStatus[name] ? null : 'paused'
}

/** 前台节流窗口：底部标签页 onShow 在此窗口内不重复请求。 */
export const FEATURE_THROTTLE_MS = 10_000
/** 前台轮询周期：用户停在页面不操作时，最迟这么久感知到后台改动。 */
export const FEATURE_POLL_MS = 60_000
/** 服务端开关读缓存 TTL（FeatureSwitchAppService.CACHE_TTL = 15 秒，其他实例最迟此时间生效）。 */
export const SERVER_CACHE_TTL_MS = 15_000
/** 后台改开关后，前台用户最长多久能看到（服务端缓存 + 轮询周期）；回前台 / 切页 / 进 AI 页时更快。 */
export const FEATURE_MAX_STALE_MS = SERVER_CACHE_TTL_MS + FEATURE_POLL_MS

type Listener = () => void
const listeners = new Set<Listener>()
/** 订阅开关状态变化（拉取完成 / 收到暂停码 / 恢复缓存）；返回取消订阅函数。theme.ts 用它实时同步底部标签。 */
export function onFeatureStatusChange(cb: Listener): () => void {
  listeners.add(cb)
  return () => listeners.delete(cb)
}
function notify(): void {
  listeners.forEach((cb) => {
    try {
      cb()
    } catch {
      // 订阅者异常不影响开关状态
    }
  })
}

function persist(): void {
  try {
    uni.setStorageSync(FEATURE_CACHE_KEY, {
      agentChat: featureStatus.agentChat,
      cookAsk: featureStatus.cookAsk,
      vision: featureStatus.vision,
      mealPlan: featureStatus.mealPlan,
    })
  } catch {
    // 存储不可用时忽略
  }
}

/** 启动时恢复上次的开关状态（App.onLaunch 最先调用）；缓存缺失 / 损坏则保持「未知」。 */
export function restoreFeatureSwitches(): void {
  let c: any
  try {
    c = uni.getStorageSync(FEATURE_CACHE_KEY)
  } catch {
    return
  }
  const keys: FeatureName[] = ['agentChat', 'cookAsk', 'vision', 'mealPlan']
  if (!c || typeof c !== 'object' || !keys.every((k) => typeof c[k] === 'boolean')) return
  keys.forEach((k) => (featureStatus[k] = c[k]))
  featureState.known = true
  notify()
}

/** 5032 ~ 5035 → 切到对应暂停状态；返回是否识别为暂停码。 */
export function noteSuspendedCode(code: unknown): boolean {
  const f = CODE_TO_FEATURE[Number(code)]
  if (!f) return false
  featureStatus[f] = false
  if (featureState.known) persist()
  notify()
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

let loadSeq = 0
let lastAttemptAt = 0
let inflight: Promise<void> | null = null

/** 仅测试用：清节流时钟与在途请求。 */
export function resetFeatureRefreshClock(): void {
  lastAttemptAt = 0
  inflight = null
}

/**
 * 拉取本端状态（强制，不节流）。游客（无 token）也拉取：后端 GET /feature-switches 免登录、只返回四个布尔值，
 * 审核员未登录读协议时也要拿到真实状态。
 * - 并发时以「最后发起」的请求为准：序号落后的晚到响应直接丢弃，不覆盖新结果；
 * - 失败保持原状（本地缓存的上次结果；没有缓存则仍是「未知」，标签保守显示「推荐」），并通知订阅者按最新登录态重算；
 * - 成功后写入本地缓存。
 */
export async function loadFeatureSwitches(): Promise<void> {
  const seq = ++loadSeq
  lastAttemptAt = Date.now()
  try {
    // 动态引入避免与 request.ts 循环依赖
    const { request } = await import('./request')
    const r = await request<Partial<Record<FeatureName, boolean>> | null>({
      url: '/feature-switches',
      method: 'GET',
      silent: true,
    })
    if (seq !== loadSeq) return // 过期响应
    featureStatus.agentChat = r?.agentChat !== false
    featureStatus.cookAsk = r?.cookAsk !== false
    featureStatus.vision = r?.vision !== false
    featureStatus.mealPlan = r?.mealPlan !== false
    featureState.known = true
    featureState.failed = false
    persist()
  } catch {
    // 拉取失败不阻塞：沿用已知状态
    if (seq === loadSeq && !featureState.known) featureState.failed = true
    if (seq === loadSeq) lastAttemptAt = 0 // 失败不算成功，下次 onShow 立即重试
  }
  if (seq === loadSeq) notify()
}

/** 节流版：窗口内（FEATURE_THROTTLE_MS）已成功拉过则跳过；在途请求共用。底部标签页 onShow 用。 */
export function refreshFeatureSwitchesThrottled(): Promise<void> {
  if (inflight) return inflight
  if (lastAttemptAt && Date.now() - lastAttemptAt < FEATURE_THROTTLE_MS) return Promise.resolve()
  inflight = loadFeatureSwitches().finally(() => (inflight = null))
  return inflight
}

let pollTimer: ReturnType<typeof setInterval> | null = null
/** 前台轮询（App.onShow 开启、onHide 停止）：重复调用只保留一个定时器。 */
export function startFeaturePolling(): void {
  if (pollTimer) return
  pollTimer = setInterval(() => void loadFeatureSwitches(), FEATURE_POLL_MS)
}
export function stopFeaturePolling(): void {
  if (pollTimer) clearInterval(pollTimer)
  pollTimer = null
}
