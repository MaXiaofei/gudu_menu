import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

/**
 * 按平台解析 src/pages.json（模拟 uni-app 条件编译）：
 * 只认本仓库用到的 `// #ifdef X` / `// #ifndef X` / `// #endif`（不嵌套），其余 // 注释行丢弃。
 * platform 用 uni 的大写写法：'H5' / 'MP-WEIXIN'。
 */
export function loadPagesJson(platform: 'H5' | 'MP-WEIXIN' = 'H5'): any {
  const raw = readFileSync(resolve(__dirname, '../../src/pages.json'), 'utf8')
  const out: string[] = []
  let keep = true
  let inBlock = false
  for (const line of raw.split('\n')) {
    const t = line.trim()
    const m = t.match(/^\/\/\s*#(ifdef|ifndef)\s+(.+)$/)
    if (m) {
      if (inBlock) throw new Error('pages.json 条件编译不支持嵌套')
      inBlock = true
      const hit = m[2].split('||').map((s) => s.trim()).includes(platform)
      keep = m[1] === 'ifdef' ? hit : !hit
      continue
    }
    if (/^\/\/\s*#endif/.test(t)) {
      inBlock = false
      keep = true
      continue
    }
    if (t.startsWith('//')) continue
    if (keep) out.push(line)
  }
  // 剔除块后可能留下的尾逗号
  const json = out.join('\n').replace(/,(\s*[}\]])/g, '$1')
  return JSON.parse(json)
}
