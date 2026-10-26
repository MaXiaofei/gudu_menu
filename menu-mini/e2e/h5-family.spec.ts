import { test, expect, type Page, type Route } from '@playwright/test'

// 多家庭（multi-tenant-family F.1）H5 主链路：「我的」页家庭切换、邀请落地（未登录回跳 / 已登录加入）、
// 403 FAMILY_ACCESS_DENIED 回到家庭选择。网络统一 page.route mock（契约以 menu-api FamilyController 为准）。

const FAMS = [
  { id: 1, name: '我的家', role: 'OWNER', memberCount: 1 },
  { id: 2, name: '爸妈家', role: 'MEMBER', memberCount: 3 },
]

type Handler = (route: Route, body: any) => any

async function mockApi(page: Page, routes: Record<string, Handler | any>, calls: { m: string; u: string; body: any }[] = []) {
  await page.route(/\/gudu\//, async (route) => {
    const req = route.request()
    const url = new URL(req.url())
    const path = url.pathname.replace(/^.*\/gudu/, '')
    const key = `${req.method()} ${path}`
    let body: any = null
    try {
      body = req.postDataJSON()
    } catch {}
    calls.push({ m: req.method(), u: path, body })
    const h = routes[key]
    const data = typeof h === 'function' ? h(route, body) : h
    if (data && data.__raw) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(data.__raw) })
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 0, msg: 'ok', data: h === undefined ? null : data }),
    })
  })
  return calls
}

test.describe('H5 家庭切换', () => {
  test('「我的」页显示当前家庭，ActionSheet 切到另一个家庭', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('token', 'h5-token')
      localStorage.setItem('legal_consent', '2026-10-26')
    })
    let current = 1
    const calls = await mockApi(page, {
      'GET /family/mine': () => ({ families: FAMS, currentFamilyId: current }),
      'POST /family/current': (_r: Route, b: any) => {
        current = b.familyId
        return { familyId: current, profileId: 20, role: 'MEMBER' }
      },
      'GET /member': { records: [], total: 0 },
      'GET /member/current': 0,
      'GET /member/me': { id: 1, name: '老张' },
    })
    await page.goto('/#/pages/profile/Profile')
    await expect(page.locator('[data-testid="family-current-name"]')).toHaveText('我的家', { timeout: 15000 })
    await page.locator('[data-testid="family-switch-btn"]').click()
    await page.getByText('切换到「爸妈家」').click()
    await expect(page.locator('[data-testid="family-current-name"]')).toHaveText('爸妈家', { timeout: 10000 })
    const sw = calls.find((c) => c.m === 'POST' && c.u === '/family/current')
    expect(sw?.body).toEqual({ familyId: 2 })
  })

  test('家庭选择页：列出家庭、标记当前，点另一个家庭切换', async ({ page }) => {
    await page.addInitScript(() => localStorage.setItem('token', 'h5-token'))
    let current = 1
    const calls = await mockApi(page, {
      'GET /family/mine': () => ({ families: FAMS, currentFamilyId: current }),
      'POST /family/current': (_r: Route, b: any) => {
        current = b.familyId
        return { familyId: current, profileId: 20, role: 'MEMBER' }
      },
    })
    await page.goto('/#/pkg-family/Switch')
    await expect(page.locator('[data-testid="family-item-1"] [data-testid="family-current"]')).toBeVisible({ timeout: 15000 })
    await page.locator('[data-testid="family-item-2"]').click()
    await expect.poll(() => calls.some((c) => c.m === 'POST' && c.u === '/family/current' && c.body?.familyId === 2)).toBe(true)
  })

  test('403 FAMILY_ACCESS_DENIED：回到家庭选择页并说明原因', async ({ page }) => {
    await page.addInitScript(() => localStorage.setItem('token', 'h5-token'))
    await mockApi(page, {
      'GET /member': { __raw: { code: 403, msg: '你已不在该家庭中，请重新选择家庭', data: { reason: 'FAMILY_ACCESS_DENIED' } } },
      'GET /family/mine': { families: [FAMS[0]], currentFamilyId: null },
    })
    await page.goto('/#/pages/member/Index')
    await expect(page).toHaveURL(/pkg-family\/Switch\?reason=denied/, { timeout: 15000 })
    await expect(page.locator('[data-testid="switch-reason"]')).toContainText('你已不在之前的家庭中')
  })
})

