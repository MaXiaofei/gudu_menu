import { describe, it, expect } from 'vitest'
import { installHttpMock } from '../helpers/http'
import {
  createIngredient, deleteIngredient, listIngredients, resolveIngredient, searchIngredients, updateIngredient,
} from '@/api/ingredient'

// api/ingredient：新建不再带营养（走 /ingredient/resolve 去重）、只看我家、本家编辑。
describe('api/ingredient', () => {
  it('新建食材：POST /ingredient/resolve，只发名称 / 分类 / 食用属性，不带营养', async () => {
    const http = installHttpMock({
      'POST /ingredient/resolve': { id: 3, name: '马铃薯', existed: 'public', familyOwned: false },
    })
    const r = await resolveIngredient('土豆', 5, 1)
    expect(r.existed).toBe('public')
    expect(http.calls[0].data).toEqual({ name: '土豆', purchaseCategoryId: 5, edible: 1 })
    expect(JSON.stringify(http.calls[0].data)).not.toContain('nutrition')

    await createIngredient('番茄')
    expect(http.calls[1].url).toBe('/ingredient/resolve')
    expect(http.calls[1].data.name).toBe('番茄')
  })

  it('列表：familyOnly=只看我家；不传时不带该字段', async () => {
    const http = installHttpMock({ 'GET /ingredient': { records: [], total: 0 } })
    await listIngredients({ familyOnly: true, pageNum: 1 })
    expect(http.calls[0].data).toMatchObject({ familyOnly: true, pageNum: 1, pageSize: 10 })
    await listIngredients({ pageNum: 1 })
    expect(http.calls[1].data.familyOnly).toBeUndefined()
  })

  it('服务端搜索（命中别名）', async () => {
    const http = installHttpMock({
      'GET /ingredient': { records: [{ id: 9, name: '马铃薯', matchedAlias: '土豆' }], total: 1 },
    })
    const r = await searchIngredients('土豆')
    expect(r[0].matchedAlias).toBe('土豆')
    expect(http.calls[0].data).toEqual({ keyword: '土豆', pageNum: 1, pageSize: 20 })
  })

  it('本家食材 改 / 删', async () => {
    const http = installHttpMock({ 'PUT /ingredient': null, 'DELETE /ingredient/8': null })
    await updateIngredient(8, { name: '家传辣酱', edible: 2 })
    expect(http.calls[0].data).toEqual({ id: 8, name: '家传辣酱', edible: 2 })
    await deleteIngredient(8)
    expect(http.calls[1].method).toBe('DELETE')
  })
})
