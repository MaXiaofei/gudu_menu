import { describe, it, expect, vi, beforeEach } from 'vitest'

// 拦截器逻辑是 request.ts 的核心。用 vi.mock('axios') 接管 create，
// 捕获注册到 instance 上的 request/response 拦截器回调，再单独驱动它们，
// 从而精确验证每个分支（code==0 返 data / 401 清 token+跳登录 / 其它 code 报错 / 网络错误）。

// element-plus 的 ElMessage 有副作用，mock 掉避免 jsdom 报错。
vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn(), success: vi.fn(), warning: vi.fn() },
}))

// jsdom 不支持 location.href 真实导航（401 跳转会抛 Not implemented）。
// 用一个可记录导航、可改 pathname 的假 location 替换，验证跳转目标而不触发导航。
const hrefSetter = vi.fn()
let currentPathname = '/'
vi.stubGlobal('location', {
  get pathname() {
    return currentPathname
  },
  set href(v: string) {
    hrefSetter(v)
  },
  get href() {
    return currentPathname
  },
})

// 接管 axios：记录 interceptors.use 注册的回调。
const requestInterceptors: ((c: any) => any)[] = []
const responseSuccess: ((r: any) => any)[] = []
const responseError: ((e: any) => any)[] = []
const fakeInstance = {
  request: vi.fn(),
  interceptors: {
    request: { use: (f: any) => requestInterceptors.push(f) },
    response: {
      use: (s: any, e: any) => {
        responseSuccess.push(s)
        responseError.push(e)
      },
    },
  },
}
vi.mock('axios', () => ({
  default: { create: () => fakeInstance },
}))

// 必须在 mock 声明之后 import，让模块加载时拿到 mock 的 axios。
const { default: instance, request } = await import('./request')

