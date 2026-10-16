import { test, expect } from '@playwright/test'

// H5 登录页 E2E：验证页面结构完整渲染。
//
// 已知限制（uni-app H5 运行时）：
//  - <text> 渲染为 uni-text 自定义元素，getByText 对其长文本匹配不稳定，
//    故品牌/slogan 等用 class 选择器定位（.brand/.slogan/.welcome）。
//  - <input> placeholder 不在 DOM 属性上；<button> 渲染为 uni-button，无原生 button role。
test.describe('H5 登录页', () => {
  test('渲染品牌、slogan、2 个输入框、登录按钮', async ({ page }) => {
    await page.route(/\/gudu\//, async (route) => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '{"code":0,"msg":"ok","data":{}}' })
    })

    await page.goto('/', { waitUntil: 'networkidle' })

    // 品牌 + slogan + 欢迎语（用 class 定位，避免 getByText 对 uni-text 失效）
    await expect(page.locator('.brand')).toContainText('小食单')
    await expect(page.locator('.slogan')).toContainText('家的味道')
    await expect(page.locator('.welcome')).toContainText('欢迎回来')

    // 2 个输入框（用户名 text + 密码 password）
    await expect(page.locator('input')).toHaveCount(2, { timeout: 15000 })
    await expect(page.locator('input[type="password"]')).toHaveCount(1)

    // 登录按钮（uni-button 自定义元素，文本在子节点）
    await expect(page.locator('.login-btn')).toContainText('登 录')
  })
})
