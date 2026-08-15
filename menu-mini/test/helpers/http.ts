/**
 * HTTP mock helper（对齐 Flutter 端 mock_http 范式）：
 * 按路由表拦截 uni.request，捕获调用供断言。
 *
 * 用法：
 *   const http = installHttpMock({
 *     'GET /dish/search': { records: [], total: 0 },
 *   })
 *   await searchDishes({ pageNum: 1 })
 *   expect(http.calls[0].data).toEqual({ pageNum: 1 })
 */

export interface HttpCall {
  method: string
  url: string // 相对路径（已去 host + /gudu 前缀）
  data?: any
  header?: Record<string, string>
}

export type RouteHandler = ((call: HttpCall) => any) | any

export interface HttpMock {
  calls: HttpCall[]
  callsOf: (path: string) => HttpCall[]
}

export function ok(data: any) {
  return { statusCode: 200, data: { code: 0, msg: 'ok', data } }
}

export function installHttpMock(routes: Record<string, RouteHandler>): HttpMock {
  const calls: HttpCall[] = []

  ;(globalThis as any).uni.request = async (opt: any) => {
    const url = opt.url.replace(/^https?:\/\/[^/]+\/gudu/, '')
    const call: HttpCall = { method: opt.method || 'GET', url, data: opt.data, header: opt.header }
    calls.push(call)

    const handler =
      routes[`${call.method} ${url}`] ?? routes[url] ?? routes[`${call.method} ${url.split('?')[0]}`]
    if (handler === undefined) {
      return { statusCode: 200, data: { code: 404, msg: `mock 未定义路由: ${call.method} ${url}`, data: null } }
    }
    const body = typeof handler === 'function' ? handler(call) : handler
    return ok(body)
  }

  return {
    calls,
    callsOf: (p) => calls.filter((c) => c.url.split('?')[0] === p || c.url === p),
  }
}
