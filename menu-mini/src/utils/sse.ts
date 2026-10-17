/**
 * SSE（text/event-stream）流式解析 + 两端传输（showcase-ai-community contracts §1）。
 *
 * - 微信小程序没有 EventSource：用 uni.request 的 enableChunked + onChunkReceived 拿 ArrayBuffer 分块；
 * - H5：fetch + ReadableStream。
 * 分块可能在任意字节处切断（含 UTF-8 多字节字符中间、事件中间），解析器与解码器都按流式处理。
 */
import { BASE, getToken } from './request'

export interface SseEvent {
  event: string
  /** data 行拼接后尝试 JSON.parse，失败保留原字符串 */
  data: any
  id?: string
}

export interface SseParser {
  push(text: string): void
  /** 流结束：派发尚未以空行结尾的最后一个事件 */
  end(): void
}

/** 按 SSE 规范解析：空行派发；`event:`/`data:`/`id:`；`:` 开头为注释；兼容 \r\n。 */
export function createSseParser(onEvent: (e: SseEvent) => void): SseParser {
  let buf = ''
  let event = ''
  let data: string[] = []
  let id: string | undefined

  const dispatch = () => {
    if (!data.length && !event) return
    const raw = data.join('\n')
    let parsed: any = raw
    try {
      parsed = raw ? JSON.parse(raw) : {}
    } catch {
      parsed = raw
    }
    onEvent({ event: event || 'message', data: parsed, id })
    event = ''
    data = []
  }

  const line = (l: string) => {
    if (l === '') return dispatch()
    if (l.startsWith(':')) return
    const i = l.indexOf(':')
    const field = i < 0 ? l : l.slice(0, i)
    let value = i < 0 ? '' : l.slice(i + 1)
    if (value.startsWith(' ')) value = value.slice(1)
    if (field === 'event') event = value
    else if (field === 'data') data.push(value)
    else if (field === 'id') id = value
  }

  return {
    push(text: string) {
      buf += text
      let nl: number
      while ((nl = buf.search(/\r\n|\r|\n/)) >= 0) {
        const len = buf[nl] === '\r' && buf[nl + 1] === '\n' ? 2 : 1
        // 末尾单独的 \r 可能是 \r\n 被切开，等下一块
        if (buf[nl] === '\r' && nl === buf.length - 1) break
        line(buf.slice(0, nl))
        buf = buf.slice(nl + len)
      }
    },
    end() {
      if (buf) {
        line(buf)
        buf = ''
      }
      dispatch()
    },
  }
}

/** 流式 UTF-8 解码：保留被切断的多字节序列到下一块（小程序基础库不保证有 TextDecoder）。 */
export function createUtf8Decoder() {
  let pending: number[] = []
  return (chunk: ArrayBuffer | Uint8Array): string => {
    const bytes = chunk instanceof Uint8Array ? chunk : new Uint8Array(chunk)
    const all = pending.length ? Uint8Array.from([...pending, ...bytes]) : bytes
    pending = []
    let out = ''
    let i = 0
    while (i < all.length) {
      const b = all[i]
      const need = b < 0x80 ? 1 : b >= 0xf0 ? 4 : b >= 0xe0 ? 3 : b >= 0xc0 ? 2 : 1
      if (i + need > all.length) {
        pending = Array.from(all.slice(i))
        break
      }
      let cp: number
      if (need === 1) cp = b < 0x80 ? b : 0xfffd
      else if (need === 2) cp = ((b & 0x1f) << 6) | (all[i + 1] & 0x3f)
      else if (need === 3) cp = ((b & 0x0f) << 12) | ((all[i + 1] & 0x3f) << 6) | (all[i + 2] & 0x3f)
      else cp = ((b & 0x07) << 18) | ((all[i + 1] & 0x3f) << 12) | ((all[i + 2] & 0x3f) << 6) | (all[i + 3] & 0x3f)
      out += String.fromCodePoint(cp)
      i += need
    }
    return out
  }
}

export interface SseRequest {
  /** 相对路径（不含 BASE） */
  url: string
  method?: 'GET' | 'POST'
  data?: unknown
  onEvent: (e: SseEvent) => void
  /** 网络失败 / 非 200 */
  onError?: (err: Error) => void
  onDone?: () => void
}

export interface SseHandle {
  abort(): void
}

/** H5：fetch ReadableStream。 */
export function fetchTransport(req: SseRequest, fetchImpl: typeof fetch = fetch): SseHandle {
  const ctrl = typeof AbortController !== 'undefined' ? new AbortController() : null
  const parser = createSseParser(req.onEvent)
  const decode = createUtf8Decoder()
  ;(async () => {
    try {
      const res = await fetchImpl(BASE + req.url, {
        method: req.method || 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream', Authorization: getToken() },
        body: req.data === undefined ? undefined : JSON.stringify(req.data),
        signal: ctrl?.signal,
      })
      if (!res.ok || !res.body) throw new Error(`HTTP ${res.status}`)
      const reader = res.body.getReader()
      for (;;) {
        const { done, value } = await reader.read()
        if (done) break
        if (value) parser.push(decode(value))
      }
      parser.end()
      req.onDone?.()
    } catch (e: any) {
      if (e?.name === 'AbortError') return
      req.onError?.(e instanceof Error ? e : new Error(String(e)))
    }
  })()
  return { abort: () => ctrl?.abort() }
}

/** 微信小程序：uni.request enableChunked + onChunkReceived。 */
export function chunkedTransport(req: SseRequest, requestImpl: any = uni.request): SseHandle {
  const parser = createSseParser(req.onEvent)
  const decode = createUtf8Decoder()
  let aborted = false
  const task: any = requestImpl({
    url: BASE + req.url,
    method: req.method || 'POST',
    data: req.data as any,
    enableChunked: true,
    responseType: 'arraybuffer',
    timeout: 120000,
    header: { 'Content-Type': 'application/json', Accept: 'text/event-stream', Authorization: getToken() },
    success: (res: any) => {
      if (aborted) return
      if (res?.statusCode && res.statusCode !== 200) {
        req.onError?.(new Error(`HTTP ${res.statusCode}`))
        return
      }
      parser.end()
      req.onDone?.()
    },
    fail: (e: any) => {
      if (!aborted) req.onError?.(new Error(e?.errMsg || '网络异常'))
    },
  } as any)
  task?.onChunkReceived?.((r: { data: ArrayBuffer }) => {
    if (!aborted) parser.push(decode(r.data))
  })
  return {
    abort() {
      aborted = true
      task?.abort?.()
    },
  }
}

/** 按平台选择传输。 */
export function streamSse(req: SseRequest): SseHandle {
  // #ifdef H5
  return fetchTransport(req)
  // #endif
  // #ifndef H5
  return chunkedTransport(req)
  // #endif
}
