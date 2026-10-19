import { test, expect, type Page } from '@playwright/test'

// 运营仪表盘 E2E：mock GET /ops/stats，覆盖 KPI 渲染 / 趋势图 / 空态 / 错误态 / 主题切换。
test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('gudu-token', 'e2e-token')
    localStorage.setItem('gudu-nickname', 'E2E管理员')
    localStorage.setItem('gudu-is-admin', 'true')
  })
})

// 1x1 png
const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64')
const ok = (data: unknown) => ({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 0, msg: 'ok', data }) })

function trend(days: number, zero = false) {
  const out = []
  for (let i = 0; i < days; i++) {
    const d = new Date(Date.UTC(2026, 9, 1 + i)).toISOString().slice(0, 10)
    out.push(zero
      ? { date: d, newAccounts: 0, activeMembers: 0, cookRecords: 0, aiCalls: 0 }
      : { date: d, newAccounts: i % 3, activeMembers: 10 + i, cookRecords: 5 + (i % 4), aiCalls: 20 + i * 2 })
  }
  return out
}

async function mockStats(page: Page, body: object) {
  await page.route(/\/gudu\//, (route) => route.fulfill(ok({})))
  await page.route(/\/gudu\/ops\/stats/, (route) => route.fulfill(body))
}

test.describe('运营仪表盘', () => {
  test('渲染累计 / 近 7 天 KPI 与 14 天趋势图', async ({ page }) => {
    let query = ''
    await page.route(/\/gudu\//, (route) => route.fulfill(ok({})))
    await page.route(/\/gudu\/ops\/stats/, (route) => {
      query = new URL(route.request().url()).search
      return route.fulfill(ok({
        totals: { members: 1234, accounts: 56, dishes: 789, cookRecords: 10000, feedbackOpen: 3 },
        last7d: { newAccounts: 5, activeMembers: 40, cookRecords: 88, aiCalls: 120, aiCostYuan: 3.456 },
        trend: trend(14),
      }))
    })

    await page.goto('/home')
    await expect(page.getByTestId('kpi-members')).toContainText('1,234')
    await expect(page.getByTestId('kpi-members')).toContainText('成员档案')
    await expect(page.getByTestId('kpi-members')).toContainText('菜谱 789 道')
    await expect(page.getByTestId('kpi-accounts')).toContainText('近 7 天 +5')
    await expect(page.getByTestId('kpi-cookRecords')).toContainText('累计 10,000 次')
    await expect(page.getByTestId('todo-feedback')).toContainText('3')
    await expect(page.getByTestId('kpi-aiCostYuan')).toContainText('¥3.46')
    await expect(page.getByTestId('kpi-activeMembers')).toContainText('40')
    // 侧栏角标与仪表盘同源（totals.feedbackOpen）
    await expect(page.getByTestId('badge-feedback')).toHaveText('3')
    await expect(page.getByTestId('dash-trend-chart').locator('canvas')).toBeVisible()
    expect(query).toContain('days=14')

    // 切换到抹茶禅意主题，图表仍在（颜色由主题 token 重新 setOption）
    await page.getByRole('button', { name: /主题/ }).click()
    await page.getByRole('menuitem', { name: /抹茶禅意/ }).click()
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'matcha')
    await expect(page.getByTestId('dash-trend-chart').locator('canvas')).toBeVisible()
  })

  test('点击待处理反馈 → 跳转意见反馈页', async ({ page }) => {
    await mockStats(page, ok({
      totals: { members: 1, accounts: 1, dishes: 1, cookRecords: 1, feedbackOpen: 2 },
      last7d: { newAccounts: 0, activeMembers: 0, cookRecords: 0, aiCalls: 0, aiCostYuan: 0 },
      trend: trend(14),
    }))
    await page.goto('/home')
    await page.getByTestId('todo-feedback').click()
    await expect(page).toHaveURL(/\/feedback$/)
  })

  test('趋势全 0 → 空态', async ({ page }) => {
    await mockStats(page, ok({
      totals: { members: 0, accounts: 0, dishes: 0, cookRecords: 0, feedbackOpen: 0 },
      last7d: { newAccounts: 0, activeMembers: 0, cookRecords: 0, aiCalls: 0, aiCostYuan: 0 },
      trend: trend(14, true),
    }))
    await page.goto('/home')
    await expect(page.getByTestId('dash-trend-empty')).toBeVisible()
    await expect(page.getByText('近期暂无数据')).toBeVisible()
  })

  test('接口失败 → 错误态 + 重试成功', async ({ page }) => {
    let calls = 0
    await page.route(/\/gudu\//, (route) => route.fulfill(ok({})))
    await page.route(/\/gudu\/ops\/stats/, (route) => {
      calls++
      if (calls === 1) {
        return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 500, msg: '统计服务异常', data: null }) })
      }
      return route.fulfill(ok({
        totals: { members: 9, accounts: 9, dishes: 9, cookRecords: 9, feedbackOpen: 0 },
        last7d: { newAccounts: 1, activeMembers: 1, cookRecords: 1, aiCalls: 1, aiCostYuan: 0.1 },
        trend: trend(14),
      }))
    })
    await page.goto('/home')
    await expect(page.getByTestId('dash-error')).toBeVisible()
    await expect(page.getByTestId('dash-error')).toContainText('统计服务异常')
    await page.getByRole('button', { name: '重试' }).click()
    await expect(page.getByTestId('kpi-members')).toContainText('9')
  })

  test('最常做的菜显示真实封面 + 待处理（缺营养 / 新反馈）+ 切换天数', async ({ page }) => {
    const statQueries: string[] = []
    await page.route(/\/gudu\//, (route) => route.fulfill(ok({})))
    await page.route(/\/gudu\/uploads\//, (route) => route.fulfill({ status: 200, contentType: 'image/png', body: PNG }))
    await page.route(/\/gudu\/ops\/stats/, (route) => {
      statQueries.push(new URL(route.request().url()).search)
      return route.fulfill(ok({
        totals: { members: 5, accounts: 4, dishes: 300, cookRecords: 40, feedbackOpen: 1 },
        last7d: { newAccounts: 0, activeMembers: 3, cookRecords: 6, aiCalls: 9, aiCostYuan: 0.5 },
        trend: trend(14),
      }))
    })
    await page.route(/\/gudu\/dish\/search/, (route) => route.fulfill(ok({
      records: [
        { id: 1, name: '清蒸鲈鱼', coverUrl: '/gudu/uploads/original/luyu.jpg', cookedCount: 4 },
        { id: 2, name: '滑蛋虾仁', coverUrl: '/gudu/uploads/original/xiaren.jpg', cookedCount: 12 },
        { id: 3, name: '没图的菜', coverUrl: null, cookedCount: 1 },
      ],
      total: 3,
    })))
    await page.route(/\/gudu\/ingredient/, (route) => route.fulfill(ok({
      records: [
        { id: 1, name: '鲈鱼', purchaseCategoryId: 1, nutrition: {} },
        { id: 2, name: '番茄', purchaseCategoryId: 1, nutrition: { calorie: 19 } },
        { id: 3, name: '抽纸', purchaseCategoryId: 2, nutrition: {}, edible: false },
      ],
      total: 3,
    })))
    await page.route(/\/gudu\/feedback/, (route) => route.fulfill(ok({
      records: [{ id: 9, memberId: 3, memberName: '小王', content: '放进余量没反应', status: 'NEW', createTime: '2026-10-16T10:00:00', images: [], contact: null, clientInfo: null }],
      total: 1,
    })))

    await page.goto('/home')
    const top = page.getByTestId('dash-top-dishes')
    await expect(top).toBeVisible()
    await expect(page.getByText('最常做的菜')).toBeVisible()
    // 按做过次数倒序：滑蛋虾仁(12) 排第一；封面取缩略图
    await expect(top.locator('.top-name').first()).toHaveText('滑蛋虾仁')
    await expect(top.locator('img').first()).toHaveAttribute('src', '/gudu/uploads/thumbnail/xiaren.jpg')
    await expect(top).toContainText('做了 12 次')
    // 没图回退首字
    await expect(top.getByTestId('dish-thumb-initial')).toHaveText('没')

    await expect(page.getByTestId('todo-nutrition')).toContainText('1') // 抽纸非食用不计
    await expect(page.getByTestId('todo-nutrition')).toContainText('鲈鱼')
    await expect(page.getByTestId('todo-feedback')).toContainText('小王')
    await expect(page.getByTestId('todo-library')).toContainText('待接入')

    await page.getByRole('button', { name: '30 天' }).click()
    await expect.poll(() => statQueries.at(-1)).toContain('days=30')
  })
})
