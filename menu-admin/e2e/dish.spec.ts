import { test, expect } from '@playwright/test'

// 菜品列表 E2E：mock 后端分页接口，验证列表渲染 + 分页。
test.beforeEach(async ({ page, context }) => {
  // 注入登录态（localStorage），跳过登录页直接进入业务页
  await page.addInitScript(() => {
    localStorage.setItem('gudu-token', 'e2e-token')
    localStorage.setItem('gudu-nickname', 'E2E管理员')
    localStorage.setItem('gudu-is-admin', 'true')
  })
})

test.describe('菜品管理页', () => {
  test('进入菜品页 → 渲染分页表格', async ({ page }) => {
    // 其它 /gudu 接口兜底空（先注册，优先级低于后注册的 /gudu/dish）
    await page.route(/\/gudu\//, async (route) => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '{"code":0,"msg":"ok","data":{}}' })
    })
    // mock 菜品分页：限定 /gudu/dish 前缀，避免误拦 /assets/dish-*.js 等静态产物
    await page.route(/\/gudu\/dish/, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          msg: 'ok',
          data: {
            records: [
              { id: 1, name: '番茄炒蛋', difficulty: 2, price: 12 },
              { id: 2, name: '黄瓜炒蛋', difficulty: 1, price: 8 },
            ],
            total: 2,
            current: 1,
            size: 10,
          },
        }),
      })
    })
    await page.goto('/dish')

    // 表格渲染出菜品名
    await expect(page.getByText('番茄炒蛋')).toBeVisible({ timeout: 10000 })
    await expect(page.getByText('黄瓜炒蛋')).toBeVisible()
  })

  test('侧边栏导航含菜谱 / 意见反馈，家庭私有数据页已下线', async ({ page }) => {
    await page.route(/\/gudu\//, async (route) => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '{"code":0,"msg":"ok","data":{"records":[],"total":0}}' })
    })

    await page.goto('/home')
    // 侧边栏导航文字（限定在 .sidebar 容器内，避免与首页卡片标题冲突）
    const sidebar = page.locator('.sidebar')
    // mini-release-compliance F.5：家庭成员 / 库存 / 食集 / 采购 / 周计划页面已下线
    for (const t of ['家庭成员', '库存', '食集', '采购清单', '周计划']) {
      await expect(sidebar.getByText(t, { exact: true })).toHaveCount(0)
    }
    // 2026-07 命名定稿：「菜品」→「菜谱」
    await expect(sidebar.getByText('菜谱', { exact: true })).toBeVisible()
    await expect(sidebar.getByText('意见反馈', { exact: true })).toBeVisible()
  })
})
