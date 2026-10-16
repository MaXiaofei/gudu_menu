import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { __testUni } from '../setup'

// 登录页：未勾选《用户协议》《隐私政策》不可登录（微信审核要求）；勾选后正常登录。
const auth = vi.hoisted(() => ({ login: vi.fn(), nickname: '' }))
const wx = vi.hoisted(() => ({ silentLogin: vi.fn() }))
vi.mock('@/store/auth', () => ({ useAuthStore: () => auth }))
vi.mock('@/api/auth', () => wx)

import Login from '@/pages/login/Login.vue'

const flush = async () => {
  for (let i = 0; i < 5; i++) {
    await Promise.resolve()
    await nextTick()
  }
}
function button(root: HTMLElement, text: string): HTMLElement {
  const b = Array.from(root.querySelectorAll('button')).find((x) => x.textContent?.replace(/\s/g, '') === text)
  if (!b) throw new Error(`按钮「${text}」未找到`)
  return b as HTMLElement
}
function fill(root: HTMLElement, idx: number, v: string) {
  const ipt = root.querySelectorAll('input')[idx] as HTMLInputElement
  ipt.value = v
  ipt.dispatchEvent(new Event('input'))
}

describe('pages/login 协议勾选', () => {
  let app: App | null = null
  let root: HTMLElement

  beforeEach(async () => {
    auth.login.mockReset().mockResolvedValue(undefined)
    wx.silentLogin.mockReset().mockResolvedValue(true)
    vi.mocked(__testUni.mocks.showToast).mockClear()
    vi.mocked(__testUni.mocks.switchTab).mockClear()
    vi.mocked(__testUni.mocks.navigateTo).mockClear()
    root = document.createElement('div')
    document.body.appendChild(root)
    app = createApp(Login)
    app.config.warnHandler = () => {}
    app.mount(root)
    await flush()
    fill(root, 0, '13800000001')
    fill(root, 1, 'pwd123')
    await flush()
  })

  afterEach(() => {
    app?.unmount()
    root.remove()
  })

  it('未勾选：账号登录被拦截并提示', async () => {
    button(root, '登录').click()
    await flush()
    expect(auth.login).not.toHaveBeenCalled()
    expect(__testUni.mocks.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: expect.stringContaining('用户协议') }))
  })

  it('未勾选：微信一键登录被拦截', async () => {
    button(root, '微信一键登录').click()
    await flush()
    expect(wx.silentLogin).not.toHaveBeenCalled()
  })

  it('勾选后：账号登录正常调用并进首页', async () => {
    ;(root.querySelector('[data-testid="agree-box"]') as HTMLElement).click()
    await flush()
    button(root, '登录').click()
    await flush()
    expect(auth.login).toHaveBeenCalledWith('13800000001', 'pwd123')
    expect(__testUni.mocks.switchTab).toHaveBeenCalledWith({ url: '/pages/dish/List' })
  })

  it('点《隐私政策》跳协议页', async () => {
    const link = Array.from(root.querySelectorAll('text')).find((t) => t.textContent === '《隐私政策》') as HTMLElement
    link.click()
    expect(__testUni.mocks.navigateTo).toHaveBeenCalledWith({ url: '/pages/profile/Legal?type=privacy' })
  })
})
