import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { parseLegal, stripInline } from '@/utils/legal'
import { TERMS_MD } from '@/static/legal/terms'
import { PRIVACY_MD } from '@/static/legal/privacy'

describe('utils/legal parseLegal', () => {
  it('标题 / 段落 / 列表 / 表格 / 粗体', () => {
    const blocks = parseLegal(
      ['# 标题', '', '**生效日期：x**', '', '## 一、服务', '第一行', '第二行', '', '1. 甲', '- 乙', '', '| 名称 | 用途 |', '|---|---|', '| 微信 | 登录 |'].join('\n'),
    )
    expect(blocks[0]).toEqual({ type: 'h1', text: '标题' })
    expect(blocks[1]).toEqual({ type: 'p', text: '生效日期：x' })
    expect(blocks[2]).toEqual({ type: 'h2', text: '一、服务' })
    expect(blocks[3]).toEqual({ type: 'p', text: '第一行第二行' })
    expect(blocks[4]).toEqual({ type: 'li', marker: '1.', text: '甲' })
    expect(blocks[5]).toEqual({ type: 'li', marker: '·', text: '乙' })
    expect(blocks[6]).toEqual({ type: 'li', marker: '·', text: '名称 · 用途' })
    expect(blocks[7]).toEqual({ type: 'li', marker: '·', text: '微信 · 登录' })
    expect(blocks).toHaveLength(8)
  })

  it('stripInline 去链接/代码', () => {
    expect(stripInline('见 [隐私政策](./privacy.md) 与 `code`')).toBe('见 隐私政策 与 code')
  })

  it('内置正文与 docs/legal 源文件一致，且已无【】占位', () => {
    const root = resolve(__dirname, '../../..')
    expect(TERMS_MD).toBe(readFileSync(resolve(root, 'docs/legal/terms.md'), 'utf8'))
    expect(PRIVACY_MD).toBe(readFileSync(resolve(root, 'docs/legal/privacy.md'), 'utf8'))
    expect(TERMS_MD + PRIVACY_MD).not.toMatch(/【[^】]*】/)
    expect(PRIVACY_MD).toContain('maxiaofeidev@gmail.com')
    expect(parseLegal(PRIVACY_MD).some((b) => b.type === 'h2')).toBe(true)
  })
})