test.describe('H5 家庭邀请落地', () => {
  const PREVIEW = { familyName: '爸妈家', inviterNickname: '老妈', expiresAt: '2099-11-02T10:00:00', usable: true, reason: null }

  test('已登录：预览 → 加入（提交邀请码）→ 进入成员档案引导', async ({ page }) => {
    await page.addInitScript(() => localStorage.setItem('token', 'h5-token'))
    const calls = await mockApi(page, {
      'GET /family/invitations/INV123': PREVIEW,
      'POST /family/join': { familyId: 2, profileId: 20, role: 'MEMBER' },
      'GET /family/mine': { families: FAMS, currentFamilyId: 2 },
      'GET /account/me': { account: { id: 9 }, families: FAMS, currentFamilyId: 2 },
      'GET /member': { records: [{ id: 20, name: '小张', accountId: 9, healthProfile: null }] },
    })
    await page.goto('/#/pkg-family/Invite?code=INV123')
    await expect(page.locator('[data-testid="invite-family"]')).toContainText('爸妈家', { timeout: 15000 })
    await page.locator('[data-testid="invite-join"]').click()
    await expect.poll(() => calls.find((c) => c.u === '/family/join')?.body).toEqual({ code: 'INV123' })
    await expect(page).toHaveURL(/pkg-family\/Onboard\?from=join/, { timeout: 10000 })
    await expect(page.locator('[data-testid="onboard-title"]')).toHaveText('欢迎加入新家庭')
  })

  test('邀请已失效：展示原因，不出现加入按钮', async ({ page }) => {
    await page.addInitScript(() => localStorage.setItem('token', 'h5-token'))
    await mockApi(page, {
      'GET /family/invitations/OLD1': { ...PREVIEW, usable: false, reason: '邀请已过期' },
    })
    await page.goto('/#/pkg-family/Invite?code=OLD1')
    await expect(page.locator('[data-testid="invite-unusable"]')).toContainText('已过期', { timeout: 15000 })
    await expect(page.locator('[data-testid="invite-join"]')).toHaveCount(0)
  })

  test('未登录：记下邀请码 → 登录 → 回到落地页', async ({ page }) => {
    // 已同意协议但无 token：落地页可游客预览
    await page.addInitScript(() => {
      if (!sessionStorage.getItem('seeded')) {
        localStorage.setItem('legal_consent', '2026-10-26')
        localStorage.setItem('logged_out', '1')
        sessionStorage.setItem('seeded', '1')
      }
    })
    await mockApi(page, {
      'GET /family/invitations/INV123': PREVIEW,
      'POST /auth/login': { token: 'new-tok', accountId: 9, nickname: '小张', families: FAMS, currentFamilyId: 1 },
    })
    await page.goto('/#/pkg-family/Invite?code=INV123')
    // App.onLaunch 会把未登录用户送到登录页；邀请码已在启动时记下
    await expect(page.locator('.login-btn')).toBeVisible({ timeout: 15000 })
    expect(await page.evaluate(() => localStorage.getItem('gd_pending_invite'))).toContain('INV123')
    const inputs = page.locator('input')
    await inputs.nth(0).fill('13800000001')
    await inputs.nth(1).fill('pwd123')
    await page.locator('.login-btn').click()
    await expect(page).toHaveURL(/pkg-family\/Invite\?code=INV123/, { timeout: 10000 })
    await expect(page.locator('[data-testid="invite-join"]')).toBeVisible({ timeout: 10000 })
  })
})
