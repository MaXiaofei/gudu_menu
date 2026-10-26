import { test, expect, type Page } from '@playwright/test'

// mini-release-compliance A.7 首次登录完善资料 / A.10 分享落地页登录回跳（H5 端覆盖；
// 小程序关闭厨房圈、隐藏语音是 mp-weixin 构建期行为，由 unit features.spec + 构建产物检查覆盖）。

async function mockApi(page: Page, onPutMe?: (body: any) => void) {
  await page.route(/\/gudu\//, async (route) => {
    const req = route.request()
    if (req.url().includes('/member/me') && req.method() === 'PUT') {
      onPutMe?.(req.postDataJSON())
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: '{"code":0,"msg":"ok","data":{}}' })
  })
}

test.describe('A.7 首次登录完善资料', () => {
  test('默认昵称：弹一次，可跳过，刷新后不再弹', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('token', 'h5-token')
      if (!sessionStorage.getItem('seeded')) {
        localStorage.setItem('nickname', '微信用户')
        sessionStorage.setItem('seeded', '1')
      }
    })
    await mockApi(page)
    await page.goto('/#/pages/dish/List')
    const sheet = page.locator('[data-testid="profile-setup"]')
    await expect(sheet).toBeVisible({ timeout: 15000 })
    await expect(sheet).toContainText('设置头像和昵称')
    await page.locator('[data-testid="profile-setup-skip"]').click()
    await expect(sheet).toHaveCount(0)
    await page.reload()
    await page.waitForTimeout(1500)
    await expect(sheet).toHaveCount(0)
  })

  test('填写昵称完成：调用资料更新接口，弹窗关闭', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('token', 'h5-token')
      localStorage.setItem('nickname', '微信用户')
    })
    let body: any = null
    await mockApi(page, (b) => (body = b))
    await page.goto('/#/pages/dish/List')
    const sheet = page.locator('[data-testid="profile-setup"]')
    await expect(sheet).toBeVisible({ timeout: 15000 })
    await sheet.locator('[data-testid="nickname-input"] input').fill('张妈妈')
    await page.locator('[data-testid="profile-setup-save"]').click()
    await expect(sheet).toHaveCount(0, { timeout: 10000 })
    expect(body).toEqual({ name: '张妈妈' })
  })

  test('昵称已改过的账号不弹', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('token', 'h5-token')
      localStorage.setItem('nickname', '小张')
    })
    await mockApi(page)
    await page.goto('/#/pages/dish/List')
    await page.waitForTimeout(2000)
    await expect(page.locator('[data-testid="profile-setup"]')).toHaveCount(0)
  })
})

test.describe('A.10 聚餐邀请需登录', () => {
  test('未登录打开邀请链接：去登录页且带回跳参数', async ({ page }) => {
    await mockApi(page)
    await page.goto('/#/pages/together/Pick?menuId=7&token=inv1')
    // H5 下登录页是首页，地址为 #/?redirect=…（路由会再编码一层）
    await expect(page).toHaveURL(/[?&]redirect=/, { timeout: 15000 })
    await expect(page.locator('[data-testid="agree-row"]')).toBeVisible()
    let redirect = page.url().split('redirect=')[1] || ''
    for (let i = 0; i < 3 && redirect.includes('%'); i++) redirect = decodeURIComponent(redirect)
    expect(redirect).toBe('/pages/together/Pick?menuId=7&token=inv1')
  })
})
