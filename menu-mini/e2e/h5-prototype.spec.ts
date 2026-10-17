import { test, expect, type Page } from '@playwright/test'
import { installMocks } from './fixtures/demo'

// prototype-ui-rollout P1（小程序 H5）：按原型改版的页面冒烟 + 图片落位断言。
// 设 SHOT_DIR=<目录> 时每页另存 390×844 截图，供与 docs/design/prototype 并排验收。
test.use({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, hasTouch: true, isMobile: true })

async function shot(page: Page, name: string) {
  const dir = process.env.SHOT_DIR
  if (!dir) return
  await page.waitForTimeout(600)
  await page.screenshot({ path: `${dir}/${name}.png` })
}

/** 页面内全部 <img> 已加载且无破图。 */
async function expectImagesLoaded(page: Page, min: number) {
  await expect
    .poll(async () =>
      page.evaluate(() => {
        const imgs = Array.from(document.querySelectorAll('uni-image img, img')) as HTMLImageElement[]
        const ok = imgs.filter((i) => i.complete && i.naturalWidth > 0).length
        const broken = imgs.filter((i) => i.complete && i.naturalWidth === 0 && i.src).length
        return broken ? -broken : ok
      }),
      { timeout: 15000 },
    )
    .toBeGreaterThanOrEqual(min)
}

test.describe('H5 原型改版 · 菜谱', () => {
  test('菜谱列表：首推大图 + 封面行 + 覆盖率', async ({ page }) => {
    await installMocks(page)
    await page.goto('/#/pages/dish/List')
    await expect(page.locator('[data-testid="dish-hero"]')).toContainText('滑蛋虾仁', { timeout: 15000 })
    await expect(page.locator('.udrow').first()).toContainText('清蒸鲈鱼')
    await expect(page.locator('.cov').first()).toContainText('家里够 4/5 样 · 缺葱')
    await expectImagesLoaded(page, 4)
    await shot(page, 'Recipes')
  })

  test('菜谱详情：主图 + 用料插画与家里状态 + 横滑步骤图 + 现在就做', async ({ page }) => {
    await installMocks(page)
    await page.goto('/#/pages/dish/Detail?id=2')
    await expect(page.locator('.title')).toContainText('清蒸鲈鱼', { timeout: 15000 })
    await expect(page.locator('.ing-row')).toHaveCount(5)
    await expect(page.locator('.st.expire')).toContainText('明天到期')
    await expect(page.locator('.buy').first()).toContainText('加入采购')
    await expect(page.locator('.cov')).toContainText('家里够 4/5 样')
    await expectImagesLoaded(page, 6)
    await shot(page, 'RecipeDetail')
    await page.locator('[data-testid="cook-now"]').click()
    await expect(page.locator('.mask .sheet .title')).toContainText('清蒸鲈鱼做好了')
    await expect(page.locator('.mask .sheet .row')).toHaveCount(5)
    await shot(page, 'CookSheet')
  })
})

test.describe('H5 原型改版 · 智荐 / 余量 / 食集', () => {
  test('智荐：今晚首推大图 + 快到期 + 你家常做 + 应季 + 最近做过', async ({ page }) => {
    await installMocks(page)
    await page.goto('/#/pages/recommend/Index')
    await expect(page.locator('[data-testid="smart-hero"]')).toContainText('清蒸鲈鱼', { timeout: 15000 })
    await expect(page.locator('[data-testid="sec-expiring"]')).toContainText('鲈鱼')
    await expect(page.locator('[data-testid="sec-frequent"]')).toContainText('滑蛋虾仁')
    await expect(page.locator('[data-testid="sec-seasonal"]')).toContainText('小鸡炖蘑菇')
    await expect(page.locator('[data-testid="sec-recent"]')).toContainText('土豆炖牛肉')
    await expectImagesLoaded(page, 8)
    await shot(page, 'Smart')
    await page.locator('[data-testid="hero-next"]').click()
    await expect(page.locator('[data-testid="smart-hero"]')).toContainText('凉拌菠菜')
  })

  test('我家余量：四格统计 + 先吃这些 + 插画行 + 行内三档', async ({ page }) => {
    await installMocks(page)
    await page.goto('/#/pages/pantry/List')
    await expect(page.locator('[data-testid="eat-first"]')).toContainText('鲈鱼', { timeout: 15000 })
    await expect(page.locator('[data-testid="eat-first"]')).toContainText('今晚做「清蒸鲈鱼」')
    await expect(page.locator('.row').first()).toBeVisible()
    await expect(page.locator('[data-testid="lv-15-NONE"]').first()).toHaveClass(/on/)
    await expectImagesLoaded(page, 6)
    await shot(page, 'Pantry')
  })

  test('食集列表：进行中主卡拼图 + 其他食集小圆图', async ({ page }) => {
    await installMocks(page)
    await page.goto('/#/pages/menu/List')
    await expect(page.locator('[data-testid="active-menu"]')).toContainText('今晚的饭', { timeout: 15000 })
    await expect(page.locator('[data-testid="active-menu"]')).toContainText('5/8')
    await expect(page.locator('.card').first()).toContainText('爸妈来吃饭')
    await expectImagesLoaded(page, 6)
    await shot(page, 'Menus')
  })

  test('食集详情：菜 / 备菜 / 采购 / 一起吃 四个 Tab', async ({ page }) => {
    await installMocks(page)
    await page.goto('/#/pages/menu/Detail?id=21')
    await expect(page.locator('.dish-card')).toHaveCount(4, { timeout: 15000 })
    await expect(page.locator('.dish-card').nth(1)).toContainText('朵朵点的')
    await expectImagesLoaded(page, 4)
    await shot(page, 'MenuDetail')
    await page.locator('[data-testid="seg-prep"]').click()
    await expect(page.locator('.prep-progress')).toContainText('已备 5 / 8')
    await expect(page.locator('.prep-row').first()).toContainText('2 道菜要用')
    await shot(page, 'MenuPrep')
    await page.locator('[data-testid="seg-shop"]').click()
    await expect(page.locator('[data-testid="menu-shop-make"]')).toContainText('生成采购清单 · 3 样')
    await shot(page, 'MenuShop')
    await page.locator('[data-testid="seg-together"]').click()
    await expect(page.locator('.invite-card')).toContainText('K7P2QM')
    await shot(page, 'MenuTogether')
  })
})

