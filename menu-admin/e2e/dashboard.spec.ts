import { test, expect, type Page } from '@playwright/test'

// 运营仪表盘 E2E：mock GET /ops/stats，覆盖 KPI 渲染 / 趋势图 / 空态 / 错误态 / 主题切换。
test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('gudu-token', 'e2e-token')
    localStorage.setItem('gudu-nickname', 'E2E管理员')
    localStorage.setItem('gudu-is-admin', 'true')
  })
})

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
    await expect(page.getByTestId('kpi-total-members')).toContainText('1,234')
    await expect(page.getByTestId('kpi-total-members')).toContainText('成员数')
    await expect(page.getByTestId('kpi-total-cookRecords')).toContainText('10,000')
    await expect(page.getByTestId('kpi-total-feedbackOpen')).toContainText('3')
    await expect(page.getByTestId('kpi-7d-aiCostYuan')).toContainText('¥3.46')
    await expect(page.getByTestId('kpi-7d-activeMembers')).toContainText('40')
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
    await page.getByTestId('kpi-total-feedbackOpen').click()
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
    await expect(page.getByTestId('kpi-total-members')).toContainText('9')
  })
})
