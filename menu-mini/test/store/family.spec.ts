import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { __testUni } from '../setup'
import { installHttpMock } from '../helpers/http'
import { useFamilyStore } from '@/store/family'
import { getCurrentFamilyId, saveFamilyContext } from '@/utils/family'

const FAMS = [
  { id: 1, name: '我的家', role: 'OWNER', memberCount: 1 },
  { id: 2, name: '爸妈家', role: 'MEMBER', memberCount: 3 },
]

// 家庭 store：切换 / 加入 / 新建都经服务端会话，成功后本地上下文跟上。
describe('store/family', () => {
  beforeEach(() => {
    __testUni.resetStorage()
    setActivePinia(createPinia())
    ;(globalThis as any).uni.$emit = () => {}
  })

  it('初始化从 storage 恢复；current / currentRole 跟随当前家庭', () => {
    saveFamilyContext({ families: FAMS, currentFamilyId: 2 })
    const s = useFamilyStore()
    expect(s.current?.name).toBe('爸妈家')
    expect(s.currentRole).toBe('MEMBER')
  })

  it('switchTo：POST /family/current {familyId}，再以 /family/mine 为准刷新', async () => {
    saveFamilyContext({ families: FAMS, currentFamilyId: 1 })
    const http = installHttpMock({
      'POST /family/current': { familyId: 2, profileId: 20, role: 'MEMBER' },
      'GET /family/mine': { families: FAMS, currentFamilyId: 2 },
    })
    const s = useFamilyStore()
    await s.switchTo(2)
    expect(http.calls[0].data).toEqual({ familyId: 2 })
    expect(http.callsOf('/family/mine')).toHaveLength(1)
    expect(s.currentId).toBe(2)
    expect(getCurrentFamilyId()).toBe(2)
    expect(s.currentRole).toBe('MEMBER')
  })

  it('join：凭邀请码加入后当前家庭切到新家庭', async () => {
    installHttpMock({
      'POST /family/join': { familyId: 3, profileId: 30, role: 'MEMBER' },
      'GET /family/mine': { families: [...FAMS, { id: 3, name: '小姨家', role: 'MEMBER', memberCount: 2 }], currentFamilyId: 3 },
    })
    const s = useFamilyStore()
    await s.join('INV123')
    expect(s.current?.name).toBe('小姨家')
  })

  it('切换失败（非成员 404）：当前家庭不变', async () => {
    saveFamilyContext({ families: FAMS, currentFamilyId: 1 })
    ;(globalThis as any).uni.request = async () => ({ statusCode: 200, data: { code: 404, msg: '家庭不存在', data: null } })
    const s = useFamilyStore()
    await expect(s.switchTo(99)).rejects.toThrow('家庭不存在')
    expect(s.currentId).toBe(1)
    expect(getCurrentFamilyId()).toBe(1)
  })
})
