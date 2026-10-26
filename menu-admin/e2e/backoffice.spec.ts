import { test, expect } from '@playwright/test'

// 运营后台（mini-release-compliance F.1–F.3 / multi-tenant-family F.3）：mock /admin/** 接口验证页面行为。
const ok = (data: unknown) => ({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 0, msg: 'ok', data }) })

function login(page: import('@playwright/test').Page, role: 'OPERATOR' | 'SUPER_ADMIN') {
  return page.addInitScript((r) => {
    localStorage.setItem('gudu-token', 'e2e-token')
    localStorage.setItem('gudu-nickname', 'E2E运营')
    localStorage.setItem('gudu-is-admin', 'true')
    localStorage.setItem('gudu-role', r)
  }, role)
}

const account = {
  id: 5, nickname: '小王', maskedPhone: '138****0001', openidTail: 'a1b2c3', status: 'ACTIVE', platformRole: 'USER',
  familyCount: 2, banned: false, banReason: null, bannedAt: null, createdAt: '2026-10-01T10:00:00', deactivatedAt: null,
}

test('平台运营：侧栏隐藏超管专属页；直达超管页回首页', async ({ page }) => {
  await login(page, 'OPERATOR')
  await page.route(/\/gudu\//, (route) => route.fulfill(ok({ records: [], total: 0 })))
  await page.goto('/tenants')
  const sidebar = page.locator('.sidebar')
  await expect(sidebar.getByText('账号与家庭', { exact: true })).toBeVisible()
  await expect(sidebar.getByText('内容安全复核', { exact: true })).toBeVisible()
  await expect(sidebar.getByText('模型网关', { exact: true })).toHaveCount(0)
  await expect(sidebar.getByText('平台运营')).toBeVisible()
  await page.goto('/ai-gateway')
  await expect(page).toHaveURL(/\/home$/)
})

test('账号列表：封禁需填原因，提交后调用封禁接口', async ({ page }) => {
  await login(page, 'OPERATOR')
  let banBody: any = null
  await page.route(/\/gudu\//, (route) => route.fulfill(ok({})))
  await page.route(/\/gudu\/admin\/accounts(\?|$)/, (route) => route.fulfill(ok({ records: [account], total: 1 })))
  await page.route(/\/gudu\/admin\/accounts\/5\/ban/, async (route) => {
    banBody = route.request().postDataJSON()
    await route.fulfill(ok({ accountId: 5, reason: banBody.reason, bannedAt: '2026-10-26T10:00:00' }))
  })
  await page.goto('/tenants')
  const table = page.getByTestId('acc-table')
  await expect(table.getByText('小王')).toBeVisible()
  await expect(table.getByText('138****0001')).toBeVisible()
  await page.getByTestId('acc-ban').click()
  await page.getByTestId('reason-submit').click()
  await expect(page.getByText('请填写原因')).toBeVisible()
  expect(banBody).toBeNull()
  await page.getByRole('dialog').locator('textarea').fill('刷广告')
  await page.getByTestId('reason-submit').click()
  await expect.poll(() => banBody?.reason).toBe('刷广告')
})

test('注销处理：超期记录告警并高亮，失败记录可重试', async ({ page }) => {
  await login(page, 'OPERATOR')
  let retried = false
  await page.route(/\/gudu\//, (route) => route.fulfill(ok({})))
  await page.route(/\/gudu\/admin\/deactivations(\?|$)/, (route) => route.fulfill(ok({
    records: [{
      id: 3, accountId: 9, requestedAt: '2026-09-01T10:00:00', deadline: '2026-09-22T23:59:59', status: 'FAILED',
      steps: [{ name: '账号匿名化与退出家庭', ok: true, detail: null }, { name: '意见反馈联系方式清除', ok: false, detail: 'DB 超时' }],
      attempts: 1, lastError: '意见反馈联系方式清除：DB 超时', completedAt: null, overdue: true,
    }],
    total: 1, overdueTotal: 1,
  })))
  await page.route(/\/gudu\/admin\/deactivations\/3\/retry/, async (route) => {
    retried = true
    await route.fulfill(ok({ id: 3, status: 'DONE', steps: [], attempts: 2, lastError: null, overdue: false }))
  })
  await page.goto('/deactivation')
  await expect(page.getByTestId('deact-overdue-alert')).toContainText('1 条')
  await expect(page.getByTestId('deact-table').locator('tr.is-overdue')).toHaveCount(1)
  await expect(page.getByText('DB 超时')).toBeVisible()
  await page.getByTestId('deact-retry').click()
  await expect.poll(() => retried).toBe(true)
})

test('内容安全复核：图片恢复需填原因', async ({ page }) => {
  await login(page, 'OPERATOR')
  let restoreBody: any = null
  await page.route(/\/gudu\//, (route) => route.fulfill(ok({ records: [], total: 0 })))
  await page.route(/\/gudu\/admin\/content-violations\/media(\?|$)/, (route) => route.fulfill(ok({
    records: [{ id: 7, mediaUrl: '/gudu/uploads/original/a.png', accountId: 5, purpose: 'AVATAR', status: 'RISKY', label: 20002,
      createTime: '2026-10-26T09:00:00', reviewStatus: null, reviewedBy: null, reviewedAt: null }],
    total: 1,
  })))
  await page.route(/\/gudu\/admin\/content-violations\/media\/7\/restore/, async (route) => {
    restoreBody = route.request().postDataJSON()
    await route.fulfill(ok({ violation: { id: 7, reviewStatus: 'RESTORED' }, filesRestored: true }))
  })
  await page.goto('/content-review')
  const table = page.getByTestId('cr-media-table')
  await expect(table.getByText('头像')).toBeVisible()
  await expect(table.getByText('待复核')).toBeVisible()
  await page.getByTestId('cr-restore').click()
  await page.getByRole('dialog').locator('textarea').fill('误判')
  await page.getByRole('dialog').getByRole('button', { name: '确定' }).click()
  await expect.poll(() => restoreBody?.reason).toBe('误判')
})
