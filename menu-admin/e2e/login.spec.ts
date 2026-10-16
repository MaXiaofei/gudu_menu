import { test, expect, type Route } from '@playwright/test'

// 登录流程 E2E：用 route mock 拦截后端，避免依赖真实服务。
// 用正则 /\/gudu\// 精确匹配浏览器发出的相对/绝对路径请求。
test.describe('登录流程', () => {
  test('未登录访问根路径 → 重定向到登录页', async ({ page }) => {
    await page.goto('/')
    await expect(page).toHaveURL(/\/login$/)
    await expect(page.getByText('小食单')).toBeVisible()
  })

  test('登录成功 → 跳转首页', async ({ page }) => {
    // mock 登录接口（精确匹配）
    await page.route(/\/auth\/login$/, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 0, msg: 'ok', data: { token: 'e2e-token', nickname: 'E2E管理员', isAdmin: true } }),
      })
    })
    // 其它 /gudu 请求兜底（首页等）
    await page.route(/\/gudu\//, async (route: Route) => {
      if (route.request().url().includes('/auth/login')) {
        await route.fulfill({ status: 200, contentType: 'application/json', body: '{"code":0,"msg":"ok","data":{"token":"e2e-token","nickname":"E2E管理员","isAdmin":true}}' })
        return
      }
      await route.fulfill({ status: 200, contentType: 'application/json', body: '{"code":0,"msg":"ok","data":{}}' })
    })

    await page.goto('/login')
    await page.getByPlaceholder('请输入用户名').fill('admin')
    await page.getByPlaceholder('请输入密码').fill('admin123')
    await page.getByRole('button', { name: /登.*录/ }).click()

    await expect(page).toHaveURL(/\/home$/, { timeout: 10000 })
  })

  test('登录请求带 scope=admin；非超管 → 提示「无后台权限」并停留登录页', async ({ page }) => {
    let sentBody: any = null
    await page.route(/\/auth\/login$/, async (route) => {
      sentBody = route.request().postDataJSON()
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 403, msg: '无后台权限', data: null }),
      })
    })

    await page.goto('/login')
    await page.getByPlaceholder('请输入用户名').fill('admin')
    await page.getByPlaceholder('请输入密码').fill('admin123')
    await page.getByRole('button', { name: /登.*录/ }).click()

    await expect(page.getByText('无后台权限')).toBeVisible()
    await expect(page).toHaveURL(/\/login$/)
    expect(sentBody?.scope).toBe('admin')
    expect(await page.evaluate(() => localStorage.getItem('gudu-token'))).toBeNull()
  })

  test('残留 token 但无 isAdmin → 访问首页被踢回登录页', async ({ page }) => {
    await page.addInitScript(() => localStorage.setItem('gudu-token', 'stale-token'))
    await page.goto('/home')
    await expect(page).toHaveURL(/\/login$/)
  })

  test('错误密码 → 提示错误且停留在登录页', async ({ page }) => {
    await page.route(/\/auth\/login$/, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 1, msg: '用户名或密码错误', data: null }),
      })
    })

    await page.goto('/login')
    await page.getByPlaceholder('请输入用户名').fill('admin')
    await page.getByPlaceholder('请输入密码').fill('admin123')
    await page.getByRole('button', { name: /登.*录/ }).click()

    // 留在登录页
    await expect(page).toHaveURL(/\/login$/)
  })
})
