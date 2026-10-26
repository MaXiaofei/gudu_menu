import { describe, it, expect, vi, beforeEach } from 'vitest'
import { __testUni } from '../setup'
import { installHttpMock } from '../helpers/http'
import {
  applyLoginResult,
  canManage,
  clearLocalSession,
  currentFamily,
  FAMILY_SWITCH_PAGE,
  getAccountId,
  getCurrentFamilyId,
  getFamilies,
  handleFamilyDenied,
  invitePath,
  isFamilyDenied,
  PENDING_INVITE_KEY,
  profileIsBlank,
  roleLabel,
  saveFamilyContext,
} from '@/utils/family'
import { request } from '@/utils/request'

const FAMS = [
  { id: 1, name: '我的家', role: 'OWNER', memberCount: 1 },
  { id: 2, name: '爸妈家', role: 'MEMBER', memberCount: 3 },
]

// 家庭上下文（multi-tenant-family F.1）：登录后保存 families / currentFamilyId，403 回家庭选择。
describe('utils/family 家庭上下文', () => {
  beforeEach(() => {
    __testUni.resetStorage()
    vi.mocked(__testUni.mocks.reLaunch).mockClear()
    vi.mocked(__testUni.mocks.showToast).mockClear()
  })

  it('登录结果：写 token、账号 id、家庭列表与当前家庭，清主动退出标记', () => {
    __testUni.setStorage('logged_out', '1')
    applyLoginResult({ token: 't1', accountId: 9, nickname: '老张', families: FAMS, currentFamilyId: 2, currentMemberId: 77 })
    expect(__testUni.getStorage('token')).toBe('t1')
    expect(getAccountId()).toBe(9)
    expect(getFamilies()).toEqual(FAMS)
    expect(getCurrentFamilyId()).toBe(2)
    expect(currentFamily()?.name).toBe('爸妈家')
    expect(__testUni.getStorage('logged_out')).toBeUndefined()
  })

  it('saveFamilyContext 只给其一时保留另一项；currentFamilyId=null 清当前家庭', () => {
    saveFamilyContext({ families: FAMS, currentFamilyId: 1 })
    saveFamilyContext({ currentFamilyId: 2 })
    expect(getFamilies()).toHaveLength(2)
    expect(getCurrentFamilyId()).toBe(2)
    saveFamilyContext({ currentFamilyId: null })
    expect(getCurrentFamilyId()).toBeNull()
  })

  it('clearLocalSession：清 token / 登录方式 / 家庭上下文 / 待加入邀请，不清协议同意', () => {
    applyLoginResult({ token: 't', accountId: 1, families: FAMS, currentFamilyId: 1 })
    __testUni.setStorage('login_via', 'wx')
    __testUni.setStorage(PENDING_INVITE_KEY, 'ABC')
    __testUni.setStorage('legal_consent', 'v')
    clearLocalSession()
    expect(__testUni.getStorage('token')).toBeUndefined()
    expect(__testUni.getStorage('login_via')).toBeUndefined()
    expect(__testUni.getStorage(PENDING_INVITE_KEY)).toBeUndefined()
    expect(getFamilies()).toEqual([])
    expect(getCurrentFamilyId()).toBeNull()
    expect(getAccountId()).toBeNull()
    expect(__testUni.getStorage('legal_consent')).toBe('v')
  })

  it('isFamilyDenied：仅 code=403 且 reason=FAMILY_ACCESS_DENIED', () => {
    expect(isFamilyDenied({ code: 403, data: { reason: 'FAMILY_ACCESS_DENIED' } })).toBe(true)
    expect(isFamilyDenied({ code: 403, msg: '无权限', data: null })).toBe(false)
    expect(isFamilyDenied({ code: 0, data: { reason: 'FAMILY_ACCESS_DENIED' } })).toBe(false)
    expect(isFamilyDenied(null)).toBe(false)
  })

  it('handleFamilyDenied：清当前家庭 + 提示 + reLaunch 家庭选择页；已在选择页不重复跳；并发只跳一次', () => {
    vi.useFakeTimers()
    saveFamilyContext({ families: FAMS, currentFamilyId: 2 })
    ;(globalThis as any).getCurrentPages = () => [{ route: 'pages/dish/List' }]
    handleFamilyDenied('你已不在该家庭中')
    expect(getCurrentFamilyId()).toBeNull()
    expect(getFamilies()).toHaveLength(2) // 家庭列表保留，供选择
    expect(__testUni.mocks.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '你已不在该家庭中' }))
    expect(__testUni.mocks.reLaunch).toHaveBeenCalledWith({ url: `${FAMILY_SWITCH_PAGE}?reason=denied` })
    handleFamilyDenied() // 并发的第二个 403
    expect(__testUni.mocks.reLaunch).toHaveBeenCalledTimes(1)
    vi.runAllTimers()

    vi.mocked(__testUni.mocks.reLaunch).mockClear()
    ;(globalThis as any).getCurrentPages = () => [{ route: 'pkg-family/Switch' }]
    handleFamilyDenied()
    expect(__testUni.mocks.reLaunch).not.toHaveBeenCalled()
    delete (globalThis as any).getCurrentPages
    vi.useRealTimers()
  })

  it('角色文案与管理权限', () => {
    expect(roleLabel('OWNER')).toBe('主理人')
    expect(roleLabel('ADMIN')).toBe('管理员')
    expect(roleLabel(undefined)).toBe('成员')
    expect(canManage('OWNER')).toBe(true)
    expect(canManage('ADMIN')).toBe(true)
    expect(canManage('MEMBER')).toBe(false)
  })

  it('邀请分享路径带 code（编码）', () => {
    expect(invitePath('AB 12')).toBe('/pkg-family/Invite?code=AB%2012')
  })

  it('profileIsBlank：没填任何身体 / 健康字段才算空', () => {
    expect(profileIsBlank(null)).toBe(true)
    expect(profileIsBlank({ audiences: [], allergies: [] })).toBe(true)
    expect(profileIsBlank({ age: 3 })).toBe(false)
    expect(profileIsBlank({ audiences: ['宝宝辅食'] })).toBe(false)
  })
})

