import { test, expect } from '@playwright/test'

// 意见反馈管理 E2E：mock GET /feedback 与 PUT /feedback/{id}/status。
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

const rows = [
  {
    id: 11, memberId: 3, memberName: '小王', content: '采购清单希望能导出图片', contact: 'wx:wang',
    images: ['/gudu/uploads/fb-1.png', '/gudu/uploads/fb-2.png'], clientInfo: 'mp-weixin 1.2.0 iPhone',
    status: 'NEW', createTime: '2026-10-15T10:20:30',
  },
  {
    id: 12, memberId: 4, memberName: null, content: '菜谱搜索很好用', contact: null,
    images: [], clientInfo: null, status: 'NEW', createTime: '2026-10-14T08:00:00',
  },
]

test.describe('意见反馈管理', () => {
  test('默认筛选待处理 → 渲染表格 / 缩略图预览 / 标记已处理', async ({ page }) => {
    const listQueries: string[] = []
    const puts: { url: string; body: any }[] = []
    let list = rows.map((r) => ({ ...r }))

    await page.route(/\/gudu\//, (route) => route.fulfill(ok({})))
    await page.route(/\/gudu\/uploads\//, (route) => route.fulfill({ status: 200, contentType: 'image/png', body: PNG }))
    await page.route(/\/gudu\/feedback/, async (route) => {
      const req = route.request()
      const url = new URL(req.url())
      if (req.method() === 'PUT') {
        puts.push({ url: url.pathname, body: req.postDataJSON() })
        const id = Number(url.pathname.split('/').slice(-2)[0])
        list = list.map((r) => (r.id === id ? { ...r, status: req.postDataJSON().status } : r))
        return route.fulfill(ok(null))
      }
      listQueries.push(url.search)
      const status = url.searchParams.get('status')
      const records = status ? list.filter((r) => r.status === status) : list
      return route.fulfill(ok({ records, total: records.length }))
    })

    await page.goto('/feedback')
    await expect(page.getByText('采购清单希望能导出图片')).toBeVisible()
    await expect(page.getByText('小王')).toBeVisible()
    await expect(page.getByText('#4')).toBeVisible() // 无昵称回退显示成员 ID
    await expect(page.getByText('wx:wang')).toBeVisible()
    await expect(page.getByText('mp-weixin 1.2.0 iPhone')).toBeVisible()
    await expect(page.getByText('2026-10-15 10:20')).toBeVisible()
    expect(listQueries[0]).toContain('status=NEW')
    expect(listQueries[0]).toContain('pageNum=1')

    // 缩略图 → 大图预览
    const thumbs = page.locator('.thumb img')
    await expect(thumbs).toHaveCount(2)
    await thumbs.first().click()
    await expect(page.locator('.el-image-viewer__wrapper')).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(page.locator('.el-image-viewer__wrapper')).toHaveCount(0)

    // 标记已处理 → PUT 后从待处理列表移除
    const row = page.locator('.el-table__row', { hasText: '采购清单希望能导出图片' })
    await row.getByRole('button', { name: '标记已处理' }).click()
    await expect(page.getByText('已标记为已处理')).toBeVisible()
    expect(puts[0]).toEqual({ url: '/gudu/feedback/11/status', body: { status: 'DONE' } })
    await expect(page.getByText('采购清单希望能导出图片')).toHaveCount(0)

    // 切到「已处理」→ 可重新打开
    await page.getByTestId('fb-status-filter').getByText('已处理').click()
    await expect(page.getByText('采购清单希望能导出图片')).toBeVisible()
    expect(listQueries.at(-1)).toContain('status=DONE')
    await page.locator('.el-table__row', { hasText: '采购清单' }).getByRole('button', { name: '重新打开' }).click()
    expect(puts[1]).toEqual({ url: '/gudu/feedback/11/status', body: { status: 'NEW' } })

    // 切到「全部」→ 不带 status
    await page.getByTestId('fb-status-filter').getByText('全部').click()
    await expect(page.getByText('菜谱搜索很好用')).toBeVisible()
    expect(listQueries.at(-1)).not.toContain('status=')
  })

  test('无数据 → 空态文案', async ({ page }) => {
    await page.route(/\/gudu\//, (route) => route.fulfill(ok({})))
    await page.route(/\/gudu\/feedback/, (route) => route.fulfill(ok({ records: [], total: 0 })))
    await page.goto('/feedback')
    await expect(page.getByText('没有待处理的反馈')).toBeVisible()
  })

  test('分页：点击第 2 页 → 请求 pageNum=2', async ({ page }) => {
    const queries: string[] = []
    await page.route(/\/gudu\//, (route) => route.fulfill(ok({})))
    await page.route(/\/gudu\/feedback/, (route) => {
      const url = new URL(route.request().url())
      queries.push(url.search)
      const p = Number(url.searchParams.get('pageNum'))
      return route.fulfill(ok({
        records: [{ ...rows[1], id: 100 + p, content: `第${p}页的反馈` }],
        total: 20,
      }))
    })
    await page.goto('/feedback')
    await expect(page.getByText('第1页的反馈')).toBeVisible()
    await page.locator('.el-pager li', { hasText: '2' }).click()
    await expect(page.getByText('第2页的反馈')).toBeVisible()
    expect(queries.at(-1)).toContain('pageNum=2')
    expect(queries.at(-1)).toContain('pageSize=15')
  })
})
