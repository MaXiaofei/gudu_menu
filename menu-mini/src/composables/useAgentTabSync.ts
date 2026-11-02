import { onShow } from '@dcloudio/uni-app'
import { onTabPageShow } from '@/utils/launch'

/**
 * 底部标签页在每次显示时重新同步「咕嘟 / 推荐」标签并节流拉开关：
 * setTabBarItem 在非 tab 页会静默失败，App.onShow 时首页也可能还没渲染，所以每个 tab 页自己兜底。
 */
export function useAgentTabSync(): void {
  onShow(() => {
    void onTabPageShow()
  })
}
