import { ref } from 'vue'
import { isLoggedIn } from '@/utils/guest'
import { launchAuthDone } from '@/utils/loginRedirect'

/**
 * 页面登录态（游客浏览）：等启动静默登录流程结束后再判定，避免已登录用户闪一下「登录后查看」。
 * 页面 onShow / 初始化时调用 refresh()，返回最新是否已登录。
 */
export function useLoginState() {
  const loggedIn = ref(isLoggedIn())
  async function refresh(): Promise<boolean> {
    await launchAuthDone()
    loggedIn.value = isLoggedIn()
    return loggedIn.value
  }
  return { loggedIn, refresh }
}
