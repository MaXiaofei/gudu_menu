import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { APP_VERSION } from '@/config/app'

// 关于页版本号 = manifest.json versionName（manifest 带注释，正则取值）
describe('config/app APP_VERSION', () => {
  it('与 manifest.json versionName 一致', () => {
    const manifest = readFileSync(resolve(__dirname, '../../src/manifest.json'), 'utf8')
    const m = /"versionName"\s*:\s*"([^"]+)"/.exec(manifest)
    expect(m?.[1]).toBe(APP_VERSION)
  })
})
