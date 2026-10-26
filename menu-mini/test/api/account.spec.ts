import { describe, it, expect, beforeEach, vi } from 'vitest'
import { __testUni } from '../setup'
import { installHttpMock } from '../helpers/http'
import { deactivateAccount, mergePhoneAccount, ownedFamiliesBlockingDeactivation, ensureAccountId } from '@/api/account'
import { applyLoginResult, getAccountId, getCurrentFamilyId, getFamilies } from '@/utils/family'

// 账号与安全：合并手机号账号（新 token 替换）、注销（清本地登录态）。
describe('api/account', () => {
  beforeEach(() => {
    __testUni.resetStorage()
    vi.mocked(__testUni.mocks.showToast).mockClear()
  })

  it('合并手机号账号：提交 {phone,password}，用返回的新 token 整体替换，并换成手机号账号的家庭上下文', async () => {
    applyLoginResult({
      token: 'wx-old',
      accountId: 5,
      families: [{ id: 10, name: '我的家', role: 'OWNER', memberCount: 1 }],
      currentFamilyId: 10,
    })
    const http = installHttpMock({
      'POST /account/merge-phone': {
        token: 'phone-new',
        accountId: 3,
        nickname: '老张',
        families: [
          { id: 10, name: '我的家', role: 'OWNER', memberCount: 1 },
          { id: 1, name: '咕嘟之家', role: 'OWNER', memberCount: 4 },
        ],
        currentFamilyId: 10,
        currentMemberId: 33,
      },
    })
    const r = await mergePhoneAccount(' 13800000001 ', 'pwd123')
    expect(http.calls[0].data).toEqual({ phone: '13800000001', password: 'pwd123' })
    // 旧 token 已被服务端下线：后续请求带新 token
    expect(http.calls[0].header?.Authorization).toBe('wx-old')
    expect(uni.getStorageSync('token')).toBe('phone-new')
    expect(getAccountId()).toBe(3)
    expect(getFamilies().map((f) => f.id)).toEqual([10, 1])
    expect(getCurrentFamilyId()).toBe(10)
    expect(r.token).toBe('phone-new')
  })

  it('合并失败（密码错）：本地 token 不变', async () => {
    applyLoginResult({ token: 'wx-old', accountId: 5, families: [], currentFamilyId: null })
    ;(globalThis as any).uni.request = async () => ({ statusCode: 200, data: { code: 1, msg: '手机号或密码错误', data: null } })
    await expect(mergePhoneAccount('13800000001', 'bad')).rejects.toThrow('手机号或密码错误')
    expect(uni.getStorageSync('token')).toBe('wx-old')
    expect(getAccountId()).toBe(5)
  })

  it('注销成功：清 token / 家庭上下文，标记主动退出（不再静默登录）', async () => {
    applyLoginResult({ token: 't', accountId: 5, families: [{ id: 1, name: 'a', role: 'MEMBER', memberCount: 2 }], currentFamilyId: 1 })
    uni.setStorageSync('login_via', 'wx')
    const http = installHttpMock({ 'DELETE /account/me': null })
    await deactivateAccount()
    expect(http.calls[0].method).toBe('DELETE')
    expect(uni.getStorageSync('token')).toBe('')
    expect(uni.getStorageSync('login_via')).toBe('')
    expect(getFamilies()).toEqual([])
    expect(uni.getStorageSync('logged_out')).toBe('1')
  })

  it('注销被拒（仍是有成员家庭的主理人）：本地登录态不动', async () => {
    applyLoginResult({ token: 't', accountId: 5, families: [], currentFamilyId: 1 })
    ;(globalThis as any).uni.request = async () => ({
      statusCode: 200,
      data: { code: 1, msg: '你是「咕嘟之家」的主理人，请先转让或解散', data: null },
    })
    await expect(deactivateAccount()).rejects.toThrow('主理人')
    expect(uni.getStorageSync('token')).toBe('t')
  })

  it('注销前置检查：只拦「我是主理人且还有其他成员」的家庭', () => {
    const r = ownedFamiliesBlockingDeactivation([
      { id: 1, name: '独居', role: 'OWNER', memberCount: 1 },
      { id: 2, name: '大家', role: 'OWNER', memberCount: 3 },
      { id: 3, name: '别家', role: 'ADMIN', memberCount: 5 },
    ])
    expect(r.map((f) => f.id)).toEqual([2])
  })

  it('ensureAccountId：本地没有则拉 /account/me 补上', async () => {
    const http = installHttpMock({ 'GET /account/me': { account: { id: 42 }, families: [], currentFamilyId: null } })
    expect(await ensureAccountId()).toBe(42)
    expect(await ensureAccountId()).toBe(42)
    expect(http.calls).toHaveLength(1)
  })
})
