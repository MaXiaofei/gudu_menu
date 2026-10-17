// 纯函数：用量解析、食材名归一、难度/时长推断。无 IO，单测见 ../parse.test.mjs

const CN_DIGIT = { 零: 0, 一: 1, 二: 2, 两: 2, 三: 3, 四: 4, 五: 5, 六: 6, 七: 7, 八: 8, 九: 9, 十: 10, 半: 0.5 };

/** 「一」「两」「十二」「三个半」里的数字部分 → number；不认识返回 null */
export function cnNumber(s) {
  if (!s) return null;
  if (s === '半') return 0.5;
  let m = s.match(/^([一二两三四五六七八九]?)十([一二三四五六七八九]?)$/);
  if (m) return (m[1] ? CN_DIGIT[m[1]] : 1) * 10 + (m[2] ? CN_DIGIT[m[2]] : 0);
  if (s.length === 1 && s in CN_DIGIT) return CN_DIGIT[s];
  return null;
}

// 不定量词：amount=0，单位用字典里同名词条（如「适量」「少许」）
const VAGUE = ['适量', '少许', '少量', '若干', '一点点', '一点', '一些', '些许', '酌量', '随意', '按喜好', '按口味'];

/** 单位同义词 → 字典单位名（字典里不存在的写法统一到存在的写法） */
export const UNIT_SYNONYMS = {
  公斤: '千克', kg: '千克', KG: '千克', Kg: '千克', G: 'g', 克: '克', ML: 'ml', mL: 'ml', Ml: 'ml', L: '升', l: '升',
  茶勺: '小勺', 调羹: '汤匙', 羹匙: '汤匙', 汤勺: '汤勺', 大汤匙: '大匙', 小汤匙: '小匙',
  枚: '个', 粒儿: '粒', 支: '根', 只: '只', 尾: '尾', 坨: '块', 团: '块', 朵: '朵',
};

/**
 * 用量文本 → { amount, unit }。unit 为原文单位词（已做同义归一），amount 为数字（不定量为 0，无法解析为 null）。
 * 支持：500克 / 1/2小匙 / 2-3根（取上限）/ 2~3 / 1个半 / 半个 / 三汤匙 / 一汤匙半 / 适量 / 少许
 */
export function parseAmount(raw, unitNames = null) {
  if (raw == null) return { amount: null, unit: null };
  let t = String(raw).trim().replace(/\s+/g, '').replace(/[（(].*?[)）]/g, '');
  if (!t) return { amount: null, unit: null };
  for (const v of VAGUE) if (t.startsWith(v)) return { amount: 0, unit: v === '一些' || v === '些许' || v === '酌量' || v === '随意' || v.startsWith('按') ? '适量' : v };

  let amount = null; let rest = t;
  // 阿拉伯数字：分数 / 区间 / 小数
  let m = t.match(/^(\d+(?:\.\d+)?)\s*\/\s*(\d+(?:\.\d+)?)/);
  if (m) { amount = Number(m[1]) / Number(m[2]); rest = t.slice(m[0].length); }
  if (amount == null) {
    m = t.match(/^(\d+(?:\.\d+)?)\s*[-~～—至到]\s*(\d+(?:\.\d+)?)/);
    if (m) { amount = Number(m[2]); rest = t.slice(m[0].length); }
  }
  if (amount == null) {
    m = t.match(/^(\d+(?:\.\d+)?)/);
    if (m) { amount = Number(m[1]); rest = t.slice(m[0].length); }
  }
  // 中文数字：两/三/十二/半
  if (amount == null) {
    m = t.match(/^([一二两三四五六七八九十]{1,3}|半)/);
    if (m) {
      const n = cnNumber(m[1]);
      if (n != null) { amount = n; rest = t.slice(m[0].length); }
    }
  }
  if (amount == null) return { amount: null, unit: null };

  // 「个半」「汤匙半」：单位后跟「半」= +0.5
  let half = false;
  if (rest.endsWith('半') && rest.length > 1) { half = true; rest = rest.slice(0, -1); }
  if (rest.startsWith('半') && rest.length > 1) { half = true; rest = rest.slice(1); }
  if (half) amount += 0.5;
  // 「2根左右」「约 3 片」
  rest = rest.replace(/^(多|余|来)/, '').replace(/(左右|上下|多|以上|即可)$/, '');
  let unit = rest || null;
  if (unit && UNIT_SYNONYMS[unit]) unit = UNIT_SYNONYMS[unit];
  if (unit && unitNames && !unitNames.has(unit)) {
    // 取最长的已知单位前缀（如「片切薄」→「片」）
    const known = [...unitNames].filter((u) => unit.startsWith(u)).sort((a, b) => b.length - a.length)[0];
    unit = known || unit;
  }
  return { amount: Math.round(amount * 100) / 100, unit };
}

