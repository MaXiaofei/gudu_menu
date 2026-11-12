/** 协议 / 隐私政策 Markdown（docs/legal/*.md 的子集）→ 可渲染块。 */

export type LegalBlock =
  | { type: 'h1' | 'h2' | 'h3' | 'p'; text: string }
  | { type: 'li'; text: string; marker: string }

/** 去掉 **粗体**、`代码`、[文字](链接) 等行内标记，保留文字。 */
export function stripInline(s: string): string {
  return s
    .replace(/\*\*(.+?)\*\*/g, '$1')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
    .trim()
}

/** 按行解析：# / ## / ### 标题，- * 1. 列表，表格行转为「a · b · c」，其余合并为段落。 */
export function parseLegal(md: string): LegalBlock[] {
  const out: LegalBlock[] = []
  let para: string[] = []
  const flush = () => {
    if (para.length) out.push({ type: 'p', text: stripInline(para.join('')) })
    para = []
  }
  for (const raw of (md || '').split(/\r?\n/)) {
    const line = raw.trim()
    if (!line) { flush(); continue }
    let m: RegExpExecArray | null
    if ((m = /^(#{1,3})\s+(.*)$/.exec(line))) {
      flush()
      out.push({ type: (['h1', 'h2', 'h3'] as const)[m[1].length - 1], text: stripInline(m[2]) })
    } else if ((m = /^([-*]|\d+[.、])\s+(.*)$/.exec(line))) {
      flush()
      out.push({ type: 'li', marker: /^\d/.test(m[1]) ? m[1].replace('、', '.') : '·', text: stripInline(m[2]) })
    } else if (/^\|.*\|$/.test(line)) {
      flush()
      if (/^\|[\s:|-]+\|$/.test(line)) continue // 分隔行
      const cells = line.slice(1, -1).split('|').map((c) => stripInline(c)).filter(Boolean)
      if (cells.length) out.push({ type: 'li', marker: '·', text: cells.join(' · ') })
    } else if (/^(---+|\*\*\*+)$/.test(line)) {
      flush()
    } else {
      para.push(line.replace(/^>\s?/, ''))
    }
  }
  flush()
  return out
}

import { TERMS_MD } from '@/static/legal/terms'
import { PRIVACY_MD } from '@/static/legal/privacy'
import { TERMS_NOAI_MD } from '@/static/legal/terms-noai'
import { PRIVACY_NOAI_MD } from '@/static/legal/privacy-noai'

/** 评价入口被后台关停时，协议/隐私里不再出现点评、评分、点评照片等描述。 */
function withoutReview(md: string): string {
  return md
    .replace(
      '| 食记与点评 | 做菜记录、饮食日志、评分与点评文字、照片 | 记录饮食、生成营养报告、形成口味画像 | 使用该功能时必需 |',
      '| 食记 | 做菜记录、饮食日志 | 记录饮食、生成营养报告 | 使用该功能时必需 |',
    )
    .replace('采购清单、食记、餐后点评、聚餐协同点菜', '采购清单、食记、聚餐协同点菜')
    .replace('您录入或上传的菜谱、图片、点评等内容', '您录入或上传的菜谱、图片等内容')
}

/** 协议正文：aiOn=false（本端四类 AI 全关）取「无 AI 版」，否则完整版；reviewOn=false 时不展示评价相关描述。 */
export function legalMarkdown(type: 'terms' | 'privacy', aiOn: boolean, reviewOn = true): string {
  const md = type === 'privacy' ? (aiOn ? PRIVACY_MD : PRIVACY_NOAI_MD) : aiOn ? TERMS_MD : TERMS_NOAI_MD
  return reviewOn ? md : withoutReview(md)
}

/** 成员档案「敏感信息授权」说明：本端四类 AI 全关时不提 AI 回答。 */
export function consentHint(aiOn: boolean): string {
  return aiOn
    ? '健康档案与儿童信息仅用于营养计算、菜品过滤与 AI 回答，加密存储；撤回后删除对应信息'
    : '健康档案与儿童信息仅用于营养计算与菜品过滤，加密存储；撤回后删除对应信息'
}
