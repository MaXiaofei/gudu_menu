import { test, expect, type Page } from '@playwright/test'

// 「我的」新增页面 H5 E2E：关于 / 用户协议 / 隐私政策 / 意见反馈 / 主题外观 / 登录协议勾选。
// 网络统一 page.route mock（后端契约：openspec/changes/feature-gaps-closure/api-contract.md）。

async function mockApi(page: Page, onFeedback?: (body: any) => void) {
  await page.route(/\/gudu\//, async (route) => {
    const req = route.request()
    const url = req.url()
    if (url.includes('/feedback') && req.method() === 'POST') {
      onFeedback?.(req.postDataJSON())
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 0, msg: 'ok', data: 101 }) })
      return
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: '{"code":0,"msg":"ok","data":{}}' })
  })
}

test.describe('H5 关于 / 协议', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => localStorage.setItem('token', 'h5-token'))
    await mockApi(page)
  })

  test('关于页：版本号 + 三个入口，点用户协议进协议页', async ({ page }) => {
    await page.goto('/#/pages/profile/About')
    await expect(page.locator('[data-testid="app-version"]')).toContainText('版本 v1.0.0', { timeout: 15000 })
    await expect(page.locator('.row-label')).toHaveText(['用户协议', '隐私政策', '意见反馈'])
    await page.locator('.row-label', { hasText: '用户协议' }).click()
    await expect(page.locator('.bh-title')).toHaveText('用户协议', { timeout: 10000 })
    await expect(page.locator('.h2').first()).toContainText('服务内容')
    await expect(page.locator('.doc')).toContainText('【运营主体】')
  })

  test('隐私政策页直达渲染正文', async ({ page }) => {
    await page.goto('/#/pages/profile/Legal?type=privacy')
    await expect(page.locator('.bh-title')).toHaveText('隐私政策', { timeout: 15000 })
    expect(await page.locator('.h2').count()).toBeGreaterThan(3)
  })
})

test.describe('H5 意见反馈', () => {
  test('少于 5 字报错；填写合法后提交成功，请求体符合契约', async ({ page }) => {
    await page.addInitScript(() => localStorage.setItem('token', 'h5-token'))
    let body: any = null
    await mockApi(page, (b) => (body = b))
    await page.goto('/#/pages/profile/Feedback')

    const ta = page.locator('[data-testid="feedback-content"] textarea')
    await ta.waitFor({ timeout: 15000 })
    await ta.fill('太慢')
    await page.locator('[data-testid="feedback-submit"]').click()
    await expect(page.locator('[data-testid="feedback-error"]')).toContainText('请至少写 5 个字')
    expect(body).toBeNull()

    await ta.fill('采购清单希望能按超市分区')
    await page.locator('[data-testid="feedback-contact"] input').fill('wx-abc')
    await page.locator('[data-testid="feedback-submit"]').click()
    await expect(page.locator('[data-testid="feedback-done"]')).toContainText('已收到', { timeout: 10000 })
    expect(body.content).toBe('采购清单希望能按超市分区')
    expect(body.contact).toBe('wx-abc')
    expect(body.images).toBeUndefined()
    expect(typeof body.clientInfo).toBe('string')
  })
})

test.describe('H5 主题外观', () => {
  test('切换抹茶禅意：页面 CSS 变量即时生效并持久化', async ({ page }) => {
    await page.addInitScript(() => localStorage.setItem('token', 'h5-token'))
    await mockApi(page)
    await page.goto('/#/pages/profile/Theme')
    await page.locator('[data-testid="theme-matcha"]').click({ timeout: 15000 })
    const primary = () =>
      page.evaluate(() =>
        getComputedStyle(document.querySelector('uni-page-body') as Element).getPropertyValue('--primary').trim(),
      )
    await expect.poll(primary).toBe('#7A9A5B')
    await page.reload()
    await page.locator('[data-testid="theme-matcha"]').waitFor({ timeout: 15000 })
    await expect.poll(primary).toBe('#7A9A5B')
  })
})

test.describe('H5 登录协议勾选', () => {
  test('未勾选点登录：不发登录请求；勾选后发出', async ({ page }) => {
    let loginCalls = 0
    await page.route(/\/gudu\//, async (route) => {
      if (route.request().url().includes('/auth/login')) loginCalls++
      await route.fulfill({ status: 200, contentType: 'application/json', body: '{"code":1,"msg":"账号或密码错误","data":null}' })
    })
    await page.addInitScript(() => localStorage.setItem('logged_out', '1'))
    await page.goto('/#/pages/login/Login')
    await expect(page.locator('[data-testid="agree-row"]')).toContainText('《隐私政策》', { timeout: 15000 })
    const inputs = page.locator('input')
    await inputs.nth(0).fill('13800000001')
    await inputs.nth(1).fill('pwd123')
    await page.locator('.login-btn').click()
    await page.waitForTimeout(500)
    expect(loginCalls).toBe(0)

    await page.locator('[data-testid="agree-box"]').click()
    await page.locator('.login-btn').click()
    await expect.poll(() => loginCalls).toBe(1)
  })
})
