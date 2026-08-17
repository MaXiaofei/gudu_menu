import { describe, it, expect } from 'vitest'
import { installHttpMock } from '../helpers/http'
import { semanticSearch } from '@/api/dish'
import { recommendMenu } from '@/api/ai'

// 推荐（对齐 APP AiRecommendPage 依赖）：
// 语义找菜 /dish/semantic-search（TopK 默认 8）、组合推荐 /ai/menu/recommend（偏好非空才传）。
describe('api/dish semanticSearch', () => {
  it('POST /dish/semantic-search：默认 topK=8，可指定', async () => {
    const http = installHttpMock({
      'POST /dish/semantic-search': [{ dishId: 1, name: '番茄炒蛋', cookTime: 10 }],
    })
    const hits = await semanticSearch('清淡下饭')
    expect(http.calls[0].method).toBe('POST')
    expect(http.calls[0].url).toBe('/dish/semantic-search')
    expect(http.calls[0].data).toEqual({ query: '清淡下饭', topK: 8 })
    expect(hits[0].dishId).toBe(1)

    await semanticSearch('酸甜口', 5)
    expect(http.calls[1].data).toEqual({ query: '酸甜口', topK: 5 })
  })
})

describe('api/ai recommendMenu', () => {
  it('POST /ai/menu/recommend：memberId 必传，偏好非空才传并去首尾空格', async () => {
    const http = installHttpMock({ 'POST /ai/menu/recommend': [] })
    await recommendMenu(3)
    expect(http.calls[0].url).toBe('/ai/menu/recommend')
    expect(http.calls[0].data).toEqual({ memberId: 3 })

    await recommendMenu(3, ' 清淡下饭 ')
    expect(http.calls[1].data).toEqual({ memberId: 3, preference: '清淡下饭' })

    await recommendMenu(3, '   ')
    expect(http.calls[2].data).toEqual({ memberId: 3 })
  })

  it('返回组合结构透传（dishes + reasons）', async () => {
    const http = installHttpMock({
      'POST /ai/menu/recommend': [
        {
          dishes: [{ dishId: 1, name: '番茄炒蛋' }],
          reasons: ['与你近期常做的「番茄」口味相近', '快手，10 分钟搞定'],
          source: 'vector',
        },
      ],
    })
    const groups = await recommendMenu(3, '快手菜')
    expect(groups[0].dishes[0].name).toBe('番茄炒蛋')
    expect(groups[0].reasons).toHaveLength(2)
    expect(http.calls[0].data.preference).toBe('快手菜')
  })
})
