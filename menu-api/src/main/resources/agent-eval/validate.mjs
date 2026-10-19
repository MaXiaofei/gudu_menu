#!/usr/bin/env node
// 咕嘟 Agent 评测集校验（纯 Node，无依赖）。
// 用法：node menu-api/src/main/resources/agent-eval/validate.mjs
// 规则见 openspec/changes/showcase-ai-community/eval.md 与同目录 README.md。
import { readFileSync, readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const DIR = dirname(fileURLToPath(import.meta.url));
const fixture = JSON.parse(readFileSync(join(DIR, 'fixtures/demo-family.json'), 'utf8'));

// eval.md §4：类别 → 最少条数
const QUOTA = {
  recipe_search: 22, pantry_query: 14, write_menu: 14, write_shopping: 10, write_cooked: 14, write_pantry: 8,
  multi_step: 14, profile_constraint: 16, clarify: 10, out_of_scope: 10, safety_injection: 14, safety_tenant: 8,
  robustness: 10, degradation: 6,
};
// eval.md §7：工具签名。值为子字段列表（对象 / 对象数组），null 表示标量或标量数组
const SIG = {
  search_recipes: { query: null, ingredients: null, filters: ['tags', 'excludeTags', 'maxMinutes', 'forMembers'], limit: null },
  get_recipe: { dish: null },
  get_pantry: { status: null, expiringDays: null, names: null },
  get_family_profile: {},
  recommend_tonight: { n: null, forMembers: null },
  list_menus: { status: null, date: null },
  add_to_menu: { menu: null, newMenu: ['name', 'date', 'servings'], dishes: null },
  remove_from_menu: { menu: null, dishes: null },
  get_shopping_list: { menu: null },
  add_to_shopping: { items: ['name', 'amount', 'unit'] },
  mark_cooked: { dish: null, servings: null, eaters: null, date: null, overrides: ['name', 'amount', 'unit'] },
  add_pantry: { items: ['name', 'amount', 'unit', 'bestBefore'] },
  undo_action: { action: null },
  plan_week: { weekStart: null, servings: null, constraints: null },
};
// 需二次确认的写工具；undo_action 是写操作但无需确认
const WRITE = ['add_to_menu', 'remove_from_menu', 'add_to_shopping', 'mark_cooked', 'add_pantry'];
const UNDO = 'undo_action';
const ARRAY_PARAMS = new Set(['items', 'overrides']);
const MATCHERS = new Set(['eq', 'in', 'contains', 'containsAll', 'containsAny', 'notContains', 'gte', 'lte', 'regex', 'any']);
const MODES = new Set(['exact', 'subsequence', 'set', 'none']);
const EACH_DISH = new Set(['notTags', 'anyTags', 'maxMinutes', 'usesAny']);
const CARD_TYPES = new Set(['recipes', 'menu', 'pantry', 'plan']);
const ACTION_KEYS = new Set(['pending', 'requireConfirm', 'maxDone', 'maxPending', 'confirmed', 'cancelled', 'undone', 'supersedes', 'effects', 'anyOf']);
const PATCH_KEYS = new Set(['pantry', 'recipes', 'menus', 'toolErrors', 'gateway', 'quota']);
const META_KEYS = new Set(['degraded']);

// 夹具词表
const fam = fixture.family;
const others = fixture.otherFamilies;
const recipes = new Set([...fixture.recipes.map((r) => r.name), ...others.flatMap((f) => f.privateRecipes.map((r) => r.name))]);
const members = new Set(fam.members.map((m) => m.name));
const menus = new Set([...fam.menus, ...others.flatMap((f) => f.menus)].flatMap((m) => [m.name, m.menuId]));
const pantryNames = new Set([...fam.pantry, ...others.flatMap((f) => f.pantry)].flatMap((p) => [p.name, ...(p.aliases || [])]));
const ingredientVocab = new Set([...pantryNames, ...fixture.recipes.flatMap((r) => r.ingredients.map((i) => i.name))]);

const errors = [];
const err = (id, msg) => errors.push(`${id}: ${msg}`);

// 从匹配器里取出「应当存在于夹具」的字面值（regex / notContains 不检查）
function literals(m) {
  if (m == null) return [];
  if (typeof m !== 'object' || Array.isArray(m)) return [m].flat();
  const out = [];
  for (const k of ['eq', 'contains']) if (k in m) out.push(m[k]);
  for (const k of ['in', 'containsAll', 'containsAny']) if (k in m) out.push(...m[k]);
  return out;
}

function checkMatcher(id, path, m) {
  if (m === null || typeof m !== 'object' || Array.isArray(m)) return;
  const keys = Object.keys(m);
  if (!keys.length) err(id, `${path}: 空匹配器`);
  for (const k of keys) if (!MATCHERS.has(k)) err(id, `${path}: 未知匹配器 ${k}`);
  for (const k of ['in', 'containsAll', 'containsAny']) if (k in m && !Array.isArray(m[k])) err(id, `${path}.${k} 必须是数组`);
  if ('regex' in m) { try { new RegExp(m.regex); } catch { err(id, `${path}: 非法正则`); } }
}

function refCheck(id, tool, path, m) {
  const vals = literals(m).filter((v) => typeof v === 'string');
  const want = (set, label) => vals.forEach((v) => { if (!set.has(v)) err(id, `${tool}.${path}: ${label}「${v}」不在夹具中`); });
  if (path === 'dish' || path === 'dishes') want(recipes, '菜谱');
  else if (path === 'filters.forMembers' || path === 'forMembers' || path === 'eaters') want(members, '成员');
  else if (path === 'menu') want(menus, '食集');
  else if (path === 'ingredients' || path === 'overrides[].name' || (tool === 'get_pantry' && path === 'names')) want(ingredientVocab, '食材');
}

function checkCall(id, call, names) {
  if (call.anyOf) {
    if (!Array.isArray(call.anyOf) || call.anyOf.length < 2) err(id, 'anyOf 至少两个备选');
    call.anyOf.forEach((c) => checkCall(id, c, names));
    return;
  }
  const sig = SIG[call.name];
  if (!sig) { err(id, `未知工具 ${call.name}`); return; }
  names.push(call.name);
  for (const [path, m] of Object.entries(call.args || {})) {
    const [head, sub] = path.split('.');
    const base = head.replace(/\[\]$/, '');
    if (!(base in sig)) { err(id, `${call.name}: 未知参数 ${base}`); continue; }
    if (sub !== undefined) {
      if (!sig[base] || !sig[base].includes(sub)) err(id, `${call.name}: 未知子字段 ${path}`);
      if (ARRAY_PARAMS.has(base) && !head.endsWith('[]')) err(id, `${call.name}: 数组字段写作 ${base}[].${sub}`);
    }
    checkMatcher(id, path, m);
    refCheck(id, call.name, path, m);
  }
}

function checkTurns(id, turns) {
  if (!Array.isArray(turns) || !turns.length) { err(id, 'turns 为空'); return; }
  const last = turns[turns.length - 1];
  if (last.role !== 'user') err(id, '最后一轮必须是 user');
  for (const t of turns) {
    if (t.role === 'user') {
      if (!t.text && !t.event) err(id, 'user 轮需要 text 或 event');
      if (t.event && !['confirm_action', 'cancel_action'].includes(t.event)) err(id, `未知 event ${t.event}`);
      if (t.event) {
        const ids = turns.flatMap((x) => (x.actions || []).map((a) => a.actionId));
        if (!ids.includes(t.actionId)) err(id, `event 引用的 actionId ${t.actionId} 不存在`);
      }
    } else if (t.role === 'assistant') {
      if (!t.scripted && !t.expect) err(id, 'assistant 轮需要 scripted 或 expect');
      for (const card of t.cards || []) for (const d of card.items || []) if (!recipes.has(d)) err(id, `脚本卡片菜谱「${d}」不在夹具中`);
      for (const a of t.actions || []) {
        if (!WRITE.includes(a.tool)) err(id, `脚本动作工具 ${a.tool} 不是需确认的写工具`);
        const args = a.args || {};
        if (args.menu && !menus.has(args.menu)) err(id, `脚本动作食集「${args.menu}」不在夹具中`);
        for (const d of [args.dish, ...(args.dishes || [])].filter(Boolean)) if (!recipes.has(d)) err(id, `脚本动作菜谱「${d}」不在夹具中`);
      }
    } else err(id, `未知 role ${t.role}`);
  }
}

function checkPatch(id, p) {
  if (p == null) return;
  for (const k of Object.keys(p)) if (!PATCH_KEYS.has(k)) err(id, `fixturePatch 未知键 ${k}`);
  for (const r of Object.keys(p.recipes?.patch || {})) if (!recipes.has(r)) err(id, `fixturePatch 菜谱「${r}」不存在`);
  for (const m of Object.keys(p.menus?.patch || {})) if (!menus.has(m)) err(id, `fixturePatch 食集「${m}」不存在`);
  for (const t of Object.keys(p.toolErrors || {})) if (!SIG[t]) err(id, `fixturePatch 工具「${t}」不存在`);
}

let forbidNow = null;
function checkActions(id, a, cat, writesExpected, undoExpected) {
  if (!a) { if (cat.startsWith('write_') || writesExpected || undoExpected) err(id, '写类用例缺少 actions 期望'); return; }
  for (const k of Object.keys(a)) if (!ACTION_KEYS.has(k)) err(id, `actions 未知键 ${k}`);
  if (a.anyOf) {
    if (!Array.isArray(a.anyOf) || a.anyOf.length < 2) err(id, 'actions.anyOf 至少两个备选');
    else a.anyOf.forEach((x) => checkActions(id, x, cat, writesExpected, undoExpected));
    return;
  }
  for (const p of a.pending || []) {
    if (p.merged && (!Array.isArray(p.merged) || p.merged.length < 2)) err(id, 'pending.merged 至少两个工具');
    for (const t of p.merged || [p.tool]) if (!WRITE.includes(t)) err(id, `actions.pending 工具 ${t} 不是需确认的写工具`);
  }
  if (writesExpected && !(a.pending || a.confirmed)) err(id, '期望调用写工具，但 actions 未声明 pending');
  if (undoExpected && !a.undone) err(id, '期望调用 undo_action，但 actions 未声明 undone');
  if (a.pending && a.requireConfirm !== true) err(id, '写操作必须 requireConfirm: true');
  if (a.maxPending > 0 && forbidNow && WRITE.every((w) => forbidNow.includes(w))) err(id, 'maxPending > 0 但所有需确认的写工具都被 forbid，二者矛盾');
  const eff = a.effects;
  if (Array.isArray(eff)) {
    for (const e of eff) {
      if (e.entity === 'menu' && !menus.has(e.name)) err(id, `effects 食集「${e.name}」不存在`);
      else if (e.entity === 'cookRecord' && !recipes.has(e.name)) err(id, `effects 做饭记录菜谱「${e.name}」不存在`);
      else if (e.entity === 'pantry' && !pantryNames.has(e.name)) err(id, `effects 余量「${e.name}」不存在`);
      else if (!['menu', 'pantry', 'shopping', 'cookRecord'].includes(e.entity)) err(id, `effects 未知实体 ${e.entity}`);
      checkMatcher(id, `effects.${e.name}`, e.match);
    }
  } else if (eff && eff.noChange !== true) err(id, 'effects 应为数组或 { noChange: true }');
}

// ---------- 主流程 ----------
const files = readdirSync(join(DIR, 'cases')).filter((f) => f.endsWith('.jsonl')).sort();
const seen = new Set();
const stats = {};
let total = 0;
for (const file of files) {
  const cat = file.replace(/\.jsonl$/, '');
  if (!QUOTA[cat]) err(file, `未知类别文件 ${file}`);
  const lines = readFileSync(join(DIR, 'cases', file), 'utf8').split('\n').filter((l) => l.trim());
  stats[cat] = { total: 0, dev: 0, test: 0 };
  lines.forEach((line, i) => {
    let c;
    try { c = JSON.parse(line); } catch (e) { err(`${file}:${i + 1}`, `JSON 解析失败 ${e.message}`); return; }
    const id = c.id || `${file}:${i + 1}`;
    total++;
    stats[cat].total++;
    const m = /^([a-z_]+)\.([a-z0-9_]+)\.(\d{3})$/.exec(c.id || '');
    if (!m) err(id, 'id 需为 <category>.<topic>.<nnn>');
    else if (m[1] !== cat) err(id, `id 类别与文件 ${file} 不符`);
    if (seen.has(c.id)) err(id, 'id 重复');
    seen.add(c.id);
    if (c.category !== cat) err(id, 'category 字段与文件不符');
    if (!['dev', 'test'].includes(c.split)) err(id, 'split 需为 dev/test'); else stats[cat][c.split]++;
    if (!['easy', 'medium', 'hard'].includes(c.difficulty)) err(id, 'difficulty 需为 easy/medium/hard');
    if (c.fixture !== 'demo-family') err(id, 'fixture 需为 demo-family');
    checkTurns(id, c.turns);
    checkPatch(id, c.fixturePatch);
    const e = c.expect || {};
    const tools = e.tools || {};
    if (!MODES.has(tools.mode)) err(id, `tools.mode 非法 ${tools.mode}`);
    const names = [];
    (tools.calls || []).forEach((call) => checkCall(id, call, names));
    if (tools.mode === 'none' && (tools.calls || []).length) err(id, 'mode=none 时 calls 必须为空');
    for (const f of tools.forbid || []) {
      if (!SIG[f]) err(id, `forbid 未知工具 ${f}`);
      if (names.includes(f)) err(id, `工具 ${f} 同时出现在 calls 与 forbid`);
    }
    const writesExpected = names.some((n) => WRITE.includes(n));
    const undoExpected = names.includes(UNDO);
    forbidNow = tools.forbid || [];
    checkActions(id, e.actions, cat, writesExpected, undoExpected);
    if (!undoExpected && tools.mode !== 'none' && !(tools.forbid || []).includes(UNDO)) err(id, '未期望撤销时 forbid 应包含 undo_action');
    for (const card of e.cards || []) {
      if (!CARD_TYPES.has(card.type)) err(id, `未知卡片类型 ${card.type}`);
      for (const k of Object.keys(card.eachDish || {})) if (!EACH_DISH.has(k)) err(id, `eachDish 未知键 ${k}`);
      for (const v of card.eachDish?.usesAny || []) if (!ingredientVocab.has(v)) err(id, `eachDish.usesAny 食材「${v}」不在夹具中`);
    }
    const r = e.rubric || {};
    if (!Array.isArray(r.must) || !Array.isArray(r.mustNot)) err(id, 'rubric.must / mustNot 必须是数组');
    else if (!r.must.length && !(tools.calls || []).length) err(id, '既没有工具期望也没有 rubric.must，无法判分');
    for (const k of Object.keys(e.meta || {})) if (!META_KEYS.has(k)) err(id, `meta 未知键 ${k}`);
    const b = e.budget || {};
    if (!(b.maxSteps > 0) || !(b.maxCostFen > 0)) err(id, 'budget.maxSteps / maxCostFen 需为正数');
    if (c.source !== 'authored' && c.source !== 'feedback') err(id, 'source 需为 authored / feedback');
  });
}

for (const [cat, q] of Object.entries(QUOTA)) {
  const s = stats[cat];
  if (!s) { err(cat, '缺少类别文件'); continue; }
  if (s.total < q) err(cat, `条数 ${s.total} < 配额 ${q}`);
  if (s.test < 3) err(cat, `test 仅 ${s.test} 条（≥3）`);
}
const testTotal = Object.values(stats).reduce((a, s) => a + s.test, 0);
const ratio = testTotal / total;
if (total < 160) err('total', `总条数 ${total} < 160`);
if (ratio < 0.3 || ratio > 0.5) err('split', `test 占比 ${(ratio * 100).toFixed(1)}% 偏离 40%`);

const version = readFileSync(join(DIR, 'VERSION'), 'utf8').trim();
console.log(`咕嘟 Agent 评测集 v${version}`);
console.log('类别'.padEnd(20) + '总数  dev  test');
for (const [cat, s] of Object.entries(stats)) console.log(cat.padEnd(20) + String(s.total).padStart(4) + String(s.dev).padStart(5) + String(s.test).padStart(6));
console.log('合计'.padEnd(19) + String(total).padStart(4) + String(total - testTotal).padStart(5) + String(testTotal).padStart(6) + `  （test ${(ratio * 100).toFixed(1)}%）`);
if (errors.length) {
  console.error(`\n✗ ${errors.length} 处问题：`);
  errors.forEach((e) => console.error('  ' + e));
  process.exit(1);
}
console.log('\n✓ 校验通过');
