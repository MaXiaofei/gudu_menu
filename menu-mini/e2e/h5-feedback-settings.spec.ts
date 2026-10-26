import { test, expect, type Page } from '@playwright/test'

// mini-release-compliance A.11 意见反馈闭环 + A.8 个性化推荐开关（H5，网络 page.route mock）。

const ok = (data: unknown) => ({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 0, msg: 'ok', data }) })

const MINE = {
  id: 31, type: 'ai', sourceRef: 'agent:m9', content: 'AI 说番茄要炖两小时', images: [],
  status: 'REPLIED', reply: '已修正，谢谢你', repliedAt: '2026-10-26T10:00:00', createTime: '2026-10-25T09:00:00',
}

async function mockApi(page: Page, opts: { onFeedback?: (b: any) => void; onPref?: (b: any) => void } = {}) {
  let pref = true
  await page.route(/\/gudu\//, async (route) => {
    const req = route.request()
    const url = new URL(req.url())
    const path = url.pathname.replace(/^\/gudu/, '')
    if (path === '/feedback' && req.method() === 'POST') {
      opts.onFeedback?.(req.postDataJSON())
      return route.fulfill(ok(101))
    }
    if (path === '/feedback/mine') return route.fulfill(ok({ records: [MINE], total: 1 }))
    if (path === '/feedback/mine/31') return route.fulfill(ok(MINE))
    if (path === '/notification') {
      return route.fulfill(ok([{ id: 5, type: 'feedback_reply', title: '你的反馈有回复了', content: '已修正，谢谢你', refId: '31', isRead: 0, createTime: '2026-10-26T10:00:00' }]))
    }
    if (path === '/preference' && req.method() === 'PUT') {
      const b = req.postDataJSON()
      opts.onPref?.(b)
      pref = b.personalizedRecommend
      return route.fulfill(ok({ personalizedRecommend: pref }))
    }
    if (path === '/preference') return route.fulfill(ok({ personalizedRecommend: pref }))
    return route.fulfill(ok({}))
  })
}

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('token', 'h5-token'))
})

test('AI 内容反馈：type=ai + sourceRef 独立字段，clientInfo 不再带来源前缀', async ({ page }) => {
  let body: any = null
  await mockApi(page, { onFeedback: (b) => (body = b) })
  await page.goto('/#/pages/profile/Feedback?type=ai&source=agent&ref=m9')
  const ta = page.locator('[data-testid="feedback-content"] textarea')
  await ta.waitFor({ timeout: 15000 })
  await expect(page.locator('[data-testid="fb-type-ai"]')).toBeVisible()
  await ta.fill('AI 说番茄要炖两小时')
  await page.locator('[data-testid="feedback-submit"]').click()
  await expect(page.locator('[data-testid="feedback-done"]')).toContainText('15 个工作日', { timeout: 10000 })
  expect(body.type).toBe('ai')
  expect(body.sourceRef).toBe('agent:m9')
  expect(body.clientInfo || '').not.toContain('来源')
})

test('投诉举报：type=complaint；一般分类：type=general', async ({ page }) => {
  const bodies: any[] = []
  await mockApi(page, { onFeedback: (b) => bodies.push(b) })
  await page.goto('/#/pages/profile/Feedback')
  const ta = page.locator('[data-testid="feedback-content"] textarea')
  await ta.waitFor({ timeout: 15000 })
  await page.locator('[data-testid="fb-type-complaint"]').click()
  await ta.fill('有菜谱里在打广告')
  await page.locator('[data-testid="feedback-submit"]').click()
  await expect(page.locator('[data-testid="feedback-done"]')).toBeVisible({ timeout: 10000 })
  expect(bodies[0].type).toBe('complaint')
  expect(bodies[0].sourceRef).toBeUndefined()
})

test('我的反馈：列表显示状态与回复 → 点进详情看完整回复', async ({ page }) => {
  await mockApi(page)
  await page.goto('/#/pages/profile/FeedbackMine')
  const item = page.locator('[data-testid="my-feedback"]').first()
  await expect(item).toContainText('AI 说番茄要炖两小时', { timeout: 15000 })
  await expect(item).toContainText('已回复')
  await expect(item.locator('[data-testid="my-feedback-reply"]')).toContainText('已修正，谢谢你')
  await item.click()
  await expect(page.locator('[data-testid="feedback-detail-reply"]')).toContainText('已修正，谢谢你', { timeout: 10000 })
})

test('消息与提醒：反馈回复消息点开进反馈详情', async ({ page }) => {
  await mockApi(page)
  await page.goto('/#/pages/notice/Index')
  await expect(page.locator('[data-testid="notice-feedback-act"]')).toBeVisible({ timeout: 15000 })
  await page.locator('[data-testid="notice"]').first().click()
  await expect(page.locator('[data-testid="feedback-detail"]')).toContainText('AI 说番茄要炖两小时', { timeout: 10000 })
})

test('设置：关闭个性化推荐 → PUT false，说明文案切换', async ({ page }) => {
  const puts: any[] = []
  await mockApi(page, { onPref: (b) => puts.push(b) })
  await page.goto('/#/pages/profile/Settings')
  const toggle = page.locator('[data-testid="personalized-toggle"]')
  await expect(toggle).toHaveAttribute('aria-checked', 'true', { timeout: 15000 })
  await toggle.click()
  await expect(toggle).toHaveAttribute('aria-checked', 'false')
  await expect(page.locator('[data-testid="personalized-hint"]')).toContainText('已关闭')
  expect(puts).toEqual([{ personalizedRecommend: false }])
})
