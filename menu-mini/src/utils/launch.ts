/**
 * App 启动 / 切前台流程（从 App.vue 抽出，便于单测）。
 *
 * 开关拉取时机见 test/utils/feature-refresh-timing.spec.ts 顶部的时机表。
 */
import { silentLogin } from '@/api/auth'
import { initTheme, syncAgentTab } from '@/utils/theme'
import { hasConsent } from '@/utils/consent'
import {
  loadFeatureSwitches,
  refreshFeatureSwitchesThrottled,
  restoreFeatureSwitches,
  startFeaturePolling,
  stopFeaturePolling,
} from '@/utils/featureSwitch'
import { launchTarget, noteLaunchPath, trackLaunchAuth } from '@/utils/loginRedirect'
import { installRouteGuard } from '@/utils/guest'

/** 回前台 / 冷启动首次显示：先按已知状态同步标签，强制拉取，拉完再同步，并开启前台轮询。 */
export function onAppShow(): Promise<void> {
  syncAgentTab()
  startFeaturePolling()
  return loadFeatureSwitches().then(() => {
    syncAgentTab()
  })
}

/** 切后台：停止轮询。 */
export function onAppHide(): void {
  stopFeaturePolling()
}

/** 底部标签页 onShow：重新同步标签（tabBar 在非 tab 页设置会静默失败）+ 节流拉取。 */
export function onTabPageShow(): Promise<void> {
  syncAgentTab()
  return refreshFeatureSwitchesThrottled().then(() => {
    syncAgentTab()
  })
}

export function onAppLaunch(options?: { path?: string; query?: Record<string, unknown> } | null): void {
  initTheme()
  // 先恢复上次的开关状态再同步标签：缓存为关时第一帧就是「推荐」
  restoreFeatureSwitches()
  syncAgentTab()
  // 游客浏览：冷启动直接进首页（pages.json 第一页），不再先进登录页；游客点到需登录的功能时才提示登录
  installRouteGuard()
  noteLaunchPath(options?.path)
  // 分享落地页（聚餐点菜 / 家庭邀请…）：落地页自己判断登录态，游客操作时再带着原始页面与参数去登录（A.10）；
  // 家庭邀请卡片同时记下邀请码（新微信用户静默登录直接进邀请家庭）
  launchTarget(options)
  // 同步登记启动登录流程：落地页 ensureLogin() 会等它结束再决定是否发请求
  trackLaunchAuth(
    (async () => {
      const token = uni.getStorageSync('token')
      const loggedOut = uni.getStorageSync('logged_out')
      if (token) return
      // 主动退出过 / 尚未勾选协议：保持游客态，不发起任何登录（含 wx.login）
      if (loggedOut || !hasConsent()) return
      // 已同意协议的老用户：wx.login 静默登录（无感）；失败也保持游客态，不跳登录页
      // 成功后：登录态变了，重新拉开关（按新身份）并同步标签
      if (await silentLogin()) {
        syncAgentTab()
        void loadFeatureSwitches()
      }
    })(),
  )
}
