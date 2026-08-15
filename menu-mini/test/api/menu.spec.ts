import { describe, it, expect } from 'vitest'
import { installHttpMock } from '../helpers/http'
import { listMenus, createMenu, addDishToMenu } from '@/api/menu'

// api/menu：新建食集 payload + 加菜自动拼接名规则（对齐 Flutter MenuService.addDishToMenu）。
describe('api/menu', () => {
  it('createMenu：POST /menu 带 menu + dishes（servingCount=1 / ACTIVE）', async () => {
    const http = installHttpMock({ 'POST /menu': 7 })
    const id = await createMenu('今晚的饭', [10, 11])
    expect(id).toBe(7)
    expect(http.calls[0].data).toEqual({
      menu: { name: '今晚的饭', servingCount: 1, status: 'ACTIVE' },
      dishes: [
        { dishId: 10, servingFactor: 1 },
        { dishId: 11, servingFactor: 1 },
      ],
    })
  })

  it('addDishToMenu：已在食集 → 不发 PUT', async () => {
    const http = installHttpMock({
      'GET /menu/1': { menu: { id: 1, name: 'x', servingCount: 2 }, dishes: [{ dishId: 10, servingFactor: 1 }] },
    })
    await addDishToMenu(1, 10, '番茄炒蛋')
    expect(http.calls).toHaveLength(1) // 只有详情，无更新
  })

  it('addDishToMenu：原名为菜名拼接 → 更新为含新菜的拼接名（保留份数）', async () => {
    const http = installHttpMock({
      'GET /menu/1': {
        menu: { id: 1, name: '番茄炒蛋_蛋花汤', servingCount: 3 },
        dishes: [
          { dishId: 10, dishName: '番茄炒蛋', servingFactor: 1 },
          { dishId: 11, dishName: '蛋花汤', servingFactor: 2 },
        ],
      },
      'PUT /menu': null,
    })
    await addDishToMenu(1, 12, '凉拌黄瓜')
    const put = http.callsOf('/menu')[0]
    expect(put.method).toBe('PUT')
    expect(put.data.menu.name).toBe('番茄炒蛋_蛋花汤_凉拌黄瓜')
    expect(put.data.menu.servingCount).toBe(3) // 保留原份数
    expect(put.data.dishes).toHaveLength(3)
    expect(put.data.dishes[2]).toEqual({ dishId: 12, servingFactor: 1 })
  })

  it('addDishToMenu：用户自定义名 → 不改名；servingFactor 空 → 1', async () => {
    const http = installHttpMock({
      'GET /menu/2': {
        menu: { id: 2, name: '周末大餐' },
        dishes: [{ dishId: 10, dishName: '番茄炒蛋' }],
      },
      'PUT /menu': null,
    })
    await addDishToMenu(2, 12, '凉拌黄瓜')
    const put = http.callsOf('/menu')[0]
    expect(put.data.menu.name).toBe('周末大餐') // 自定义名保留
    expect(put.data.dishes[0].servingFactor).toBe(1) // 空值兜底
    expect(put.data.menu.status).toBe('ACTIVE')
  })

  it('listMenus：status 过滤 + 分页', async () => {
    const http = installHttpMock({ 'GET /menu': { records: [], total: 0 } })
    await listMenus(2, 20, 'ACTIVE')
    expect(http.calls[0].data).toEqual({ pageNum: 2, pageSize: 20, status: 'ACTIVE' })
  })
})
