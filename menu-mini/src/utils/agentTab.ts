/**
 * 底部第三个 Tab：「咕嘟」或「推荐」。
 *
 * 小程序 tabBar 是 pages.json 静态配置，运行时不能删 Tab；
 * 个人主体小程序不能提供 AI 深度合成，所以 AI 关停（后台开关 agentChat=false）与游客态都不展示咕嘟助手：
 * Tab 页 pages/agent/Index 整页渲染「推荐」内容，tabBar 文字改「推荐」、图标换 static/tabbar/recommend-*。
 * 登录且开关开 → 咕嘟助手，恢复「咕嘟」文字与图标。
 */
import { reactive } from 'vue'

export type AgentTabMode = 'agent' | 'recommend'

/** 咕嘟 Tab 的图标前缀（static/tabbar/{icon}-{theme}[-on].png）：咕嘟 agent，推荐 recommend。 */
export const AGENT_TAB_ITEMS: Record<AgentTabMode, { text: string; icon: string }> = {
  agent: { text: '咕嘟', icon: 'agent' },
  recommend: { text: '推荐', icon: 'recommend' },
}

/** 游客与开关关闭都显示推荐；登录且开关开显示咕嘟。 */
export function resolveAgentTabMode(loggedIn: boolean, agentChat: boolean): AgentTabMode {
  return loggedIn && agentChat ? 'agent' : 'recommend'
}

/** 当前模式（响应式；tabBar 同步见 utils/theme.syncAgentTab）。 */
export const agentTabState = reactive<{ mode: AgentTabMode }>({ mode: 'agent' })
