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
