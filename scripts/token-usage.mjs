#!/usr/bin/env node
/**
 * Claude Code token 用量统计（项目本地、零侵入）。
 *
 * 数据源：~/.claude/projects/<本项目编码目录> 下的全部 .jsonl（递归，含子代理）里的 assistant.message.usage。
 * 输出：
 *   docs/token-usage/ledger.jsonl  每次 assistant API 调用一行（只有 token/工具/时间，不含对话内容）
 *   docs/token-usage/report.md     按阶段 / 工具 / 会话 / 日期汇总
 *
 * 用法：
 *   node scripts/token-usage.mjs                 # 扫描并刷新报表（默认最近 14 天）
 *   node scripts/token-usage.mjs --days 7        # 最近 7 天
 *   node scripts/token-usage.mjs --days 0        # 全部历史
 *   node scripts/token-usage.mjs --report-only   # 只按现有 ledger 重新出报表
 *   node scripts/token-usage.mjs --hook          # 由 Stop hook 调用：读 stdin 的 transcript_path 增量记账
 *
 * 说明：
 * - 原始 token 会被 cache_read 放大，报表同时给「原始 token」和「加权 token（相对成本）」。
 * - 加权权重可在 docs/token-usage/pricing.json 覆盖；默认 input=1 / output=5 /
 *   cache_write=1.25 / cache_read=0.1（相对值，不是美元）。
 * - 阶段归类是启发式：先看 Bash 命令是否测试/部署/构建，再看工具类型。
 */

import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const OUT_DIR = path.join(REPO_ROOT, 'docs', 'token-usage')
const LEDGER = path.join(OUT_DIR, 'ledger.jsonl')
const REPORT = path.join(OUT_DIR, 'report.md')
const ERROR_LOG = path.join(OUT_DIR, 'hook-error.log')
const PRICING_FILE = path.join(OUT_DIR, 'pricing.json')
const PROJECTS_ROOT = path.join(os.homedir(), '.claude', 'projects')

const DEFAULT_WEIGHTS = { input: 1, output: 5, cacheWrite: 1.25, cacheRead: 0.1 }
const CATEGORY_ORDER = ['部署', '测试', '构建', '开发', '调研', '规划', '沟通决策', '其他工具', '对话决策']

const TEST_RE = /\b(vitest|mvnw?\s+test|mvn\s+test|playwright|vue-tsc|flutter\s+test|pytest|jest|npm\s+(run\s+)?test)\b/i
const DEPLOY_RE = /\b(git\s+push|gh\s+workflow|gh\s+run|ssh|sshpass|docker\s+compose|deploy\.sh|vercel\s+deploy)\b/i
const BUILD_RE = /\b(npm\s+run\s+build|mvnw?\s+(package|install|clean)|vite\s+build|flutter\s+build|build:mp-weixin|npm\s+(ci|install|i))\b/i
const VERSION_RE = /^\s*git\s+(status|diff|log|show|branch|rev-parse|fetch|checkout|merge|add|commit|remote|config|stash)\b/i
const RESEARCH_BASH_RE = /^\s*(ls|find|grep|rg|cat|sed|awk|head|tail|wc|file|stat|pwd|echo|jq|tree|which|command)\b/i
const VERIFY_BASH_RE = /^\s*(curl|wget|nc|open|screencapture|osascript|node|python|python3|npx|http|dig|ping)\b/i

