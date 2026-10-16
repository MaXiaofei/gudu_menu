// node --test scripts/design/gen-tokens.test.mjs
import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  loadTokens, skinsOf, validateSkins, contrast, genFlutter, genMiniTs, genMiniCss, genAdminTs, PALETTE_KEYS,
} from './gen-tokens.mjs'

const tokens = loadTokens()
const clone = () => JSON.parse(JSON.stringify(tokens))

test('当前 tokens.json 全部皮肤通过校验', () => {
  const { errors } = validateSkins(tokens)
  assert.deepEqual(errors, [])
})

test('对比度计算：黑白 = 21', () => {
  assert.equal(Math.round(contrast('#000000', '#FFFFFF')), 21)
})

test('缺字段 / 非法色值 / 对比度不足 → 拒绝', () => {
  const t = clone()
  delete t.color.theme.cream.title
  assert.match(validateSkins(t).errors.join('\n'), /cream: title 必须是 #RRGGBB/)
  const t2 = clone()
  t2.color.theme.matcha.body = '#CCCCCC'
  assert.match(validateSkins(t2).errors.join('\n'), /matcha: body 在 bg 上对比度/)
  const t3 = clone()
  t3.color.theme.cream.chart = ['#fff']
  assert.match(validateSkins(t3).errors.join('\n'), /chart 至少 3 个/)
})

test('新增一套皮肤 → 三端产物都包含它', () => {
  const t = clone()
  t.color.theme.ocean = { ...t.color.theme.matcha, label: '海盐蓝', legacyKey: null, primary: '#3E7FA6', primaryDeep: '#2F6587' }
  assert.deepEqual(validateSkins(t).errors, [])
  assert.match(genFlutter(t), /kSkin_ocean = AppTokens\(/)
  assert.match(genFlutter(t), /key: 'ocean'/)
  assert.match(genMiniTs(t), /key: "ocean"/)
  assert.match(genAdminTs(t), /key: "ocean"/)
  assert.equal(skinsOf(t).length, skinsOf(tokens).length + 1)
})

test('第一项为默认皮肤，CSS 默认变量取自它', () => {
  const first = skinsOf(tokens)[0]
  const css = genMiniCss(tokens)
  assert.match(css, new RegExp(`--primary: ${first.primary};`))
  assert.match(genMiniTs(tokens), new RegExp(`DEFAULT_SKIN = "${first.key}"`))
})

test('每个 palette 字段都生成到 Flutter 与小程序产物', () => {
  const dart = genFlutter(tokens)
  const ts = genMiniTs(tokens)
  for (const k of PALETTE_KEYS.filter((k) => k !== 'onPrimary')) assert.match(dart, new RegExp(`  ${k}: Color\\(`))
  for (const k of PALETTE_KEYS) assert.match(ts, new RegExp(`${k}: "#`))
})

test('后台兼容旧持久化 key（warm/green）', () => {
  const ts = genAdminTs(tokens)
  assert.match(ts, /legacyKey: "warm"/)
  assert.match(ts, /legacyKey: "green"/)
})
