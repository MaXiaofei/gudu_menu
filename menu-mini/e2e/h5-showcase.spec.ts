import { test, expect, type Page } from '@playwright/test'
import { installMocks } from './fixtures/demo'
import { SHOWCASE_HANDLERS, installAgentSse } from './fixtures/showcase'

// showcase-ai-community（小程序 H5）：导航改版 + 咕嘟 / 厨房圈 / 拍照识别 / 餐程营养 / 跟做 冒烟。
// 接口全部 route mock（contracts.md）；SHOT_DIR=<目录> 时另存 390×844 截图与原型并排验收。
test.use({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, hasTouch: true, isMobile: true })

async function setup(page: Page) {
  await installMocks(page, SHOWCASE_HANDLERS as any)
  await installAgentSse(page)
}

async function shot(page: Page, name: string) {
  const dir = process.env.SHOT_DIR
  if (!dir) return
  await page.waitForTimeout(700)
  await page.screenshot({ path: `${dir}/${name}.png` })
}

test.describe('H5 导航改版', () => {
  test('底部 5 个 Tab：菜谱 / 食集 / 咕嘟 / 厨房圈 / 我家余量；右上角头像进我的', async ({ page }) => {
    await setup(page)
    await page.goto('/#/pages/dish/List')
    const tabs = page.locator('.uni-tabbar__item')
    await expect(tabs).toHaveCount(5, { timeout: 15000 })
    await expect(tabs.nth(2)).toContainText('咕嘟')
    await expect(tabs.nth(3)).toContainText('厨房圈')
    await page.locator('[data-testid="me-avatar"]').first().click()
    await expect(page.locator('[data-testid="profile-back"]')).toBeVisible()
    await expect(page.locator('.row-label', { hasText: '营养报告' })).toBeVisible()
  })

  test('余量页「拍照」、食集页「AI 排餐」、菜谱详情「跟做」入口', async ({ page }) => {
    await setup(page)
    await page.goto('/#/pages/pantry/List')
    await expect(page.locator('[data-testid="fab-scan"]')).toBeVisible({ timeout: 15000 })
    await page.goto('/#/pages/menu/List')
    await expect(page.locator('[data-testid="go-weekplan"]')).toContainText('周一')
    await page.goto('/#/pages/dish/Detail?id=2')
    await expect(page.locator('[data-testid="cook-mode"]')).toBeVisible({ timeout: 15000 })
  })
})

test.describe('H5 咕嘟 Agent', () => {
  test('首屏：开场 + 今晚首推 + 快到期 + 快捷提问 + 最近聊过', async ({ page }) => {
    await setup(page)
    await page.goto('/#/pages/agent/Index')
    await expect(page.locator('[data-testid="agent-hero"]')).toContainText('清蒸鲈鱼', { timeout: 15000 })
    await expect(page.locator('[data-testid="agent-expiring"]')).toContainText('还有 3 样快到期')
    await expect(page.locator('[data-testid="prompt-0"]')).toContainText('冰箱里的能做什么')
    await expect(page.locator('.sess')).toContainText('朵朵这周辅食')
    await shot(page, 'Agent')
  })

  test('对话：流式工具链 + 菜谱卡 → 加进食集确认卡 → 确认 → 撤销', async ({ page }) => {
    await setup(page)
    await page.goto('/#/pages/agent/Index')
    await page.locator('[data-testid="prompt-0"]').click()
    await expect(page.locator('[data-testid="tool-chain"]').first()).toContainText('查了 3 项', { timeout: 15000 })
    await expect(page.locator('[data-testid="rcard-2"]')).toContainText('清蒸鲈鱼')
    await expect(page.locator('.bubble').first()).toContainText('都只用一点点调味')
    await expect(page.locator('.trace').first()).toContainText('7f3a-91c2')
    await shot(page, 'AgentChat')
    await page.locator('.chip', { hasText: '就这几道' }).click()
    const act = page.locator('[data-testid="action-a-menu"]')
    await expect(act).toContainText('内有效', { timeout: 15000 })
    await act.locator('[data-testid="action-confirm"]').click()
    await expect(act).toContainText('已完成')
    await act.locator('[data-testid="action-undo"]').click()
    await expect(act).toContainText('已取消')
  })

  test('语音页（H5 降级打字）：做完回写确认卡 → 改人份 → 确认回写', async ({ page }) => {
    await setup(page)
    await page.goto('/#/pkg-agent/Voice')
    await page.locator('[data-testid="voice-send"]').click()
    const c = page.locator('[data-testid="cooked-a-cook"]')
    await expect(c).toContainText('麻婆豆腐做好了', { timeout: 15000 })
    await expect(c).toContainText('用完了')
    await expect(c).toContainText('不扣')
    await expect(c).toContainText('朵朵没选')
    await shot(page, 'AgentVoice')
    await c.locator('[data-testid="cooked-confirm"]').click()
    await expect(c).toContainText('已回写余量')
  })
})