describe('request 拦截器', () => {
  beforeEach(() => {
    localStorage.clear()
    currentPathname = '/'
    hrefSetter.mockClear()
  })

  it('加载时注册了 1 个 request 拦截器 + 1 个 response 拦截器', () => {
    expect(requestInterceptors).toHaveLength(1)
    expect(responseSuccess).toHaveLength(1)
    expect(responseError).toHaveLength(1)
  })

  describe('请求拦截器：塞 Authorization', () => {
    it('localStorage 无 token 时不设 Authorization', () => {
      const config: any = { headers: undefined }
      const out = requestInterceptors[0](config)
      expect(out.headers).toBeFalsy()
    })

    it('localStorage 有 token 时塞 Authorization（裸 token，无 Bearer）', () => {
      localStorage.setItem('gudu-token', 'abc123')
      const config: any = { headers: undefined }
      const out = requestInterceptors[0](config)
      expect(out.headers.Authorization).toBe('abc123')
    })

    it('已有 headers 对象时也能正确塞入', () => {
      localStorage.setItem('gudu-token', 't1')
      const config: any = { headers: { 'Content-Type': 'application/json' } }
      const out = requestInterceptors[0](config)
      expect(out.headers.Authorization).toBe('t1')
      expect(out.headers['Content-Type']).toBe('application/json')
    })
  })

  describe('响应拦截器 - 成功分支', () => {
    const onSuccess = () => responseSuccess[0]

    it('code==0 → 返回 data（拆包 R）', async () => {
      const r = await onSuccess()({
        data: { code: 0, msg: 'ok', data: { id: 1 } },
      })
      expect(r).toEqual({ id: 1 })
    })

    it('code==401 且不在登录页 → 清 token + 跳转 /login + reject', async () => {
      currentPathname = '/'
      hrefSetter.mockClear()
      localStorage.setItem('gudu-token', 'abc')
      await expect(
        onSuccess()({ data: { code: 401, msg: '未登录' } }),
      ).rejects.toThrow('未登录')
      expect(localStorage.getItem('gudu-token')).toBeNull()
      expect(hrefSetter).toHaveBeenCalledWith('/login')
    })

    it('code==401 且已在 /login → 清 token 但不跳转（避免循环）', async () => {
      currentPathname = '/login'
      hrefSetter.mockClear()
      localStorage.setItem('gudu-token', 'abc')
      await expect(
        onSuccess()({ data: { code: 401, msg: '未登录' } }),
      ).rejects.toThrow('未登录')
      expect(localStorage.getItem('gudu-token')).toBeNull()
      expect(hrefSetter).not.toHaveBeenCalled()
    })

    it('code==401 → 同时清 nickname / isAdmin', async () => {
      currentPathname = '/'
      localStorage.setItem('gudu-token', 'abc')
      localStorage.setItem('gudu-nickname', 'n')
      localStorage.setItem('gudu-is-admin', 'true')
      await expect(onSuccess()({ data: { code: 401, msg: '未登录' } })).rejects.toThrow('未登录')
      expect(localStorage.getItem('gudu-nickname')).toBeNull()
      expect(localStorage.getItem('gudu-is-admin')).toBeNull()
    })

    it('code==403 → ElMessage 服务端 msg + reject，不清登录态不跳转', async () => {
      const { ElMessage } = await import('element-plus')
      const spy = vi.mocked(ElMessage.error)
      spy.mockClear()
      hrefSetter.mockClear()
      localStorage.setItem('gudu-token', 'abc')
      await expect(onSuccess()({ data: { code: 403, msg: '无后台权限' } })).rejects.toThrow('无后台权限')
      expect(spy).toHaveBeenCalledWith('无后台权限')
      expect(localStorage.getItem('gudu-token')).toBe('abc')
      expect(hrefSetter).not.toHaveBeenCalled()
    })

    it('code==403 无 msg → 兜底「无权限访问」', async () => {
      await expect(onSuccess()({ data: { code: 403 } })).rejects.toThrow('无权限访问')
    })

    it('其它非 0 code → reject 且 ElMessage.error 被调用', async () => {
      const { ElMessage } = await import('element-plus')
      const spy = vi.mocked(ElMessage.error)
      spy.mockClear()
      await expect(
        onSuccess()({ data: { code: 500, msg: '服务器错误' } }),
      ).rejects.toThrow('服务器错误')
      expect(spy).toHaveBeenCalledWith('服务器错误')
    })

    it('响应体无 code 字段（非标准 R）→ 原样返回 body', async () => {
      const body = { arbitrary: true }
      const r = await onSuccess()({ data: body })
      expect(r).toBe(body)
    })

    it('msg 缺省时兜底「请求失败」', async () => {
      const { ElMessage } = await import('element-plus')
      const spy = vi.mocked(ElMessage.error)
      spy.mockClear()
      await expect(
        onSuccess()({ data: { code: 3 } }),
      ).rejects.toThrow('请求失败')
      expect(spy).toHaveBeenCalledWith('请求失败')
    })
  })

  describe('响应拦截器 - 网络错误分支', () => {
    it('网络错误 → ElMessage.error + reject', async () => {
      const { ElMessage } = await import('element-plus')
      const spy = vi.mocked(ElMessage.error)
      spy.mockClear()
      const err = { response: { data: { msg: '网关错误' } }, message: 'Network Error' }
      await expect(responseError[0](err)).rejects.toBe(err)
      expect(spy).toHaveBeenCalledWith('网关错误')
    })

    it('网络错误无 response.data.msg → 用 error.message', async () => {
      const { ElMessage } = await import('element-plus')
      const spy = vi.mocked(ElMessage.error)
      spy.mockClear()
      const err = { message: 'timeout of 15000ms exceeded' }
      await expect(responseError[0](err)).rejects.toBe(err)
      expect(spy).toHaveBeenCalledWith('timeout of 15000ms exceeded')
    })

    it('无任何信息 → 兜底「网络错误」', async () => {
      const { ElMessage } = await import('element-plus')
      const spy = vi.mocked(ElMessage.error)
      spy.mockClear()
      const err: any = {}
      await expect(responseError[0](err)).rejects.toBe(err)
      expect(spy).toHaveBeenCalledWith('网络错误')
    })
  })

  it('request() 函数透传 config 给 instance.request', async () => {
    vi.mocked(instance.request).mockResolvedValue({ id: 1 })
    const r = await request({ url: '/x', method: 'get' })
    expect(instance.request).toHaveBeenCalled()
    expect(r).toEqual({ id: 1 })
  })
})
