import { describe, it, expect } from 'vitest'
import type { Provider } from '@/api/aiGateway'
import {
  candidatesFor, ensureMasked, isValidBaseUrl, keyDisplay, moveItem, peakHoursText, priceText, reorder, targetSupports,
  toModelBody, toModelDraft, validateProvider, validateRoute, DEFAULT_PEAK_HOURS, type ModelDraft, type ProviderDraft,
} from './aiGateway'

const md = (o: Partial<ModelDraft> = {}): ModelDraft => ({
  model: 'deepseek-flash', capabilities: ['CHAT', 'TOOLS', 'VISION'], cacheHitPrice: '0.2', inputPrice: '1', outputPrice: '4', peakHours: DEFAULT_PEAK_HOURS, ...o,
})

const draft = (o: Partial<ProviderDraft> = {}): ProviderDraft => ({
  name: 'DeepSeek', baseUrl: 'https://api.deepseek.com/v1', apiKey: 'sk-abc', enabled: true,
  models: [md()], ...o,
})

describe('供应商表单校验', () => {
  it('合法表单无错误', () => {
    expect(validateProvider(draft(), { editing: false })).toEqual({})
  })
  it('URL：协议、查询参数', () => {
    expect(isValidBaseUrl('https://api.deepseek.com/v1')).toBe(true)
    expect(isValidBaseUrl('http://10.0.0.2:8000/v1')).toBe(true)
    expect(isValidBaseUrl('api.deepseek.com')).toBe(false)
    expect(isValidBaseUrl('ftp://x.com')).toBe(false)
    expect(isValidBaseUrl('https://x.com/v1?key=1')).toBe(false)
    expect(validateProvider(draft({ baseUrl: 'deepseek' }), { editing: false }).baseUrl).toBeTruthy()
  })
  it('key：新增必填，编辑留空 = 不修改', () => {
    expect(validateProvider(draft({ apiKey: '' }), { editing: false }).apiKey).toBeTruthy()
    expect(validateProvider(draft({ apiKey: '' }), { editing: true }).apiKey).toBeUndefined()
  })
  it('价格非负且最多两位小数；至少一个能力；模型不重复', () => {
    const e = validateProvider(
      draft({
        models: [
          md({ model: 'a', capabilities: [], inputPrice: '-1', outputPrice: '1.234', cacheHitPrice: 'x' }),
          md({ model: 'a', capabilities: ['CHAT'], inputPrice: '0', outputPrice: '0.5' }),
        ],
      }),
      { editing: false },
    )
    expect(e['models.0.capabilities']).toContain('至少')
    expect(e['models.0.inputPrice']).toBeTruthy()
    expect(e['models.0.outputPrice']).toBeTruthy()
    expect(e['models.0.cacheHitPrice']).toBeTruthy()
    expect(e['models.1.model']).toContain('重复')
    expect(e['models.1.inputPrice']).toBeUndefined()
    expect(validateProvider(draft({ models: [] }), { editing: false }).models).toBeTruthy()
  })
  it('元 ↔ 分换算，能力按固定顺序，峰谷说明', () => {
    const body = toModelBody(md({ model: ' glm-4v ', capabilities: ['VISION', 'CHAT'], cacheHitPrice: '0.5', inputPrice: '2.5', outputPrice: '10', peakHours: ' ' }))
    expect(body).toEqual({ model: 'glm-4v', capabilities: ['CHAT', 'VISION'], price: { inputCacheHitFen: 50, inputFen: 250, outputFen: 1000 }, peakHours: null })
    expect(toModelDraft(body)).toMatchObject({ cacheHitPrice: '0.5', inputPrice: '2.5', outputPrice: '10', peakHours: '' })
    expect(peakHoursText(DEFAULT_PEAK_HOURS)).toBe('高峰时段：工作日 9–12、14–18，空闲半价')
    expect(peakHoursText(null)).toBe('不分峰谷')
    expect(priceText(400)).toBe('¥4')
    expect(priceText(20)).toBe('¥0.2')
  })
})

