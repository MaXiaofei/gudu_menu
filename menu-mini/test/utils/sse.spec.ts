import { describe, it, expect, vi } from 'vitest'
import { createSseParser, createUtf8Decoder, chunkedTransport, fetchTransport, type SseEvent } from '@/utils/sse'

function collect() {
  const evs: SseEvent[] = []
  return { evs, p: createSseParser((e) => evs.push(e)) }
}

describe('utils/sse 解析器', () => {
  it('按空行派发；event + data JSON', () => {
    const { evs, p } = collect()
    p.push('event: meta\ndata: {"messageId":"m1","traceId":"t1"}\n\nevent: token\ndata: {"text":"你好"}\n\n')
    expect(evs).toEqual([
      { event: 'meta', data: { messageId: 'm1', traceId: 't1' }, id: undefined },
      { event: 'token', data: { text: '你好' }, id: undefined },
    ])
  })

  it('事件被任意切块：行中间、\\r\\n 中间都能拼回', () => {
    const { evs, p } = collect()
    const raw = 'event: tool_start\r\ndata: {"name":"get_pantry","label":"查余量"}\r\n\r\nevent: done\r\ndata: {}\r\n\r\n'
    for (let i = 0; i < raw.length; i += 3) p.push(raw.slice(i, i + 3))
    expect(evs.map((e) => e.event)).toEqual(['tool_start', 'done'])
    expect(evs[0].data.label).toBe('查余量')
  })

  it('多行 data 用 \\n 拼接；注释行忽略；无 event 默认 message', () => {
    const { evs, p } = collect()
    p.push(': ping\ndata: line1\ndata: line2\n\n')
    expect(evs).toEqual([{ event: 'message', data: 'line1\nline2', id: undefined }])
  })

  it('end() 派发未以空行结尾的最后一个事件', () => {
    const { evs, p } = collect()
    p.push('event: done\ndata: {"degraded":true}')
    expect(evs).toHaveLength(0)
    p.end()
    expect(evs[0]).toMatchObject({ event: 'done', data: { degraded: true } })
  })
})

describe('utils/sse UTF-8 流式解码', () => {
  it('多字节中文被切在字节中间也能正确拼回', () => {
    const bytes = new TextEncoder().encode('麻婆豆腐做完了🍲')
    const dec = createUtf8Decoder()
    let out = ''
    for (let i = 0; i < bytes.length; i++) out += dec(bytes.slice(i, i + 1))
    expect(out).toBe('麻婆豆腐做完了🍲')
  })
})

describe('utils/sse 传输', () => {
  it('小程序 chunked：onChunkReceived 分块 → 事件；success 后 onDone', () => {
    let chunkCb: (r: { data: ArrayBuffer }) => void = () => {}
    let opts: any
    const req = vi.fn((o: any) => {
      opts = o
      return { onChunkReceived: (cb: any) => (chunkCb = cb), abort: vi.fn() }
    })
    const evs: SseEvent[] = []
    const onDone = vi.fn()
    chunkedTransport({ url: '/agent/sessions/s1/messages', data: { text: 'hi' }, onEvent: (e) => evs.push(e), onDone }, req)
    expect(opts.enableChunked).toBe(true)
    expect(opts.url).toMatch(/\/agent\/sessions\/s1\/messages$/)
    const b = new TextEncoder().encode('event: token\ndata: {"text":"好"}\n\n')
    chunkCb({ data: b.slice(0, 20).buffer })
    chunkCb({ data: b.slice(20).buffer })
    expect(evs[0]).toMatchObject({ event: 'token', data: { text: '好' } })
    opts.success({ statusCode: 200 })
    expect(onDone).toHaveBeenCalled()
  })

  it('小程序 chunked：非 200 → onError', () => {
    let opts: any
    const onError = vi.fn()
    chunkedTransport({ url: '/x', onEvent: () => {}, onError }, (o: any) => ((opts = o), {}))
    opts.success({ statusCode: 500 })
    expect(onError).toHaveBeenCalled()
  })

  it('H5 fetch：ReadableStream 分块解析', async () => {
    const enc = new TextEncoder()
    const parts = [enc.encode('event: card\ndata: {"type":"rec'), enc.encode('ipes","items":[]}\n\n')]
    const body = {
      getReader() {
        let i = 0
        return { read: async () => (i < parts.length ? { done: false, value: parts[i++] } : { done: true, value: undefined }) }
      },
    }
    const fetchImpl = vi.fn(async () => ({ ok: true, status: 200, body })) as any
    const evs: SseEvent[] = []
    await new Promise<void>((resolve) => {
      fetchTransport({ url: '/agent/x', data: {}, onEvent: (e) => evs.push(e), onDone: resolve }, fetchImpl)
    })
    expect(evs[0]).toMatchObject({ event: 'card', data: { type: 'recipes', items: [] } })
  })
})