test.describe('H5 厨房圈', () => {
  test('Feed：话题横幅 + 关注更新 + 双列瀑布流 + 点赞 + 上拉加载', async ({ page }) => {
    await setup(page)
    await page.goto('/#/pages/community/Index')
    await expect(page.locator('[data-testid="topic-banner"]')).toContainText('#一周不重样', { timeout: 15000 })
    await expect(page.locator('.flow .col')).toHaveCount(2)
    await expect(page.locator('.pc')).toHaveCount(6)
    await expect(page.locator('.badge.hot').first()).toContainText('照着做')
    await shot(page, 'Community')
    const like = page.locator('[data-testid="like-300"]')
    await like.click()
    await expect(like).toContainText('1.3k')
    await page.mouse.wheel(0, 4000)
    await expect(page.locator('.pc')).toHaveCount(8, { timeout: 10000 })
    await page.locator('[data-testid="seg-follow"]').click()
    await expect(page.locator('text=还没有关注的厨友')).toBeVisible()
  })

  test('详情：作者 + 关联菜谱「照着做」+ 他们照着做了 + 两级评论 + 发评论', async ({ page }) => {
    await setup(page)
    await page.goto('/#/pkg-community/Post?id=300')
    await expect(page.locator('.title')).toContainText('第一次做清蒸鲈鱼', { timeout: 15000 })
    await expect(page.locator('[data-testid="copy-recipe"]')).toContainText('照着做 · 复制到我家')
    await expect(page.locator('.cov')).toContainText('你家缺 1 样')
    await expect(page.locator('.rp')).toContainText('作者')
    await shot(page, 'Post')
    await page.locator('[data-testid="follow"]').click()
    await expect(page.locator('[data-testid="follow"]')).toContainText('已关注')
    await page.locator('[data-testid="comment-input"] input').fill('马上去试')
    await page.locator('[data-testid="comment-input"] input').press('Enter')
    await expect(page.locator('.cm-text').first()).toContainText('马上去试')
  })

  test('发布：校验提示 + AI 帮我写 + 隐私提示', async ({ page }) => {
    await setup(page)
    await page.goto('/#/pkg-community/Create?fromCookRecordId=8&dishId=1&dishName=%E6%BB%91%E8%9B%8B%E8%99%BE%E4%BB%81&images=' + encodeURIComponent('https://mock.gudu.test/img/dish/huadan-xiaren.jpg'))
    await expect(page.locator('.from')).toContainText('已带入 1 张照片', { timeout: 15000 })
    await page.locator('[data-testid="publish"]').click()
    await expect(page.locator('.uni-toast, uni-toast')).toContainText('写个标题吧')
    await page.locator('[data-testid="ai-write"]').click()
    await expect(page.locator('.ai-text')).toContainText('七成熟')
    await page.locator('[data-testid="ai-use"]').click()
    await expect(page.locator('.privacy')).toContainText('不会带上家庭成员名')
    await shot(page, 'PostCreate')
  })

  test('话题挑战：7 天进度（周一起）+ 排行 + 最新作品', async ({ page }) => {
    await setup(page)
    await page.goto('/#/pkg-community/Topic?id=7')
    await expect(page.locator('.name')).toContainText('#一周不重样', { timeout: 15000 })
    await expect(page.locator('.day')).toHaveCount(7)
    await expect(page.locator('.day-lb').first()).toContainText('周一')
    await expect(page.locator('.c.done')).toHaveCount(4)
    await expect(page.locator('.rank.me')).toContainText('238')
    await shot(page, 'Topic')
  })
})

