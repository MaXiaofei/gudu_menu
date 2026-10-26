import { describe, it, expect } from 'vitest'
import { installHttpMock } from '../helpers/http'
import { joinInvite, revokeInvite, togetherSharePath, getTogether } from '@/api/together'

// api/together：聚餐须凭邀请凭证加入（登录态）；分享路径带 menuId + token。
describe('api/together', () => {
  it('togetherSharePath：/pages/together/Pick?menuId=&token=', () => {
    expect(togetherSharePath(7, 'abc123')).toBe('/pages/together/Pick?menuId=7&token=abc123')
  })

  it('joinInvite：POST /invite/{token}/join，带昵称，不带 guest key', async () => {
    const http = installHttpMock({ 'POST /invite/tok1/join': { menuId: 7, menuName: '今晚的饭' } })
    const r = await joinInvite('tok1', '小王')
    expect(r.menuId).toBe(7)
    expect(http.calls[0].data).toEqual({ nickname: '小王' })
    expect(http.calls[0].header?.['X-Guest-Key']).toBeUndefined()
  })

  it('getTogether：加入后凭登录态读取，不带 X-Guest-Key', async () => {
    const http = installHttpMock({ 'GET /menu/7/together': { members: [], dishes: [], activities: [] } })
    await getTogether(7)
    expect(http.calls[0].header?.['X-Guest-Key']).toBeUndefined()
  })

  it('revokeInvite：DELETE /menu/{id}/invite', async () => {
    const http = installHttpMock({ 'DELETE /menu/7/invite': null })
    await revokeInvite(7)
    expect(http.calls[0].method).toBe('DELETE')
  })
})
