import { test, expect } from '@playwright/test'

// 采购列表 H5 冒烟 E2E：注入登录态后访问采购列表，mock 接口验证渲染。
test.beforeEach(async ({ page }) => {
  // uni-app H5 下 uni storage 落到 localStorage，注入 token 模拟登录态
  await page.addInitScript(() => {
    localStorage.setItem('token', 'h5-token')
  })
})

test.describe('H5 采购列表', () => {
  test('注入登录态 → 访问采购列表 → 渲染空态或列表', async ({ page }) => {
    await page.route(/\/gudu\//, async (route) => {
      const url = route.request().url()
      // 采购列表接口返回空
      if (url.includes('/shopping')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ code: 0, msg: 'ok', data: [] }),
        })
        return
      }
      await route.fulfill({ status: 200, contentType: 'application/json', body: '{"code":0,"msg":"ok","data":{}}' })
    })

    // 直接导航到采购列表 hash 路由
    await page.goto('/#/pages/shopping/List')
    // 空态文案「还没有采购清单」应出现
    await expect(page.getByText('还没有采购清单')).toBeVisible({ timeout: 10000 })
  })
})

test.describe('H5 采购清单转图片分享', () => {
  test('详情 → 分享 → 转图片分享：canvas 生成 PNG 预览 + 下载入口', async ({ page }) => {
    const items = [
      { id: 1, ingredientName: '鸡蛋', purchaseAmount: 10, purchaseUnitName: '个', purchased: 0 },
      { id: 2, customName: '番茄', purchased: 0 },
    ]
    await page.route(/\/gudu\//, async (route) => {
      const url = route.request().url()
      let data: any = {}
      if (/\/shopping\/7(\?|$)/.test(url)) {
        data = { id: 7, name: '周末采购', items, grouped: { '1': items }, categoryNames: { '1': '蔬果' } }
      } else if (url.includes('/shopping')) {
        data = { records: [{ id: 7, name: '周末采购', sourceType: 'MANUAL', createTime: '2026-10-16 10:00:00' }], total: 1 }
      }
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 0, msg: 'ok', data }) })
    })
    await page.goto('/#/pages/shopping/List?listId=7')
    await page.locator('.top-btn', { hasText: '分享' }).click({ timeout: 15000 })
    await page.locator('.share-btn', { hasText: '转图片分享' }).click()
    await expect(page.locator('.img-btn', { hasText: '下载图片' })).toBeVisible({ timeout: 10000 })
    const src = await page.locator('.img-preview').evaluate((el) => {
      const i = el.querySelector('img') as HTMLImageElement | null
      const bg = getComputedStyle(el.querySelector('div') || el).backgroundImage
      return i?.src || bg || ''
    })
    expect(src).toContain('data:image/png')
  })
})