function num(v) {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

function loadWeights() {
  try {
    const raw = JSON.parse(fs.readFileSync(PRICING_FILE, 'utf8'))
    return { ...DEFAULT_WEIGHTS, ...raw }
  } catch {
    return { ...DEFAULT_WEIGHTS }
  }
}

function encodedProjectPrefix() {
  // Claude Code 把项目绝对路径编码成目录名：/Users/a/b -> -Users-a-b
  return REPO_ROOT.replace(/[/.]/g, '-')
}

function projectDirs(extra = []) {
  const prefix = encodedProjectPrefix()
  let names = []
  try {
    names = fs.readdirSync(PROJECTS_ROOT, { withFileTypes: true })
      .filter((d) => d.isDirectory())
      .map((d) => d.name)
      .filter((n) => n === prefix || n.startsWith(prefix + '-'))
  } catch {
    // 没有 ~/.claude/projects 时返回空
  }
  const dirs = names.map((n) => path.join(PROJECTS_ROOT, n))
  for (const d of extra) if (d && !dirs.includes(path.resolve(d))) dirs.push(path.resolve(d))
  return dirs
}

function walkJsonl(dir, out = []) {
  let entries = []
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true })
  } catch {
    return out
  }
  for (const e of entries) {
    if (e.name === 'node_modules' || e.name === '.git') continue
    const p = path.join(dir, e.name)
    if (e.isDirectory()) walkJsonl(p, out)
    else if (e.isFile() && e.name.endsWith('.jsonl')) out.push(p)
  }
  return out
}

function sessionIdFromFile(file) {
  return path.basename(file).replace(/\.jsonl$/, '')
}

function toolInfo(msg) {
  const content = Array.isArray(msg?.content) ? msg.content : []
  return content
    .filter((b) => b && b.type === 'tool_use')
    .map((b) => ({ name: String(b.name || ''), input: b.input || {} }))
}

function bashCommandOf(tool) {
  return tool.name === 'Bash' ? String(tool.input?.command || '') : ''
}

/** 单条 Bash 命令的归类；越靠前优先级越高。 */
function bashCategory(cmd) {
  if (DEPLOY_RE.test(cmd)) return { category: '部署', command: 'deploy' }
  if (TEST_RE.test(cmd)) return { category: '测试', command: 'test' }
  if (BUILD_RE.test(cmd)) return { category: '构建', command: 'build' }
  if (VERSION_RE.test(cmd)) return { category: '版本管理', command: 'git' }
  if (RESEARCH_BASH_RE.test(cmd)) return { category: '调研', command: 'read' }
  if (VERIFY_BASH_RE.test(cmd)) return { category: '验证', command: 'verify' }
  return { category: '其他工具', command: 'bash' }
}

const BASH_PRIORITY = ['部署', '测试', '构建', '版本管理', '调研', '验证', '其他工具']

function classify(tools) {
  const names = tools.map((t) => t.name)
  const bash = tools.map(bashCommandOf).filter(Boolean)
  if (bash.length) {
    let best = null
    for (const cmd of bash) {
      const c = bashCategory(cmd)
      if (!best || BASH_PRIORITY.indexOf(c.category) < BASH_PRIORITY.indexOf(best.category)) best = c
    }
    // 同一消息同时有测试/部署和编辑时，归到命令类；纯编辑消息没有 bash，落到下面的开发。
    return best
  }
  if (names.some((n) => ['Edit', 'Write', 'NotebookEdit'].includes(n))) return { category: '开发', command: 'edit' }
  if (names.some((n) => ['Read', 'Grep', 'Glob', 'WebFetch', 'WebSearch', 'ToolSearch', 'Agent', 'Explore'].includes(n))) {
    return { category: '调研', command: 'research' }
  }
  if (names.includes('AskUserQuestion')) return { category: '沟通决策', command: 'ask' }
  if (names.includes('TodoWrite')) return { category: '规划', command: 'plan' }
  if (names.length) return { category: '其他工具', command: 'tool' }
  return { category: '对话决策', command: 'chat' }
}

function parseUsage(usage, weights) {
  const input = num(usage?.input_tokens)
  const output = num(usage?.output_tokens)
  const cacheWrite = num(usage?.cache_creation_input_tokens)
  const cacheRead = num(usage?.cache_read_input_tokens)
  const weighted =
    input * weights.input +
    output * weights.output +
    cacheWrite * weights.cacheWrite +
    cacheRead * weights.cacheRead
  return { input, output, cacheWrite, cacheRead, weighted }
}

/**
 * 从一批 JSONL 中提取 assistant 行，按 session+messageId 去重。
 * 同一 API 消息在 JSONL 里可能拆成 thinking / text / tool_use 多行，usage 相同；
 * 这里合并工具列表后重新归类，避免同一笔 token 被重复统计。
 */
