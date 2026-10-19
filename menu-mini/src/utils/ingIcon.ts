/**
 * 食材插画与默认头像（prototype-ui-rollout P0）。
 *
 * - 规则单一来源 docs/design/ingredient-icons.json，素材清单 docs/design/assets.json；
 *   本目录的两份 JSON 是副本，test/utils/ing-icon.spec.ts 守护与源文件一致。
 * - 素材由 scripts/design/gen-assets.mjs 光栅化到 static/{ing,avatar}/<key>.png。
 * - 解析顺序：后端 iconKey → 名称关键词 → 采购品类兜底 → null（调用方退首字）。
 */
import rules from './ingredient-icons.json'
import assets from './assets.json'

const ING_KEYS = new Set<string>(assets.ing)
const AVATAR_KEYS: string[] = assets.avatar
const KEYWORDS = rules.keywords as [string, string[]][]
const CATEGORY_FALLBACK = rules.categoryFallback as Record<string, string>

/** 名称关键词匹配：取包含且关键词最长的一项（规则见 ingredient-icons.json matchRule）。 */
export function matchIngKey(name?: string | null): string | null {
  const n = (name || '').trim()
  if (!n) return null
  let best: string | null = null
  let bestLen = 0
  for (const [key, words] of KEYWORDS) {
    for (const w of words) {
      if (w.length > bestLen && n.includes(w)) {
        best = key
        bestLen = w.length
      }
    }
  }
  return best
}

/** 解析食材插画 key；都不命中返回 null。 */
export function resolveIngKey(p: { iconKey?: string | null; name?: string | null; category?: string | null }): string | null {
  if (p.iconKey && ING_KEYS.has(p.iconKey)) return p.iconKey
  const byName = matchIngKey(p.name)
  if (byName) return byName
  const byCat = p.category ? CATEGORY_FALLBACK[p.category.trim()] : undefined
  return byCat && ING_KEYS.has(byCat) ? byCat : null
}

/** key → 静态资源路径。 */
export function ingIconSrc(key: string | null): string {
  return key ? `/static/ing/${key}.png` : ''
}

const BABY_TAGS = ['宝宝', '辅食', '婴', '幼儿']

/** 成员默认插画头像：宝宝档案用 duoduo，其余按名字稳定散列到成人头像。 */
export function defaultAvatarKey(name?: string | null, tags?: string[] | null): string {
  if ((tags || []).some((t) => BABY_TAGS.some((b) => t.includes(b)))) return 'duoduo'
  const adults = AVATAR_KEYS.filter((k) => k !== 'duoduo')
  const s = (name || '').trim()
  let h = 0
  for (const ch of s) h = (h * 31 + ch.codePointAt(0)!) >>> 0
  return adults[h % adults.length]
}

export function defaultAvatarSrc(name?: string | null, tags?: string[] | null): string {
  return `/static/avatar/${defaultAvatarKey(name, tags)}.png`
}
