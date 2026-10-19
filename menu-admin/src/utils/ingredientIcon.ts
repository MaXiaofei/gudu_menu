// 食材 → 插画 key。规则单一来源：docs/design/ingredient-icons.json（后端 V68 回填 icon_key 用同一份规则）；
// 素材由 scripts/design/gen-assets.mjs 复制到 src/assets/ing/。
// 优先级：接口给的 iconKey → 名称关键词（取最长匹配）→ 采购品类兜底 → null（组件退首字）。
import rules from '../../../docs/design/ingredient-icons.json'

const ICON_URLS = import.meta.glob('../assets/ing/*.svg', {
  eager: true,
  query: '?url',
  import: 'default',
}) as Record<string, string>

const URL_BY_KEY: Record<string, string> = Object.fromEntries(
  Object.entries(ICON_URLS).map(([path, url]) => [path.replace(/^.*\/(.+)\.svg$/, '$1'), url]),
)

const KEYWORDS = (rules.keywords as [string, string[]][]).flatMap(([key, words]) =>
  words.map((w) => ({ key, word: w })),
)
const CATEGORY_FALLBACK = rules.categoryFallback as Record<string, string>

/** 已有素材的 key 集合（单测与守护用）。 */
export function availableIconKeys(): string[] {
  return Object.keys(URL_BY_KEY).sort()
}

/** 按名称关键词匹配：名称包含关键词，取最长的那个（「胡萝卜」优先于「萝卜」）。 */
export function matchIconKey(name: string | null | undefined): string | null {
  const n = (name ?? '').trim()
  if (!n) return null
  let best: { key: string; word: string } | null = null
  for (const k of KEYWORDS) {
    if (n.includes(k.word) && (!best || k.word.length > best.word.length)) best = k
  }
  return best && URL_BY_KEY[best.key] ? best.key : null
}

export function resolveIconKey(opts: {
  iconKey?: string | null
  name?: string | null
  categoryName?: string | null
}): string | null {
  if (opts.iconKey && URL_BY_KEY[opts.iconKey]) return opts.iconKey
  const byName = matchIconKey(opts.name)
  if (byName) return byName
  const cat = opts.categoryName ? CATEGORY_FALLBACK[opts.categoryName] : undefined
  return cat && URL_BY_KEY[cat] ? cat : null
}

export function iconUrl(key: string | null | undefined): string | undefined {
  return key ? URL_BY_KEY[key] : undefined
}