test.describe('H5 原型改版 · 其他', () => {
  test('我的：头像 + 三格统计 + 最近食记照片条 + 分组设置', async ({ page }) => {
    await installMocks(page)
    await page.goto('/#/pages/profile/Profile')
    await expect(page.locator('[data-testid="user-card"]')).toContainText('小王', { timeout: 15000 })
    await expect(page.locator('.stats')).toContainText('本月少扔')
    await expect(page.locator('.strip-card')).toContainText('土豆炖牛肉')
    await shot(page, 'Me')
  })

  test('消息：分组 Tab + 未读 + 全部已读', async ({ page }) => {
    await installMocks(page)
    await page.goto('/#/pages/notice/Index')
    await expect(page.locator('[data-testid="notice"]')).toHaveCount(3, { timeout: 15000 })
    await shot(page, 'Notices')
    await page.locator('[data-testid="seg-remind"]').click()
    await expect(page.locator('[data-testid="notice"]')).toHaveCount(2)
    await page.locator('[data-testid="read-all"]').click()
    await expect(page.locator('.item.unread')).toHaveCount(0)
  })

  test('食记：统计 + 带图时间轴', async ({ page }) => {
    await installMocks(page)
    await page.goto('/#/pages/foodlog/Index')
    await expect(page.locator('.tl-row').first()).toContainText('今晚的饭', { timeout: 15000 })
    await shot(page, 'CookLog')
  })

  test('采购清单分享预览：插画横幅 + 分组插画行', async ({ page }) => {
    await installMocks(page)
    await page.goto('/#/pages/shopping/List?listId=7')
    await page.locator('.top-btn', { hasText: '分享' }).click({ timeout: 15000 })
    await expect(page.locator('.share-card')).toContainText('西兰花')
    await shot(page, 'ShopShare')
  })

  test('入库：你家常买插画宫格 → 一键进入定档位', async ({ page }) => {
    await installMocks(page)
    await page.goto('/#/pages/pantry/Add')
    await expect(page.locator('[data-testid="freq-grid"]')).toContainText('小葱', { timeout: 15000 })
    await shot(page, 'PantryAdd')
    await page.locator('.freq-it', { hasText: '小葱' }).click()
    await expect(page.locator('.food-name')).toContainText('小葱')
  })

  test('错误态：重试 + 报错编号，点编号带去意见反馈', async ({ page }) => {
    await installMocks(page, [(u) => (u.pathname.endsWith('/recommend/home') || u.pathname.endsWith('/cookbook/recent') ? undefined : undefined)])
    await page.route(/\/gudu\/(recommend\/home|cookbook\/recent)/, (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', headers: { 'X-Trace-Id': '7f3a91c2e5d04b1a', 'Access-Control-Expose-Headers': 'X-Trace-Id' }, body: '{"code":500,"msg":"服务器开小差"}' }),
    )
    await page.goto('/#/pages/recommend/Index')
    await expect(page.locator('[data-testid="state-error-id"]')).toContainText('7f3a-91c2', { timeout: 15000 })
    await shot(page, 'StateError')
    await page.locator('[data-testid="state-error-id"]').click()
    await expect(page.locator('.attach-txt')).toContainText('7f3a-91c2', { timeout: 10000 })
    await shot(page, 'Feedback')
  })

  test('登录：协议未勾选提示 + 插画拼贴', async ({ page }) => {
    await page.route(/\/gudu\//, (route) => route.fulfill({ status: 200, contentType: 'application/json', body: '{"code":0,"data":{}}' }))
    await page.goto('/#/pages/login/Login')
    await expect(page.locator('[data-testid="agree-hint"]')).toBeVisible({ timeout: 15000 })
    await shot(page, 'Login')
  })
})
