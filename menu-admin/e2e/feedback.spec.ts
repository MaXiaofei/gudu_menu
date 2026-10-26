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
        const body = req.postDataJSON()
        puts.push({ url: url.pathname, body })
        const id = Number(url.pathname.split('/').slice(-2)[0])
        if (url.pathname.endsWith('/reply')) {
          list = list.map((r) => (r.id === id
            ? { ...r, reply: body.reply, status: body.markDone ? 'DONE' : r.status === 'NEW' ? 'REPLIED' : r.status }
            : r))
        } else {
          list = list.map((r) => (r.id === id ? { ...r, status: body.status } : r))
        }
        return route.fulfill(ok(null))
      }
      listQueries.push(url.search)
      const status = url.searchParams.get('status')
      const type = url.searchParams.get('type')
      const records = list.filter((r) => (!status || r.status === status) && (!type || (r.type || 'general') === type))
      return route.fulfill(ok({ records, total: records.length }))
    })

    await page.goto('/feedback')
    const table = page.getByTestId('fb-table')
    const detail = page.getByTestId('fb-detail')
    await expect(table.getByText('采购清单希望能导出图片')).toBeVisible()
    await expect(table.getByText('小王 · 附 2 张截图')).toBeVisible()
    await expect(table.getByText('#4')).toBeVisible() // 无昵称回退显示成员 ID
    await expect(table.getByText('小程序')).toBeVisible() // 由 clientInfo 推断来源端
    expect(listQueries[0]).toContain('status=NEW')
    expect(listQueries[0]).toContain('pageNum=1')

    // 默认选中第一条，右侧详情展示联系方式 / 设备 / 时间
    await expect(detail).toContainText('#FB-11')
    await expect(detail).toContainText('2026-10-15 10:20')
    await expect(detail).toContainText('wx:wang')
    await expect(detail).toContainText('mp-weixin 1.2.0 iPhone')
    // 回复框可用（A.11），类型标签显示一般反馈
    await expect(detail.getByRole('button', { name: '回复用户', exact: true })).toBeEnabled()
    await expect(detail).toContainText('一般反馈')

    // 截图 → 大图预览
    const thumbs = detail.locator('.thumb img')
    await expect(thumbs).toHaveCount(2)
    await thumbs.first().click()
    await expect(page.locator('.el-image-viewer__wrapper')).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(page.locator('.el-image-viewer__wrapper')).toHaveCount(0)

    // 点第二条 → 详情切换
    await table.getByText('菜谱搜索很好用').click()
    await expect(detail).toContainText('#FB-12')
    await expect(detail).toContainText('未留')
    await table.getByText('采购清单希望能导出图片').click()

    // 标记已处理 → PUT 后从待处理列表移除
    await detail.getByRole('button', { name: '标记已处理', exact: true }).click()
    await expect(page.getByText('已标记为已处理')).toBeVisible()
    expect(puts[0]).toEqual({ url: '/gudu/feedback/11/status', body: { status: 'DONE' } })
    await expect(table.getByText('采购清单希望能导出图片')).toHaveCount(0)

    // 切到「已处理」→ 可重新打开
    await page.getByTestId('fb-status-filter').getByText('已处理').click()
    await expect(table.getByText('采购清单希望能导出图片')).toBeVisible()
    expect(listQueries.at(-1)).toContain('status=DONE')
    await detail.getByRole('button', { name: '重新打开' }).click()
    expect(puts[1]).toEqual({ url: '/gudu/feedback/11/status', body: { status: 'NEW' } })

    // 切到「全部」→ 不带 status
    await page.getByTestId('fb-status-filter').getByText('全部').click()
    await expect(table.getByText('菜谱搜索很好用')).toBeVisible()
    expect(listQueries.at(-1)).not.toContain('status=')
  })

  test('A.11 类型筛选 / 来源 / 回复用户 → 已回复', async ({ page }) => {
    const listQueries: string[] = []
    const puts: { url: string; body: any }[] = []
    let list: any[] = [
      { ...rows[0], id: 21, type: 'ai', sourceRef: 'agent:m88', content: 'AI 说番茄要炖两小时' },
      { ...rows[1], id: 22, type: 'complaint', content: '有人在菜谱里打广告' },
    ]
    await page.route(/\/gudu\//, (route) => route.fulfill(ok({})))
    await page.route(/\/gudu\/uploads\//, (route) => route.fulfill({ status: 200, contentType: 'image/png', body: PNG }))
    await page.route(/\/gudu\/feedback/, async (route) => {
      const req = route.request()
      const url = new URL(req.url())
      if (req.method() === 'PUT') {
        const body = req.postDataJSON()
        puts.push({ url: url.pathname, body })
        list = list.map((r) => (r.id === 21 ? { ...r, reply: body.reply, status: 'REPLIED' } : r))
        return route.fulfill(ok(null))
      }
      listQueries.push(url.search)
      const status = url.searchParams.get('status')
      const type = url.searchParams.get('type')
      const records = list.filter((r) => (!status || r.status === status) && (!type || r.type === type))
      return route.fulfill(ok({ records, total: records.length }))
    })

    await page.goto('/feedback')
    const table = page.getByTestId('fb-table')
    const detail = page.getByTestId('fb-detail')
    await expect(table.getByText('AI 说番茄要炖两小时')).toBeVisible()

    // 类型筛选：AI 内容 → 带 type=ai，只剩 AI 反馈
    await page.getByTestId('fb-type-filter').getByText('AI 内容').click()
    await expect(table.getByText('有人在菜谱里打广告')).toHaveCount(0)
    expect(listQueries.at(-1)).toContain('type=ai')
    await expect(detail.getByTestId('fb-source')).toHaveText('咕嘟助手 · m88')

    // 空回复 → 前端拦截不请求
    await detail.getByRole('button', { name: '回复用户', exact: true }).click()
    await expect(detail.getByTestId('fb-reply-error')).toHaveText('请填写回复内容')
    expect(puts).toHaveLength(0)

    // 回复 → PUT /reply，待处理列表里移除
    await detail.locator('.reply-box textarea').fill('已修正菜谱步骤，谢谢')
    await detail.getByRole('button', { name: '回复用户', exact: true }).click()
    await expect(page.getByText('已回复，用户会在「消息与提醒」收到通知')).toBeVisible()
    expect(puts[0]).toEqual({ url: '/gudu/feedback/21/reply', body: { reply: '已修正菜谱步骤，谢谢', markDone: false } })
    await expect(table.getByText('AI 说番茄要炖两小时')).toHaveCount(0)

    // 已回复 Tab → 详情展示回复
    await page.getByTestId('fb-status-filter').getByText('已回复').click()
    expect(listQueries.at(-1)).toContain('status=REPLIED')
    await expect(detail.getByTestId('fb-replied')).toContainText('已修正菜谱步骤，谢谢')
    await expect(detail.getByTestId('fb-detail-status')).toHaveText('已回复')
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
    await expect(page.getByTestId('fb-table').getByText('第1页的反馈')).toBeVisible()
    await page.locator('.el-pager li', { hasText: '2' }).click()
    await expect(page.getByTestId('fb-table').getByText('第2页的反馈')).toBeVisible()
    expect(queries.at(-1)).toContain('pageNum=2')
    expect(queries.at(-1)).toContain('pageSize=15')
  })
})