test.describe('H5 AI 能力', () => {
  test('识别结果：待确认在前、<0.5 不展示、别名提示、放进余量', async ({ page }) => {
    await setup(page)
    await page.goto('/#/pkg-ai/ScanResult?jobId=5')
    await expect(page.locator('.sum-title')).toContainText('识别到 5 样', { timeout: 15000 })
    await expect(page.locator('.sr').first()).toContainText('72%')
    await expect(page.locator('.alias')).toContainText('西红柿 → 番茄')
    await expect(page.locator('[data-testid="scan-apply"]')).toContainText('放进余量 · 3 样')
    await page.locator('.alt', { hasText: '油麦菜' }).click()
    await expect(page.locator('[data-testid="scan-apply"]')).toContainText('放进余量 · 4 样')
    await shot(page, 'ScanResult')
  })

  test('拍照页：模式切换', async ({ page }) => {
    await setup(page)
    await page.goto('/#/pkg-ai/Scan')
    await expect(page.locator('.hint-txt')).toContainText('对准冰箱隔层', { timeout: 15000 })
    await page.locator('[data-testid="mode-RECEIPT"]').click()
    await expect(page.locator('.hint-txt')).toContainText('小票放平')
    await shot(page, 'Scan')
  })

  test('一周餐程：周一 10.19 起 7 天 × 午晚 + 换一道 + 生成', async ({ page }) => {
    await setup(page)
    await page.goto('/#/pkg-ai/WeekPlan')
    await expect(page.locator('.check-title')).toContainText('校验通过', { timeout: 15000 })
    await expect(page.locator('.dl-w').first()).toContainText('周一')
    await expect(page.locator('.dl-d').first()).toContainText('10.19')
    await expect(page.locator('.dl-w').last()).toContainText('周日')
    await expect(page.locator('.cell')).toHaveCount(14)
    await page.locator('.cell').nth(3).click()
    await expect(page.locator('.alt')).toHaveCount(3)
    await shot(page, 'WeekPlan')
    await page.locator('.alt').first().click()
    await expect(page.locator('[data-testid="plan-apply"]')).toContainText('生成 7 个食集和采购清单')
  })

  test('营养报告：超标提醒 + 来源菜 + 换进餐程', async ({ page }) => {
    await setup(page)
    await page.goto('/#/pkg-ai/Nutrition')
    await expect(page.locator('.al-title')).toContainText('钠 本周日均 2.6g', { timeout: 15000 })
    await expect(page.locator('.dish-name')).toContainText(['红烧肉', '麻婆豆腐'])
    await expect(page.locator('[data-testid="swap-into-plan"]')).toBeVisible()
    await shot(page, 'Nutrition')
  })

  test('跟做模式：步骤计时器 + 下一步 + 本地指令', async ({ page }) => {
    await setup(page)
    await page.goto('/#/pkg-ai/CookMode?id=2')
    await expect(page.locator('[data-testid="cook-step-text"]')).toContainText('腌 10 分钟', { timeout: 15000 })
    await page.locator('[data-testid="start-timer-600"]').click()
    await expect(page.locator('.tm')).toHaveCount(1)
    await page.locator('[data-testid="cook-next"]').click()
    await expect(page.locator('[data-testid="cook-step-text"]')).toContainText('大火蒸 8 分钟')
    await expect(page.locator('[data-testid="start-timer-480"]')).toBeVisible()
    await page.locator('[data-testid="cook-ask"] input').fill('上一步')
    await page.locator('[data-testid="cook-ask"] input').press('Enter')
    await expect(page.locator('[data-testid="cook-step-text"]')).toContainText('腌 10 分钟')
    await shot(page, 'CookMode')
  })
})