function scanFiles(files, weights) {
  const merged = new Map()
  for (const file of files) {
    let text = ''
    try {
      text = fs.readFileSync(file, 'utf8')
    } catch {
      continue
    }
    const lines = text.split(/\r?\n/)
    for (const line of lines) {
      if (!line.trim()) continue
      let o
      try {
        o = JSON.parse(line)
      } catch {
        continue
      }
      if (o?.type !== 'assistant') continue
      const msg = o.message
      const usage = msg?.usage
      if (!usage) continue
      const sessionId = o.sessionId || sessionIdFromFile(file)
      const messageId = String(msg.id || o.uuid || `${path.basename(file)}:${o.timestamp || ''}:${merged.size}`)
      const key = `${sessionId}|${messageId}`
      const tools = toolInfo(msg)
      const ts = o.timestamp || ''
      const prev = merged.get(key)
      if (prev) {
        prev.tools.push(...tools)
        prev.sidechain = prev.sidechain || !!o.isSidechain
        if (ts && (!prev.ts || ts < prev.ts)) prev.ts = ts
        if (!prev.model || prev.model === 'unknown') prev.model = String(msg.model || o.requestedModel || 'unknown')
      } else {
        merged.set(key, {
          key,
          sessionId,
          messageId,
          ts,
          model: String(msg.model || o.requestedModel || 'unknown'),
          tools,
          sidechain: !!o.isSidechain,
          usage: parseUsage(usage, weights),
        })
      }
    }
  }
  return [...merged.values()].map((r) => {
    const { category, command } = classify(r.tools)
    return {
      key: r.key,
      sessionId: r.sessionId,
      messageId: r.messageId,
      ts: r.ts,
      model: r.model,
      category,
      command,
      tools: [...new Set(r.tools.map((t) => t.name))],
      sidechain: r.sidechain,
      ...r.usage,
    }
  })
}

/** ledger 里也可能有历史重复行：按 key 合并，优先保留带工具分类的那行。 */
function dedupeRows(rows) {
  const map = new Map()
  for (const r of rows) {
    if (!r?.key) continue
    const prev = map.get(r.key)
    if (!prev) {
      map.set(r.key, r)
      continue
    }
    const tools = [...new Set([...(prev.tools || []), ...(r.tools || [])])]
    const use = (r.tools?.length || 0) > (prev.tools?.length || 0) ? r : prev
    map.set(r.key, {
      ...prev,
      ...use,
      tools,
      sidechain: !!(prev.sidechain || r.sidechain),
      ts: prev.ts && r.ts ? (prev.ts < r.ts ? prev.ts : r.ts) : prev.ts || r.ts,
    })
  }
  return [...map.values()]
}

function writeLedger(rows) {
  fs.mkdirSync(OUT_DIR, { recursive: true })
  fs.writeFileSync(LEDGER, rows.map((r) => JSON.stringify(r)).join('\n') + (rows.length ? '\n' : ''))
}

function readLedger() {
  const rows = []
  try {
    const text = fs.readFileSync(LEDGER, 'utf8')
    for (const line of text.split(/\r?\n/)) {
      if (!line.trim()) continue
      try {
        rows.push(JSON.parse(line))
      } catch {
        // 忽略损坏行
      }
    }
  } catch {
    // ledger 不存在
  }
  return dedupeRows(rows)
}

function appendLedger(newRows) {
  if (!newRows.length) return 0
  fs.mkdirSync(OUT_DIR, { recursive: true })
  fs.appendFileSync(LEDGER, newRows.map((r) => JSON.stringify(r)).join('\n') + '\n')
  return newRows.length
}

function fmt(n) {
  return Math.round(num(n)).toLocaleString('zh-CN')
}

function pct(n, total) {
  return total ? ((n / total) * 100).toFixed(1) + '%' : '0.0%'
}

function sumBy(rows, keyFn, valueFn) {
  const map = new Map()
  for (const r of rows) {
    const k = keyFn(r)
    map.set(k, (map.get(k) || 0) + valueFn(r))
  }
  return map
}

