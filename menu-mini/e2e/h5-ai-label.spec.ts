import { test, expect, type Page } from '@playwright/test'
import { installMocks } from './fixtures/demo'
import { SHOWCASE_HANDLERS, installAgentSse } from './fixtures/showcase'

// mini-release-compliance A.4（小程序 H5）：AI 产出位置的「AI 生成」标识、免责提示、AI 身份说明、反馈入口。
test.use({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, hasTouch: true, isMobile: true })

const DISCLAIMER = '仅供参考，不能替代医生或营养师建议'

async function setup(page: Page) {
  await installMocks(page, SHOWCASE_HANDLERS as any)
  await installAgentSse(page)
}

test.describe('H5 AI 生成标识', () => {
  test('咕嘟首屏：AI 身份说明；今晚首推（非模型生成）不标 AI', async ({ page }) => {
    await setup(page)
    await page.goto('/#/pages/agent/Index')
    await expect(page.locator('[data-testid="agent-hero"]')).toContainText('清蒸鲈鱼', { timeout: 15000 })
    await expect(page.locator('[data-testid="ai-intro"]')).toContainText('我是 AI 助手')
    await expect(page.locator('[data-testid="ai-intro"] [data-testid="ai-badge-chip"]')).toHaveText('AI 生成')
    await expect(page.locator('[data-testid="ai-badge"]')).toHaveCount(1)
  })

  test('对话：首屏 AI 身份说明；回复带标识，反馈进意见反馈页（AI 内容）', async ({ page }) => {
    await setup(page)
    await page.goto('/#/pages/agent/Index')
    await page.locator('[data-testid="prompt-0"]').click()
    await expect(page.locator('[data-testid="ai-intro"]')).toContainText('我是 AI 助手', { timeout: 15000 })
    await expect(page.locator('.bubble').first()).toContainText('都只用一点点调味', { timeout: 15000 })
    const badge = page.locator('.foot [data-testid="ai-badge"]').first()
    await expect(badge.locator('[data-testid="ai-badge-chip"]')).toHaveText('AI 生成')
    await badge.locator('[data-testid="ai-feedback"]').click()
    await expect(page.locator('[data-testid="fb-type-ai"]')).toContainText('AI 内容', { timeout: 15000 })
    await expect(page.locator('[data-testid="fb-type-ai"]')).toHaveClass(/on/)
  })

  test('拍照识别结果：标识 + 反馈', async ({ page }) => {
    await setup(page)
    await page.goto('/#/pkg-ai/ScanResult?jobId=5')
    await expect(page.locator('.sum-title')).toContainText('识别到 5 样', { timeout: 15000 })
    await expect(page.locator('[data-testid="ai-badge-chip"]')).toHaveText('AI 生成')
    await expect(page.locator('[data-testid="ai-feedback"]')).toBeVisible()
  })

  test('一周餐程：标识 + 免责提示', async ({ page }) => {
    await setup(page)
    await page.goto('/#/pkg-ai/WeekPlan')
    await expect(page.locator('.check-title')).toContainText('校验通过', { timeout: 15000 })
    await expect(page.locator('[data-testid="weekplan-ai"]')).toContainText('AI 生成')
    await expect(page.locator('[data-testid="weekplan-ai"]')).toContainText(DISCLAIMER)
  })

  test('营养报告（规则计算）：只有健康免责提示，不标 AI', async ({ page }) => {
    await setup(page)
    await page.goto('/#/pkg-ai/Nutrition')
    await expect(page.locator('.al-title')).toContainText('钠 本周日均 2.6g', { timeout: 15000 })
    await expect(page.locator('[data-testid="nutrition-note"]')).toHaveText(DISCLAIMER)
    await expect(page.locator('[data-testid="ai-badge"]')).toHaveCount(0)
  })

  test('智荐（本地向量召回）：不标 AI；家有特殊饮食人群时显示健康免责提示', async ({ page }) => {
    await installMocks(page)
    await page.goto('/#/pages/recommend/Index')
    await expect(page.locator('[data-testid="smart-hero"]')).toContainText('清蒸鲈鱼', { timeout: 15000 })
    await expect(page.locator('[data-testid="ai-badge"]')).toHaveCount(0)
    await expect(page.locator('[data-testid="smart-health"]')).toHaveText(DISCLAIMER)
  })
})