// request.ts 的 403 分流：FAMILY_ACCESS_DENIED 回家庭选择；普通 403 只提示不跳转。
describe('utils/request 403 FAMILY_ACCESS_DENIED', () => {
  beforeEach(() => {
    __testUni.resetStorage()
    vi.mocked(__testUni.mocks.reLaunch).mockClear()
    vi.mocked(__testUni.mocks.showToast).mockClear()
    ;(globalThis as any).getCurrentPages = () => [{ route: 'pages/menu/List' }]
  })

  it('家庭访问被拒 → 清当前家庭、保留 token、reLaunch 家庭选择页、抛服务端原因', async () => {
    __testUni.setStorage('token', 'tok')
    saveFamilyContext({ families: FAMS, currentFamilyId: 2 })
    ;(globalThis as any).uni.request = async () => ({
      statusCode: 200,
      data: { code: 403, msg: '你已不在该家庭中，请重新选择家庭', data: { reason: 'FAMILY_ACCESS_DENIED' } },
    })
    await expect(request({ url: '/menu', method: 'GET' })).rejects.toThrow('你已不在该家庭中，请重新选择家庭')
    expect(getCurrentFamilyId()).toBeNull()
    expect(__testUni.getStorage('token')).toBe('tok')
    expect(__testUni.mocks.reLaunch).toHaveBeenCalledWith({ url: `${FAMILY_SWITCH_PAGE}?reason=denied` })
  })

  it('普通 403（角色不足）→ 只提示，不清家庭、不跳转', async () => {
    saveFamilyContext({ families: FAMS, currentFamilyId: 2 })
    ;(globalThis as any).uni.request = async () => ({
      statusCode: 200,
      data: { code: 403, msg: '只有主理人可以解散家庭', data: null },
    })
    await expect(request({ url: '/family/2', method: 'DELETE' })).rejects.toThrow('只有主理人可以解散家庭')
    expect(getCurrentFamilyId()).toBe(2)
    expect(__testUni.mocks.reLaunch).not.toHaveBeenCalled()
  })

  it('请求不额外带家庭头：当前家庭在服务端会话（token 维度）', async () => {
    __testUni.setStorage('token', 'tok')
    saveFamilyContext({ currentFamilyId: 2 })
    const http = installHttpMock({ 'GET /menu': [] })
    await request({ url: '/menu', method: 'GET' })
    expect(http.calls[0].header).toEqual({ Authorization: 'tok' })
  })
})
