#!/usr/bin/env node
// 公共菜谱种子（下厨房导入数据 · 仅 staging / local）。
//
//   node recipes.mjs --env=staging --dry-run        # 只算不写，输出报告
//   node recipes.mjs --env=staging                  # upsert（source_url 自然键，dish.id 不变）
//   可选：--limit=N  --only=<source_url>  --source=<progress.json 路径>
//
// 规则（openspec/changes/recipe-content-seed）：
//   - 子表 dish_step / dish_ingredient 按菜局部重建；dish_dict 只 INSERT IGNORE 映射项，不删已有关联
//   - 分类只映射到现有字典（mappings/category.json），不新建 sys_dict 词条
//   - 食材按 原名 → 别名 → 去前后缀 → 包含 匹配；清水类不计入；只补 mappings/extra-ingredients.json 列出的基础食材
//   - 图片 URL = SEED_IMAGE_BASE/dish/original/xc_<id>.jpg（缩略图同名在 /thumbnail/，由 images.py 生成、sync-images.mjs 上传）
import { readFileSync, writeFileSync, existsSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';
import { createHash } from 'node:crypto';
import { connect, parseEnvArg } from './lib/db.mjs';
import { ROOT, readEnv } from './lib/env.mjs';
import { parseAmount, toGrams, nameCandidates, resolveIngredient, buildIndex, isWater, isHeading, cleanName, inferEffort } from './lib/parse.mjs';

const argv = process.argv.slice(2);
const opt = (k) => { const a = argv.find((x) => x.startsWith(`--${k}=`)); return a ? a.slice(k.length + 3) : null; };
const target = parseEnvArg(argv);
const DRY = argv.includes('--dry-run');
const LIMIT = Number(opt('limit') || 0);
const ONLY = opt('only');
const SOURCE = opt('source') || join(ROOT, 'xiachufang/scraper/data/progress.json');
const IMG_SRC = join(ROOT, 'xiachufang/scraper/images');
const SEED = join(ROOT, 'scripts/seed');
const SOURCE_NAME = '下厨房';

const env = readEnv();
const IMAGE_BASE = (env.SEED_IMAGE_BASE || 'https://staging.imxf.cloud/images').replace(/\/$/, '');
const catMap = JSON.parse(readFileSync(join(SEED, 'mappings/category.json'), 'utf8')).map;
const { alias, combos } = JSON.parse(readFileSync(join(SEED, 'mappings/ingredient-alias.json'), 'utf8'));
const extras = JSON.parse(readFileSync(join(SEED, 'mappings/extra-ingredients.json'), 'utf8')).items;

if (!existsSync(SOURCE)) { console.error(`源数据不存在：${SOURCE}`); process.exit(2); }
const progress = JSON.parse(readFileSync(SOURCE, 'utf8'));
let recipes = Object.values(progress.recipes).filter((r) => r && typeof r === 'object' && r.data);
const urlOf = (r) => `https://www.xiachufang.com/recipe/${r.id}/`;
if (ONLY) recipes = recipes.filter((r) => urlOf(r) === ONLY);
if (LIMIT) recipes = recipes.slice(0, LIMIT);

// 下厨房「图片缺失」占位图（灰底 + 图片图标）的 md5：视为没有图片，避免占位图被当成封面推荐
const PLACEHOLDER_MD5 = new Set(['c6beb1700e07e28e64218a366cb64711']);
const isPlaceholder = (file) => PLACEHOLDER_MD5.has(createHash('md5').update(readFileSync(file)).digest('hex'));
const localImage = (key) => ['jpg', 'png', 'jpeg'].some((e) => {
  const f = join(IMG_SRC, `${key}.${e}`);
  return existsSync(f) && !isPlaceholder(f);
});
const imageUrl = (key) => `${IMAGE_BASE}/dish/original/${key}.jpg`;

const { conn, close, describe } = await connect(target);
const report = {
  target: describe, dryRun: DRY, at: new Date().toISOString(), recipes: recipes.length,
  inserted: 0, updated: 0, unchanged: 0, extraIngredientsAdded: 0,
  lines: { total: 0, water: 0, /* 含小标题行 */ counted: 0, matched: 0, via: {} }, dishLinkRate: { full: 0, ge80: 0, lt50: 0 },
  covers: 0, stepImages: 0, steps: 0, dictLinks: 0, unmappedCategories: {}, unmatchedTop: [],
};
const unmatched = new Map();

try {
  const [[{ v }]] = await conn.query('SELECT VERSION() v');
  console.log(`目标：${describe}（MySQL ${v}）${DRY ? '【dry-run，不写库】' : ''}；菜谱 ${recipes.length} 道`);

  const [cats] = await conn.query("SELECT id, name FROM sys_dict WHERE dict_group='purchase_category'");
  const catId = new Map(cats.map((c) => [c.name, c.id]));
  // 补基础食材（按名幂等）
  let [ings] = await conn.query('SELECT id, name FROM ingredient WHERE deleted=0');
  const have = new Set(ings.map((i) => i.name));
  let fakeId = -1;
  for (const e of extras) {
    if (have.has(e.name)) continue;
    if (DRY) { ings.push({ id: fakeId--, name: e.name }); report.extraIngredientsAdded++; continue; }
    await conn.query('INSERT INTO ingredient(name, purchase_category_id, purchase_count, usage_count, deleted) VALUES (?, ?, 0, 0, 0)',
      [e.name, catId.get(e.purchaseCategory) ?? null]);
    report.extraIngredientsAdded++;
  }
  if (!DRY && report.extraIngredientsAdded) [ings] = await conn.query('SELECT id, name FROM ingredient WHERE deleted=0');
  const missingStd = [...new Set(Object.values(alias))].filter((s) => !ings.some((i) => i.name === s));
  if (missingStd.length) console.warn(`⚠️  别名指向的标准名不在食材表：${missingStd.join('、')}`);
  const index = buildIndex(ings, alias);

  const [units] = await conn.query("SELECT id, name FROM sys_dict WHERE dict_group='unit'");
  const unitId = new Map(units.map((u) => [u.name, u.id]));
  const unitNames = new Set(unitId.keys());
  const [dicts] = await conn.query("SELECT id, dict_group, name FROM sys_dict WHERE dict_group IN ('tag','cuisine')");
  const dictId = new Map(dicts.map((d) => [`${d.dict_group}:${d.name}`, d.id]));

  for (const r of recipes) {
    const d = r.data;
    const key = `xc_${r.id}`;
    const sourceUrl = urlOf(r);

    // 用料
    const rows = []; const seen = new Set(); const noteItems = [];
    for (const it of d.ingredients || []) {
      const name = cleanName(it.name);
      if (!name) continue;
      noteItems.push(`${name}${it.amount ? ` ${String(it.amount).trim()}` : ''}`);
      report.lines.total++;
      if (isWater(name) || isHeading(it.name)) { report.lines.water++; continue; }
      report.lines.counted++;
      const parsed = parseAmount(it.amount, unitNames);
      let anyHit = false;
      for (const cands of nameCandidates(name, combos)) {
        const hit = resolveIngredient(cands, index);
        if (!hit) continue;
        anyHit = true;
        report.lines.via[hit.via] = (report.lines.via[hit.via] || 0) + 1;
        if (seen.has(hit.id)) continue;
        seen.add(hit.id);
        const uid = parsed.unit ? unitId.get(parsed.unit) ?? null : null;
        rows.push([hit.id, parsed.amount ?? 0, uid, toGrams(parsed.amount, parsed.unit)]);
      }
      if (anyHit) report.lines.matched++; else unmatched.set(name, (unmatched.get(name) || 0) + 1);
    }
    const counted = (d.ingredients || []).filter((i) => cleanName(i.name) && !isWater(i.name) && !isHeading(i.name)).length;
    const rate = counted ? rows.length / counted : 1;
    if (rate >= 1) report.dishLinkRate.full++;
    if (rate >= 0.8) report.dishLinkRate.ge80++;
    if (rate < 0.5) report.dishLinkRate.lt50++;

    // 字典
    const dictIds = [];
    for (const c of r.cats || []) {
      const m = catMap[c];
      const id = m ? dictId.get(`${m[0]}:${m[1]}`) : null;
      if (id) { if (!dictIds.some((x) => x[0] === id)) dictIds.push([id, m[0]]); } else report.unmappedCategories[c] = (report.unmappedCategories[c] || 0) + 1;
    }

    const steps = (d.steps || []).map((s, i) => ({
      text: String(s.text || '').slice(0, 1000),
      image: localImage(`${key}_s${i}`) ? imageUrl(`${key}_s${i}`) : null,
    }));
    const cover = localImage(key) ? imageUrl(key) : null;
    const effort = inferEffort(steps.map((s) => s.text), (d.ingredients || []).length);
    const noteBits = [];
    if (d.tips) noteBits.push(`小贴士：${d.tips}`);
    noteBits.push(`来源：${SOURCE_NAME}`);
    const note = `${noteBits.join(' | ')} | 用料：${noteItems.join('、')}`.slice(0, 512);
    report.covers += cover ? 1 : 0;
    report.steps += steps.length;
    report.stepImages += steps.filter((s) => s.image).length;
    report.dictLinks += dictIds.length;

    if (DRY) continue;
    await conn.beginTransaction();
    try {
      const [[existing]] = await conn.query('SELECT id, name, note, cover_url, prep_time, cook_time, difficulty FROM dish WHERE source_url = ? ORDER BY id LIMIT 1', [sourceUrl]);
      let dishId;
      const fields = [d.name, note, cover, effort.prepTime, effort.cookTime, effort.difficulty];
      if (existing) {
        dishId = existing.id;
        const same = existing.name === d.name && existing.note === note && existing.cover_url === cover
          && existing.prep_time === effort.prepTime && existing.cook_time === effort.cookTime && existing.difficulty === effort.difficulty;
        await conn.query(`UPDATE dish SET name=?, note=?, cover_url=?, prep_time=?, cook_time=?, difficulty=?,
          source='IMPORT', source_name=?, deleted=0 WHERE id=?`, [...fields, SOURCE_NAME, dishId]);
        report[same ? 'unchanged' : 'updated']++;
      } else {
        const [res] = await conn.query(`INSERT INTO dish(name, note, cover_url, prep_time, cook_time, difficulty, source, source_name, source_url, deleted)
          VALUES (?, ?, ?, ?, ?, ?, 'IMPORT', ?, ?, 0)`, [...fields, SOURCE_NAME, sourceUrl]);
        dishId = res.insertId;
        report.inserted++;
      }
      await conn.query('DELETE FROM dish_step WHERE dish_id = ?', [dishId]);
      if (steps.length) {
        await conn.query('INSERT INTO dish_step(dish_id, seq, text, images, sort_order) VALUES ?',
          [steps.map((s, i) => [dishId, i + 1, s.text, s.image, i + 1])]);
      }
      await conn.query('DELETE FROM dish_ingredient WHERE dish_id = ?', [dishId]);
      if (rows.length) {
        await conn.query('INSERT INTO dish_ingredient(dish_id, ingredient_id, amount, unit_id, grams) VALUES ?', [rows.map((x) => [dishId, ...x])]);
      }
      for (const [id, rel] of dictIds) {
        await conn.query('INSERT IGNORE INTO dish_dict(dish_id, dict_id, rel_type) VALUES (?, ?, ?)', [dishId, id, rel]);
      }
      await conn.commit();
    } catch (e) {
      await conn.rollback();
      throw new Error(`「${d.name}」(${sourceUrl}) 写入失败：${e.message}`);
    }
  }
} finally {
  await close();
}

report.lines.matchRate = report.lines.counted ? Math.round((report.lines.matched / report.lines.counted) * 1000) / 10 : 0;
report.unmatchedTop = [...unmatched.entries()].sort((a, b) => b[1] - a[1]).slice(0, 60).map(([n, c]) => `${n}:${c}`);
const outDir = join(SEED, '.cache');
mkdirSync(outDir, { recursive: true });
const out = join(outDir, `report-recipes-${DRY ? 'dry-' : ''}${Date.now()}.json`);
writeFileSync(out, JSON.stringify(report, null, 2));
console.log(JSON.stringify({ ...report, unmatchedTop: report.unmatchedTop.slice(0, 30) }, null, 1));
console.log(`报告：${out}`);