function topEntries(map, limit = 20) {
  return [...map.entries()].sort((a, b) => b[1] - a[1]).slice(0, limit)
}

function buildReport(allRows, days) {
  const cutoff = days > 0 ? Date.now() - days * 86400_000 : 0
  const rows = allRows.filter((r) => {
    const t = Date.parse(r.ts)
    return !cutoff || (Number.isFinite(t) && t >= cutoff)
  })
  const weights = loadWeights()
  const total = rows.reduce(
    (a, r) => {
      a.input += num(r.input)
      a.output += num(r.output)
      a.cacheWrite += num(r.cacheWrite)
      a.cacheRead += num(r.cacheRead)
      a.weighted += num(r.weighted)
      a.raw += num(r.input) + num(r.output) + num(r.cacheWrite) + num(r.cacheRead)
      return a
    },
    { input: 0, output: 0, cacheWrite: 0, cacheRead: 0, weighted: 0, raw: 0 },
  )
  const cacheHit = total.input + total.cacheWrite + total.cacheRead
    ? total.cacheRead / (total.input + total.cacheWrite + total.cacheRead)
    : 0

  const byCategory = new Map()
  for (const r of rows) {
    const k = r.category || '未分类'
    const v = byCategory.get(k) || { calls: 0, input: 0, output: 0, cacheWrite: 0, cacheRead: 0, raw: 0, weighted: 0 }
    v.calls += 1
    v.input += num(r.input)
    v.output += num(r.output)
    v.cacheWrite += num(r.cacheWrite)
    v.cacheRead += num(r.cacheRead)
    v.raw += num(r.input) + num(r.output) + num(r.cacheWrite) + num(r.cacheRead)
    v.weighted += num(r.weighted)
    byCategory.set(k, v)
  }
  const categoryRows = [...byCategory.entries()].sort((a, b) => b[1].weighted - a[1].weighted)

  const byTool = new Map()
  for (const r of rows) {
    const tools = r.tools?.length ? r.tools : ['(无工具)']
    for (const t of tools) byTool.set(t, (byTool.get(t) || 0) + num(r.weighted) / tools.length)
  }

  const bySession = new Map()
  for (const r of rows) {
    const k = r.sessionId || 'unknown'
    const v = bySession.get(k) || { calls: 0, weighted: 0, first: r.ts, last: r.ts, models: new Set() }
    v.calls += 1
    v.weighted += num(r.weighted)
    if (r.ts && (!v.first || r.ts < v.first)) v.first = r.ts
    if (r.ts && (!v.last || r.ts > v.last)) v.last = r.ts
    if (r.model) v.models.add(r.model)
    bySession.set(k, v)
  }
  const sessionRows = [...bySession.entries()].sort((a, b) => b[1].weighted - a[1].weighted)

  const byDay = new Map()
  for (const r of rows) {
    const d = String(r.ts || '').slice(0, 10) || '未知日期'
    byDay.set(d, (byDay.get(d) || 0) + num(r.weighted))
  }
  const dayRows = [...byDay.entries()].sort((a, b) => a[0].localeCompare(b[0]))

  const topCalls = [...rows].sort((a, b) => num(b.weighted) - num(a.weighted)).slice(0, 10)

  const suggestions = []
  const testShare = ((byCategory.get('测试')?.weighted || 0) / (total.weighted || 1)) * 100
  const researchShare = ((byCategory.get('调研')?.weighted || 0) / (total.weighted || 1)) * 100
  const devShare = ((byCategory.get('开发')?.weighted || 0) / (total.weighted || 1)) * 100
  const sidechainWeighted = rows.filter((r) => r.sidechain).reduce((a, r) => a + num(r.weighted), 0)
  if (testShare > 25) suggestions.push(`测试占 ${testShare.toFixed(1)}%：优先跑定向用例（vitest 单文件 / mvnw -Dtest=...），避免每次全量。`)
  if (researchShare > 30) suggestions.push(`调研占 ${researchShare.toFixed(1)}%：多用 Grep + Read 行区间，少整文件读取/重复搜索结果。`)
  if (devShare > 45) suggestions.push(`开发占 ${devShare.toFixed(1)}%：可把大改动拆小、减少一次性重写整文件。`)
  if (cacheHit < 0.5) suggestions.push(`cache 命中率 ${(cacheHit * 100).toFixed(1)}%：上下文切换偏频繁，尽量在同一会话内连续完成同一任务。`)
  if (sidechainWeighted / (total.weighted || 1) > 0.2) suggestions.push('子代理占比偏高：只在独立调研/并行任务用 Agent，简单查找直接 Grep。')
  if (topCalls[0] && num(topCalls[0].cacheRead) > 200_000) suggestions.push('单次调用 cache_read 超过 20 万：可能有超大文件/长日志进入上下文，建议裁剪输出。')
  if (!suggestions.length) suggestions.push('暂无明显异常；继续观察测试/调研占比和 cache 命中率。')

  const lines = []
  lines.push('# Claude Code Token 用量报表')
  lines.push('')
  lines.push(`- 生成时间：${new Date().toISOString().replace('T', ' ').slice(0, 19)}`)
  lines.push(`- 统计范围：${days > 0 ? `最近 ${days} 天` : '全部历史'}`)
  lines.push(`- 数据源：\`~/.claude/projects/*menu-new*/**/*.jsonl\``)
  lines.push(`- 加权规则：input=${weights.input} / output=${weights.output} / cache_write=${weights.cacheWrite} / cache_read=${weights.cacheRead}（相对值，可在 \`pricing.json\` 覆盖）`)
  lines.push('')
  lines.push('## 总览')
  lines.push('')
  lines.push(`- assistant API 调用：${fmt(rows.length)} 次`)
  lines.push(`- 原始 token：${fmt(total.raw)}（input ${fmt(total.input)} / output ${fmt(total.output)} / cache_write ${fmt(total.cacheWrite)} / cache_read ${fmt(total.cacheRead)}）`)
  lines.push(`- 加权 token：${fmt(total.weighted)}`)
  lines.push(`- cache 命中率：${(cacheHit * 100).toFixed(1)}%`)
  lines.push('')
  lines.push('## 按阶段')
  lines.push('')
  lines.push('| 阶段 | 调用数 | 原始 token | 加权 token | 加权占比 |')
  lines.push('|---|---:|---:|---:|---:|')
  for (const [k, v] of categoryRows) {
    lines.push(`| ${k} | ${fmt(v.calls)} | ${fmt(v.raw)} | ${fmt(v.weighted)} | ${pct(v.weighted, total.weighted)} |`)
  }
  lines.push('')
  lines.push('## 按工具')
  lines.push('')
  lines.push('| 工具 | 加权 token | 占比 |')
  lines.push('|---|---:|---:|')
  for (const [k, v] of topEntries(byTool, 15)) {
    lines.push(`| ${k} | ${fmt(v)} | ${pct(v, total.weighted)} |`)
  }
  lines.push('')
  lines.push('## 按会话（Top 15）')
  lines.push('')
  lines.push('| 会话 | 时间范围 | 调用数 | 加权 token | 模型 |')
  lines.push('|---|---|---:|---:|---|')
  for (const [k, v] of sessionRows.slice(0, 15)) {
    const range = v.first && v.last ? `${String(v.first).slice(0, 16).replace('T', ' ')} ~ ${String(v.last).slice(0, 16).replace('T', ' ')}` : '—'
    lines.push(`| ${k.slice(0, 8)} | ${range} | ${fmt(v.calls)} | ${fmt(v.weighted)} | ${[...v.models].join(', ') || '—'} |`)
  }
  lines.push('')
  lines.push('## 按日期')
  lines.push('')
  lines.push('| 日期 | 加权 token | 占比 |')
  lines.push('|---|---:|---:|')
  for (const [k, v] of dayRows) lines.push(`| ${k} | ${fmt(v)} | ${pct(v, total.weighted)} |`)
  lines.push('')
  lines.push('## 最贵的 10 次调用')
  lines.push('')
  lines.push('| 时间 | 阶段 | 工具 | 模型 | 加权 token | input | output | cache_read |')
  lines.push('|---|---|---|---|---:|---:|---:|---:|')
  for (const r of topCalls) {
    lines.push(`| ${String(r.ts).slice(0, 16).replace('T', ' ')} | ${r.category} | ${(r.tools || []).join('/') || '—'} | ${r.model} | ${fmt(r.weighted)} | ${fmt(r.input)} | ${fmt(r.output)} | ${fmt(r.cacheRead)} |`)
  }
  lines.push('')
  lines.push('## 省 token 建议')
  lines.push('')
  for (const s of suggestions) lines.push(`- ${s}`)
  lines.push('')
  return lines.join('\n')
}

