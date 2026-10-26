// 把 docs/legal/*.md 同步到三端：menu-mini src/static/legal/*.ts（字符串常量）、menu-flutter assets/legal/*.md（原样复制）。
// 用法：node scripts/sync-legal.mjs（改完 docs/legal 后执行，并升级 menu-mini src/utils/consent.ts 的 LEGAL_VERSION）
import { readFileSync, writeFileSync, copyFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const docs = [
  { name: 'privacy', title: '隐私政策', constName: 'PRIVACY_MD' },
  { name: 'terms', title: '用户协议', constName: 'TERMS_MD' },
]

for (const d of docs) {
  const src = join(root, 'docs/legal', `${d.name}.md`)
  const md = readFileSync(src, 'utf8')
  if (/【[^】]*】/.test(md)) throw new Error(`${src} 仍有【占位符】，不能同步`)
  writeFileSync(
    join(root, 'menu-mini/src/static/legal', `${d.name}.ts`),
    `// 《${d.title}》正文：由 docs/legal/${d.name}.md 经 scripts/sync-legal.mjs 生成，请勿手改。\n` +
      `export const ${d.constName}: string = ${JSON.stringify(md)}\n`,
  )
  copyFileSync(src, join(root, 'menu-flutter/assets/legal', `${d.name}.md`))
  console.log(`synced ${d.name}`)
}