describe('降级链排序', () => {
  it('上移 / 下移 / 越界不变', () => {
    expect(moveItem(['a', 'b', 'c'], 2, -1)).toEqual(['a', 'c', 'b'])
    expect(moveItem(['a', 'b', 'c'], 0, 1)).toEqual(['b', 'a', 'c'])
    expect(moveItem(['a', 'b'], 0, -1)).toEqual(['a', 'b'])
  })
  it('拖拽重排', () => {
    expect(reorder(['a', 'b', 'c'], 0, 2)).toEqual(['b', 'c', 'a'])
    expect(reorder(['a', 'b', 'c'], 2, 0)).toEqual(['c', 'a', 'b'])
  })
  it('路由校验：主模型、重复、超时与重试范围', () => {
    const p = { providerId: 1, model: 'deepseek-chat' }
    expect(validateRoute({ primary: null, fallbacks: [], timeoutMs: 20000, maxRetries: 1 })).toContain('主模型')
    expect(validateRoute({ primary: p, fallbacks: [p], timeoutMs: 20000, maxRetries: 1 })).toContain('主模型')
    const g = { providerId: 2, model: 'glm-4' }
    expect(validateRoute({ primary: p, fallbacks: [g, g], timeoutMs: 20000, maxRetries: 1 })).toContain('重复')
    expect(validateRoute({ primary: p, fallbacks: [g], timeoutMs: 500, maxRetries: 1 })).toContain('超时')
    expect(validateRoute({ primary: p, fallbacks: [g], timeoutMs: 20000, maxRetries: 5 })).toContain('重试')
    expect(validateRoute({ primary: p, fallbacks: [g], timeoutMs: 20000, maxRetries: 2 })).toBeNull()
  })
})

const prov = (o: Partial<Provider>): Provider => ({
  providerId: 1, name: 'DeepSeek', type: 'OPENAI_COMPATIBLE', baseUrl: 'https://api.deepseek.com/v1',
  apiKeyMasked: 'sk-****a1b2', keySource: 'ENV', enabled: true,
  models: [
    { model: 'deepseek-flash', capabilities: ['CHAT', 'TOOLS', 'VISION'], price: { inputCacheHitFen: 20, inputFen: 100, outputFen: 400 }, peakHours: DEFAULT_PEAK_HOURS },
    { model: 'deepseek-v4-pro', capabilities: ['CHAT', 'TOOLS'], price: { inputCacheHitFen: 90, inputFen: 450, outputFen: 1800 }, peakHours: DEFAULT_PEAK_HOURS },
  ],
  health: { ok: true, latencyMs: 320, checkedAt: null }, ...o,
})

describe('掩码与候选', () => {
  it('掩码展示与来源', () => {
    expect(keyDisplay(prov({}))).toEqual({ text: 'sk-****a1b2', source: '环境变量' })
    expect(keyDisplay(prov({ apiKeyMasked: null, keySource: 'NONE' }))).toEqual({ text: '—', source: '未配置' })
    expect(keyDisplay(prov({ type: 'MOCK' })).text).toBe('无需 key')
    expect(ensureMasked('sk-1234567890abcd')).toBe('sk-****abcd')
    expect(ensureMasked('sk-****abcd')).toBe('sk-****abcd')
    expect(ensureMasked('short')).toBe('****')
  })
  it('按能力筛候选：停用不出现；VISION 只能选带视觉能力的模型', () => {
    const list = [
      prov({}),
      prov({ providerId: 2, name: 'GLM', enabled: false }),
      prov({ providerId: 9, name: '规则兜底', type: 'MOCK', models: [{ model: 'mock', capabilities: ['CHAT', 'TOOLS'], price: { inputCacheHitFen: 0, inputFen: 0, outputFen: 0 }, peakHours: null }] }),
    ]
    expect(candidatesFor(list, 'TOOLS').map((c) => c.label)).toEqual(['DeepSeek · deepseek-flash', 'DeepSeek · deepseek-v4-pro', '规则兜底 · mock'])
    expect(candidatesFor(list, 'VISION').map((c) => c.label)).toEqual(['DeepSeek · deepseek-flash'])
    expect(targetSupports(list, { providerId: 1, model: 'deepseek-flash' }, 'VISION')).toBe(true)
    expect(targetSupports(list, { providerId: 1, model: 'deepseek-v4-pro' }, 'VISION')).toBe(false)
    expect(targetSupports(list, { providerId: 2, model: 'deepseek-flash' }, 'CHAT')).toBe(false)
  })
})