/** 质量单位换算成克；其他单位返回 null */
export function toGrams(amount, unit) {
  if (amount == null || !unit) return null;
  const f = { 克: 1, g: 1, 千克: 1000, 斤: 500, 两: 50 }[unit];
  return f ? Math.round(amount * f * 100) / 100 : null;
}

// ---------- 食材名 ----------

const WATER = new Set(['水', '清水', '开水', '温水', '凉水', '冷水', '冰水', '热水', '纯净水', '矿泉水', '凉白开', '高汤', '清汤', '炸油']);

/** 原文食材名 → 去括号、去空白 */
export function cleanName(raw) {
  return String(raw || '').trim().replace(/\s+/g, '').replace(/[（(].*?[)）]/g, '')
    .replace(/^\d+[.、．]?/, '').replace(/[:：,，、。.]+$/g, '');
}

/** 小标题行（如「腌制鸡肉材料:1～6」）不是食材 */
export function isHeading(raw) {
  return /[:：]/.test(String(raw || '')) || /材料|配料|调料汁$|腌料$/.test(cleanName(raw));
}

const SEP = /[、，,+＋&]|和|及/;

export function isWater(name) {
  const n = cleanName(name);
  return WATER.has(n) || /^(高汤|清汤|骨汤|肉骨汤|鸡汤)/.test(n) && n.length <= 4;
}

const SINGLE_OK = new Set(['葱', '姜', '蒜', '盐', '糖', '醋']);
const SUFFIX = /(末|片|段|丝|花|碎|粒|丁|蓉|泥|块|条|头|白|叶|粉|汁|面|圈)$/;
const PREFIX = /^(新鲜|鲜|中等|大个|小个|大|小|嫩|熟|生|干|冷冻|冰冻|速冻|去皮|带皮|有机|优质|普通|老|细)/;

/**
 * 原文名 → 候选名列表（按优先级）。combos 用于「葱姜蒜」类拆分。
 * 返回 [[候选1, 候选2...], ...]：外层每项是拆分后的一个食材。
 */
export function nameCandidates(raw, combos = {}) {
  const n = cleanName(raw);
  if (!n) return [];
  const parts = (combos[n] || n.split(SEP)).map((x) => x.replace(/^\d+[.、．]?/, '')).filter(Boolean)
    .flatMap((x) => combos[x] || [x]);
  return parts.map((p) => {
    const out = [p];
    // 去前后缀后只剩 1 个字的，只接受常见单字调料（「油面」不能变「油」）
    const push = (x) => { if (x && (x.length >= 2 || SINGLE_OK.has(x)) && !out.includes(x)) out.push(x); };
    const noPre = p.replace(PREFIX, '');
    push(noPre);
    push(p.replace(SUFFIX, ''));
    push(noPre.replace(SUFFIX, ''));
    return out;
  });
}

/**
 * 解析一个候选名到食材 id。
 * index: { exact: Map(name→id), base: Map(去括号名→id), alias: Map(别名→标准名), names: [按长度降序的 [name,id]] }
 */