function writeReport(allRows, days) {
  fs.mkdirSync(OUT_DIR, { recursive: true })
  fs.writeFileSync(REPORT, buildReport(allRows, days))
}

async function readStdin() {
  if (process.stdin.isTTY) return null
  let data = ''
  try {
    for await (const chunk of process.stdin) data += chunk
  } catch {
    return null
  }
  if (!data.trim()) return null
  try {
    return JSON.parse(data)
  } catch {
    return null
  }
}

function parseArgs(argv) {
  const out = { days: 14, hook: false, reportOnly: false, quiet: false, rebuild: false, dirs: [] }
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i]
    if (a === '--hook') out.hook = true
    else if (a === '--report-only') out.reportOnly = true
    else if (a === '--quiet') out.quiet = true
    else if (a === '--rebuild') out.rebuild = true
    else if (a === '--days') out.days = Number(argv[++i] || 14)
    else if (a === '--project-dir') out.dirs.push(argv[++i])
  }
  return out
}

async function main() {
  const opts = parseArgs(process.argv.slice(2))
  const weights = loadWeights()
  const existing = readLedger()
  const existingKeys = new Set(existing.map((r) => r.key))

  let files = []
  if (opts.hook) {
    const input = await readStdin()
    const transcript = input?.transcript_path
    if (transcript && fs.existsSync(transcript)) {
      files.push(transcript)
      const sessionDir = transcript.replace(/\.jsonl$/, '')
      if (fs.existsSync(sessionDir) && fs.statSync(sessionDir).isDirectory()) walkJsonl(sessionDir, files)
    } else {
      for (const d of projectDirs(opts.dirs)) walkJsonl(d, files)
    }
  } else if (!opts.reportOnly) {
    for (const d of projectDirs(opts.dirs)) walkJsonl(d, files)
  }

  let added = 0
  if (files.length) {
    const rows = scanFiles(files, weights)
    const fresh = rows.filter((r) => !existingKeys.has(r.key))
    added = fresh.length
    if (opts.hook) {
      appendLedger(fresh)
    } else if (opts.rebuild) {
      // 忽略旧 ledger 的分类，按当前规则重新归类
      writeLedger(rows)
    } else {
      // 全量扫描时重写 ledger，顺手清掉历史重复行
      writeLedger(dedupeRows([...existing, ...fresh]))
    }
  }

  const allRows = readLedger()
  writeReport(allRows, opts.days)
  if (!opts.quiet && !opts.hook) {
    const total = allRows.reduce((a, r) => a + num(r.weighted), 0)
    process.stdout.write(
      `token-usage: ${opts.reportOnly ? 'reused' : 'scanned'} ${files.length} file(s), +${added} row(s), ` +
        `ledger ${allRows.length} row(s), weighted ${fmt(total)}\n` +
        `report: ${path.relative(REPO_ROOT, REPORT)}\n`,
    )
  }
}

main().catch((e) => {
  // hook 模式绝不阻塞会话：错误写入本地日志后退出 0
  try {
    fs.mkdirSync(OUT_DIR, { recursive: true })
    fs.appendFileSync(ERROR_LOG, `[${new Date().toISOString()}] ${e?.stack || e}\n`)
  } catch {
    // ignore
  }
  if (!process.argv.includes('--hook')) process.exitCode = 1
})
