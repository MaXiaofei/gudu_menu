/**
 * 咕嘟回复的轻量 Markdown 兜底（后端约定输出纯文本，模型偶尔仍带标记）。与 Flutter lib/core/lite_markdown.dart 同规则。
 *
 * - `**粗体**` → 加粗；流式中尚未闭合的 `**` 去掉标记按普通文字显示，闭合后再变粗，避免闪出星号。
 * - 行首 `- ` / `* ` / `· ` → 「• 」列表项。
 * - 行首 `#`～`###` + 空格 → 整行加粗。
 * 其余字符原样；换行以 `\n` 片段保留（模板里用 white-space: pre-wrap 渲染）。
 */
export interface MdPart {
  text: string
  bold: boolean
}

const BULLET = /^(\s*)[-*·]\s+/
const HEADING = /^#{1,3}\s+/

function inline(line: string, forceBold: boolean, out: MdPart[]) {
  let bold = false
  let rest = line
  for (;;) {
    const at = rest.indexOf('**')
    if (at < 0) {
      if (rest) out.push({ text: rest, bold: forceBold || bold })
      return
    }
    if (!bold && rest.indexOf('**', at + 2) < 0) {
      const plain = rest.slice(0, at) + rest.slice(at + 2)
      if (plain) out.push({ text: plain, bold: forceBold })
      return
    }
    if (at > 0) out.push({ text: rest.slice(0, at), bold: forceBold || bold })
    bold = !bold
    rest = rest.slice(at + 2)
  }
}

export function parseLiteMarkdown(src: string): MdPart[] {
  const parts: MdPart[] = []
  const lines = (src || '').split('\n')
  lines.forEach((raw, i) => {
    let line = raw
    let lineBold = false
    const h = HEADING.exec(line)
    if (h) {
      line = line.slice(h[0].length)
      lineBold = true
    } else {
      const b = BULLET.exec(line)
      if (b) line = `${b[1]}• ${line.slice(b[0].length)}`
    }
    inline(line, lineBold, parts)
    if (i < lines.length - 1) parts.push({ text: '\n', bold: false })
  })
  const out: MdPart[] = []
  for (const p of parts) {
    const last = out[out.length - 1]
    if (last && last.bold === p.bold) last.text += p.text
    else out.push({ ...p })
  }
  return out
}