export function resolveIngredient(cands, index) {
  const hit = (n) => {
    if (index.alias.has(n)) n = index.alias.get(n);
    return index.exact.get(n) ?? index.base.get(n) ?? null;
  };
  for (const c of cands) { const id = hit(c); if (id != null) return { id, via: c === cands[0] ? 'exact' : 'normalized' }; }
  // 包含匹配：原文包含某个已知名（≥2 字），取最长。
  // 「x酱 / x油」是调味品，不能退化成原料（辣椒酱≠红辣椒、花椒油≠花椒）
  const src = cands[0];
  const condiment = /[酱油]$/.test(src);
  const ok = (name) => name.length >= 2 && src.includes(name) && (!condiment || /[酱油]$/.test(name.replace(/[（(].*?[)）]/g, '')));
  for (const [name, id] of index.names) {
    if (ok(name)) return { id, via: 'contains' };
  }
  for (const [alias, std] of index.aliasByLen) {
    if (ok(alias)) { const id = hit(std); if (id != null) return { id, via: 'contains' }; }
  }
  return null;
}

export function buildIndex(ingredients, aliasMap) {
  const exact = new Map(); const base = new Map();
  for (const i of ingredients) {
    exact.set(i.name, i.id);
    const b = i.name.replace(/[（(].*?[)）]/g, '');
    if (!base.has(b)) base.set(b, i.id);
  }
  // 别名表可显式指定带括号的标准名（如 牛肉→牛肉(瘦)），优先于 base 首个
  const alias = new Map(Object.entries(aliasMap));
  const names = [...exact.entries(), ...[...base.entries()].filter(([b]) => !exact.has(b))]
    .sort((a, b) => b[0].length - a[0].length);
  const aliasByLen = [...alias.entries()].sort((a, b) => b[0].length - a[0].length);
  return { exact, base, alias, names, aliasByLen };
}

// ---------- 难度 / 时长 ----------

const HARD_WORDS = ['发酵', '打发', '揉面', '擀', '醒面', '烤箱', '裱花', '油炸', '炸至', '熬制', '卤', '腌制一晚', '隔夜', '过筛', '模具'];
const TIME_RE = /(\d+(?:\.\d+)?)\s*(?:[-~～到至]\s*(\d+(?:\.\d+)?))?\s*(分钟|分|小时|个小时|h)/g;
const PREP_WORDS = ['腌', '泡', '浸', '解冻', '醒', '发酵', '冷藏', '冷冻'];

/** 由步骤文本推断 { difficulty 1..5, prepTime, cookTime }（分钟，取整到 5） */
export function inferEffort(steps, ingredientCount) {
  const texts = steps.map((s) => (typeof s === 'string' ? s : s.text || ''));
  let prep = 0; let cook = 0;
  for (const t of texts) {
    // 一句里的时间按动作归 prep 或 cook
    for (const m of t.matchAll(TIME_RE)) {
      let v = Number(m[2] || m[1]);
      if (/小时|h/.test(m[3])) v *= 60;
      if (v > 24 * 60) continue;
      const before = t.slice(Math.max(0, m.index - 8), m.index);
      if (PREP_WORDS.some((w) => before.includes(w))) prep += v; else cook += v;
    }
  }
  const stepCount = texts.length;
  const prepTime = round5(Math.min(prep || 5 + stepCount * 2, 720));
  const cookTime = round5(Math.min(cook || 5 + stepCount * 3, 480));
  const all = texts.join('');
  let score = 1;
  if (stepCount > 5) score++;
  if (stepCount > 9) score++;
  if (ingredientCount > 10) score++;
  if (HARD_WORDS.some((w) => all.includes(w))) score++;
  if (prepTime + cookTime > 120) score++;
  return { difficulty: Math.min(5, score), prepTime, cookTime };
}

function round5(v) { return Math.max(5, Math.round(v / 5) * 5); }
