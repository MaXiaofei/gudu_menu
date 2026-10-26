import { test, expect } from '@playwright/test'

// 按原型改版的后台页：公共菜谱库 / 食材与营养 / AI 用量 / 系统健康 / 账号与家庭 / 侧栏。
// 只 mock 现有接口；依赖新接口的区块必须显示「接口待上线」而不是假数据。
test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('gudu-token', 'e2e-token')
    localStorage.setItem('gudu-nickname', 'E2E管理员')
    localStorage.setItem('gudu-is-admin', 'true')
  })
})

const ok = (data: unknown) => ({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 0, msg: 'ok', data }) })
// 1x1 png
const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64')

const ingredients = [
  { id: 1, name: '鲈鱼', purchaseCategoryId: 10, usageCount: 214, nutrition: { calorie: 105, protein: 18.6 } },
  { id: 2, name: '西兰花', purchaseCategoryId: 11, usageCount: 90, nutrition: {} },
  { id: 3, name: '抽纸', purchaseCategoryId: 12, usageCount: 0, nutrition: {}, edible: false },
]
const categories = [
  { id: 10, name: '水产海鲜' },
  { id: 11, name: '蔬菜' },
  { id: 12, name: '其他' },
]

test.describe('侧栏', () => {
  test('按原型分组：运营 7 项 + 内容与基础数据，带反馈角标', async ({ page }) => {
    await page.route(/\/gudu\//, (route) => route.fulfill(ok({})))
    await page.route(/\/gudu\/ops\/stats/, (route) => route.fulfill(ok({ totals: { feedbackOpen: 2 }, last7d: {}, trend: [] })))
    await page.goto('/dish')
    const sidebar = page.locator('.sidebar')
    for (const t of ['仪表盘', '公共菜谱库', '食材与营养', '意见反馈', '账号与家庭', '注销处理', '内容安全复核', '操作日志', 'AI 用量与限额', '系统健康', '内容与基础数据', '菜谱', '配置中心']) {
      await expect(sidebar.getByText(t, { exact: true })).toBeVisible()
    }
    await expect(page.getByTestId('badge-feedback')).toHaveText('2')
    await expect(sidebar.locator('[aria-current="page"]')).toContainText('菜谱')
    await sidebar.getByText('公共菜谱库', { exact: true }).click()
    await expect(page).toHaveURL(/\/library$/)
  })
})

test.describe('公共菜谱库', () => {
  test('已上架：封面缩略图 + 右侧大图预览与用料插画；待审核为接口待上线', async ({ page }) => {
    await page.route(/\/gudu\//, (route) => route.fulfill(ok({})))
    await page.route(/\/gudu\/uploads\//, (route) => route.fulfill({ status: 200, contentType: 'image/png', body: PNG }))
    await page.route(/\/gudu\/dish\/search/, (route) => route.fulfill(ok({
      records: [
        { id: 7, name: '清蒸鲈鱼', coverUrl: '/gudu/uploads/original/luyu.jpg', sourceName: '下厨房', source: 'IMPORT', prepTime: 10, cookTime: 15, cookedCount: 4 },
        { id: 8, name: '麻酱拌面', coverUrl: null, source: 'MANUAL' },
      ],
      total: 2,
    })))
    await page.route(/\/gudu\/dish\/\d+$/, (route) => route.fulfill(ok({
      dish: { id: 7, name: '清蒸鲈鱼' },
      steps: [{ text: '鲈鱼洗净划刀' }, { text: '大火蒸 8 分钟' }],
      cuisineIds: [],
      tagIds: [],
      ingredients: [{ ingredientId: 1, amount: 1, unitName: '条' }],
    })))
    await page.route(/\/gudu\/ingredient/, (route) => route.fulfill(ok({ records: ingredients, total: 3 })))

    await page.goto('/library')
    const table = page.getByTestId('lib-table')
    await expect(table.getByText('清蒸鲈鱼')).toBeVisible()
    await expect(table.getByText('导入 · 下厨房')).toBeVisible()
    await expect(table.locator('img').first()).toHaveAttribute('src', '/gudu/uploads/thumbnail/luyu.jpg')
    await expect(table.getByTestId('dish-thumb-initial')).toHaveText('麻')

    const preview = page.getByTestId('lib-preview')
    await expect(preview.locator('.hero img')).toHaveAttribute('src', '/gudu/uploads/original/luyu.jpg')
    await expect(preview).toContainText('25 分钟')
    await expect(preview).toContainText('鲈鱼')
    await expect(preview).toContainText('1条')
    await expect(preview.locator('.ing-chip [data-icon="fish"]')).toBeVisible()
    await expect(preview).toContainText('大火蒸 8 分钟')
    await expect(preview.getByRole('button', { name: '下架' })).toBeDisabled()

    await page.getByTestId('lib-tabs').getByText('待审核').click()
    await expect(page.getByTestId('pending-api')).toContainText('dish_submission')
  })
})

test.describe('食材与营养', () => {
  test('统计 / 插画 / 缺数据筛选 / 编辑面板与被引用菜谱', async ({ page }) => {
    let refQuery = ''
    await page.route(/\/gudu\//, (route) => route.fulfill(ok({})))
    await page.route(/\/gudu\/ingredient\?/, (route) => route.fulfill(ok({ records: ingredients, total: 3 })))
    await page.route(/\/gudu\/ingredient\/\d+\/nutrition/, (route) => route.fulfill(ok({ 1: 105 })))
    await page.route(/\/gudu\/dict/, (route) => route.fulfill(ok({ records: categories, total: categories.length })))
    await page.route(/\/gudu\/nutrition\/metric/, (route) => route.fulfill(ok([{ id: 1, name: '热量', unit: 'kcal' }])))
    await page.route(/\/gudu\/dish\/search/, (route) => {
      refQuery = new URL(route.request().url()).search
      return route.fulfill(ok({ records: [{ id: 7, name: '清蒸鲈鱼', coverUrl: null }], total: 214 }))
    })

    await page.goto('/ingredient')
    await expect(page.getByTestId('nut-total')).toContainText('3')
    await expect(page.getByTestId('nut-with-data')).toContainText('1')
    await expect(page.getByTestId('nut-missing')).toContainText('1') // 抽纸非食用不算缺数据
    await expect(page.getByTestId('nut-pending')).toContainText('核对状态接口待上线')

    const table = page.getByTestId('nut-table')
    await expect(table.locator('[data-icon="fish"]')).toBeVisible()
    await expect(table.locator('[data-icon="broccoli"]')).toBeVisible()
    await expect(table.getByText('不适用')).toBeVisible()

    await page.getByTestId('nut-filter').getByText(/缺数据/).click()
    await expect(table.getByText('西兰花')).toBeVisible()
    await expect(table.getByText('鲈鱼')).toHaveCount(0)
    await page.getByTestId('nut-filter').getByText('全部').click()

    await table.getByText('鲈鱼').click()
    const panel = page.getByTestId('nut-panel')
    await expect(panel).toContainText('编辑：鲈鱼')
    await expect(page.getByTestId('nut-refs')).toContainText('被 214 道菜谱引用')
    await expect(page.getByTestId('nut-refs')).toContainText('清蒸鲈鱼 等')
    expect(refQuery).toContain('ingredientIds=1')
    expect(refQuery).not.toContain('ingredientIds%5B')
  })
})

test.describe('AI 用量与限额', () => {
  test('按功能汇总 + 明细算成本与 P95，限额设置为接口待上线', async ({ page }) => {
    const now = new Date()
    const iso = (minAgo: number) => new Date(now.getTime() - minAgo * 60000).toISOString().slice(0, 19)
    await page.route(/\/gudu\//, (route) => route.fulfill(ok({})))
    await page.route(/\/gudu\/ai\/provider/, (route) => route.fulfill(ok({ current: 'deepseek', providers: ['deepseek', 'glm'], ready: [{ provider: 'deepseek', ready: true }, { provider: 'glm', ready: true }] })))
    await page.route(/\/gudu\/ai\/usage/, (route) => route.fulfill(ok([
      { scene: 'menu_recommend', totalCalls: 90, failCalls: 1, tokensIn: 1000, tokensOut: 500, latencyAvgMs: 1200 },
      { scene: 'nutrition_fill', totalCalls: 10, failCalls: 0, tokensIn: 100, tokensOut: 50, latencyAvgMs: 3000 },
    ])))
    await page.route(/\/gudu\/ai\/call-log/, (route) => route.fulfill(ok({
      records: [
        { id: 1, scene: 'menu_recommend', memberId: 3, cost: 0.2, latencyMs: 1200, status: 'ok', provider: 'deepseek', tokensIn: 10, tokensOut: 5, createTime: iso(5), request: '{}', response: '{}', errorMsg: null },
        { id: 2, scene: 'nutrition_fill', memberId: 3, cost: 0.1, latencyMs: 3200, status: 'ok', provider: 'glm', tokensIn: 10, tokensOut: 5, createTime: iso(10), request: '{}', response: '{}', errorMsg: null },
      ],
      total: 2,
    })))

    await page.goto('/ai-log')
    await expect(page.getByTestId('ai-provider')).toContainText('当前模型 deepseek，可切换：glm')
    await expect(page.getByTestId('ai-kpi-calls')).toContainText('100')
    await expect(page.getByTestId('ai-kpi-calls')).toContainText('成功率 99.0%')
    await expect(page.getByTestId('ai-kpi-cost')).toContainText('¥0.30')
    await expect(page.getByTestId('ai-kpi-cost')).toContainText('基于最近 2 条明细')
    await expect(page.getByTestId('ai-kpi-p95')).toContainText('3.2 秒')
    await expect(page.getByTestId('ai-kpi-p95')).toContainText('营养补全最慢')
    await expect(page.getByTestId('ai-scenes')).toContainText('首页智荐')
    await expect(page.getByTestId('ai-scenes')).toContainText('90 次')
    await expect(page.getByTestId('pending-api')).toContainText('prototype-ui-rollout 3.8')
    await expect(page.getByTestId('ai-logs')).toContainText('#3')
  })
})

test.describe('系统健康 / 账号与家庭', () => {
  test('系统健康：AI 服务用真实失败率，其余待接入；最近失败调用列表', async ({ page }) => {
    await page.route(/\/gudu\//, (route) => route.fulfill(ok({})))
    await page.route(/\/gudu\/ai\/usage/, (route) => route.fulfill(ok([{ scene: 'menu_recommend', totalCalls: 10, failCalls: 1, tokensIn: 0, tokensOut: 0, latencyAvgMs: 0 }])))
    await page.route(/\/gudu\/ai\/call-log/, (route) => route.fulfill(ok({
      records: [{ id: 5, scene: 'menu_recommend', status: 'fail', errorMsg: 'upstream timeout', createTime: new Date().toISOString().slice(0, 19) }],
      total: 1,
    })))
    await page.goto('/ops')
    await expect(page.getByTestId('ops-ai')).toContainText('失败率 10.0%')
    await expect(page.getByTestId('ops-ai')).toContainText('偏慢')
    await expect(page.getByTestId('ops-failures')).toContainText('upstream timeout')
    await expect(page.getByText('待接入')).toHaveCount(4)
  })

  test('账号与家庭：账号/成员用 /ops/stats，账号列表走 /admin/accounts', async ({ page }) => {
    await page.route(/\/gudu\//, (route) => route.fulfill(ok({})))
    await page.route(/\/gudu\/ops\/stats/, (route) => route.fulfill(ok({
      totals: { members: 12, accounts: 8, dishes: 0, cookRecords: 0, feedbackOpen: 0 },
      last7d: { newAccounts: 2, activeMembers: 0, cookRecords: 0, aiCalls: 0, aiCostYuan: 0 },
      trend: [],
    })))
    await page.goto('/tenants')
    await expect(page.getByTestId('tn-accounts')).toContainText('8')
    await expect(page.getByTestId('tn-accounts')).toContainText('近 7 天 +2')
    await expect(page.getByTestId('tn-members')).toContainText('12')
    await expect(page.getByTestId('acc-table')).toBeVisible()
  })
})
