import { describe, it, expect } from 'vitest'
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, resolve } from 'node:path'

// 微信 showModal 的 confirmText / cancelText 最多 4 个字符，超出时弹窗直接失败（fail 回调），
// 用户看到的是「点了没反应」（2026-10-28 成员档案「撤回」按钮即因「撤回并删除」5 个字失效）。
const SRC = resolve(__dirname, '../../src')

function files(dir: string): string[] {
  return readdirSync(dir).flatMap((n) => {
    const p = join(dir, n)
    if (statSync(p).isDirectory()) return files(p)
    return /\.(vue|ts)$/.test(n) ? [p] : []
  })
}

function buttonTexts(src: string): string[] {
  const out: string[] = []
  for (const m of src.matchAll(/(?:confirmText|cancelText)\s*[:=]\s*'([^']*)'/g)) out.push(m[1])
  // askModal(title, content, '确认', '取消') / ask(title, content, '确认')
  for (const m of src.matchAll(/\b(?:askModal|ask)\(\s*[^,]+,\s*(?:`[^`]*`|'[^']*'|[^,()]+),\s*'([^']*)'(?:\s*,\s*'([^']*)')?/g)) {
    out.push(m[1])
    if (m[2] !== undefined) out.push(m[2])
  }
  return out
}

describe('弹窗按钮文字不超过 4 个字', () => {
  it('全部源码里的 confirmText / cancelText 都 ≤ 4 个字', () => {
    const bad = files(SRC).flatMap((f) =>
      buttonTexts(readFileSync(f, 'utf8'))
        .filter((t) => [...t].length > 4)
        .map((t) => `${f.replace(SRC, 'src')}: ${t}`),
    )
    expect(bad).toEqual([])
  })
})
