#!/usr/bin/env node
// staging 演示家庭「小王家的厨房」（与 docs/design/prototype 原型一致）。
//
//   node demo-family.mjs --env=staging            # 重建（幂等：成员按 phone upsert，id 不变；动态数据删后重灌）
//   node demo-family.mjs --env=staging --clean    # 只清理演示数据（含成员）
//   node demo-family.mjs --env=staging --dry-run  # 只检查依赖（菜谱/食材是否齐全），不写库
//
// 当前为单家庭模型（无 family 表）：成员 = 账号/档案，余量为全局表。
// 演示标记：member.phone 以 demo 开头；食集 target_member_id 指向演示成员；
//           做饭记录/收藏/通知按 member_id 归属。e2e-seed.sql 不清理这些行。
// 账号：phone = .env STAGING_DEMO_USER，密码 = STAGING_DEMO_PASSWORD（只读 .env，不打印）。
import { spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { join } from 'node:path';
import bcrypt from 'bcryptjs';
import { connect, parseEnvArg, sshEnv } from './lib/db.mjs';
import { ROOT, readEnv } from './lib/env.mjs';

const argv = process.argv.slice(2);
const target = parseEnvArg(argv);
const CLEAN = argv.includes('--clean');
const DRY = argv.includes('--dry-run');
const env = readEnv();
const IMAGE_BASE = (env.SEED_IMAGE_BASE || 'https://staging.imxf.cloud/images').replace(/\/$/, '');
const DEMO_USER = env.STAGING_DEMO_USER || 'demo';
if (!DEMO_USER.startsWith('demo')) { console.error('STAGING_DEMO_USER 必须以 demo 开头（清理靠此前缀识别）'); process.exit(2); }
if (!CLEAN && !DRY && !env.STAGING_DEMO_PASSWORD) { console.error('.env 缺少 STAGING_DEMO_PASSWORD'); process.exit(2); }

// ---------------- 演示数据（与原型一致） ----------------
const MEMBERS = [
  { key: 'xiaowang', name: '小王', phone: DEMO_USER, login: true, roleTags: '271', audiences: [], avatar: 'xiaowang' },
  { key: 'linlin', name: '林林', phone: `${DEMO_USER}_linlin`, roleTags: '272', audiences: ['高血脂'], avatar: 'linlin' },
  { key: 'mama', name: '妈妈', phone: `${DEMO_USER}_mama`, roleTags: '273', audiences: ['高血压'], avatar: 'mama' },
  { key: 'duoduo', name: '朵朵', phone: `${DEMO_USER}_duoduo`, roleTags: '273', audiences: ['宝宝辅食'], avatar: 'duoduo' },
];

// [食材名, 档位, 距今到期天数(null=不记), 已开封]
const STOCK = [
  ['鲈鱼', 'ENOUGH', 1, false], ['菠菜', 'LOW', 2, false], ['北豆腐', 'ENOUGH', 2, true],
  ['番茄', 'ENOUGH', 6, false], ['小葱', 'NONE', null, false], ['马铃薯', 'ENOUGH', 12, false],
  ['鸡蛋', 'LOW', 20, false], ['西兰花', 'NONE', null, false], ['虾仁(鲜)', 'ENOUGH', 30, false],
  ['猪肉(肥瘦)', 'ENOUGH', 60, false], ['牛奶', 'ENOUGH', 5, true], ['大蒜', 'ENOUGH', 30, false],
  ['姜', 'LOW', 14, false], ['蒸鱼豉油', 'ENOUGH', null, true], ['酱油', 'ENOUGH', null, true],
  ['大豆油', 'ENOUGH', null, true], ['食盐', 'ENOUGH', null, true], ['绵白糖', 'ENOUGH', null, true],
  ['南瓜', 'ENOUGH', 10, false], ['鸡翅', 'ENOUGH', 25, false], ['猪排骨', 'ENOUGH', 25, false],
  ['莲藕', 'LOW', 5, false], ['大白菜', 'ENOUGH', 7, false], ['胡萝卜', 'ENOUGH', 14, false],
  ['料酒', 'ENOUGH', null, true], ['淀粉', 'ENOUGH', null, true], ['大米', 'ENOUGH', null, true],
  ['香菇(鲜)', 'ENOUGH', 4, false], ['山药', 'ENOUGH', 9, false],
];

// 菜名 → 指定来源（同名多道时选封面好的那道）
const DISH_PICK = { 清蒸鲈鱼: 'https://www.xiachufang.com/recipe/35783/' };

// [名称, 菜单类型, 份数, 目标成员, 状态, 创建距今天数, [[菜名, 点菜人]]]
const MENUS = [
  ['今晚的饭', '日常', 4, 'xiaowang', 'ACTIVE', 0,
    [['清蒸鲈鱼', 'xiaowang'], ['滑蛋虾仁', 'duoduo'], ['蒜蓉西兰花', 'linlin'], ['西红柿鸡蛋汤', 'mama']]],
  ['周六 · 爸妈来吃饭', '家宴', 6, 'xiaowang', 'ACTIVE', 1,
    [['糖醋排骨', 'linlin'], ['油焖大虾', 'mama'], ['小鸡炖蘑菇', 'xiaowang'], ['红烧肉', 'xiaowang'], ['莲藕排骨汤', 'mama'], ['蒜蓉西兰花', 'linlin']]],
  ['朵朵一周辅食', '宝宝餐', 1, 'duoduo', 'ACTIVE', 2,
    [['牛奶南瓜羹', 'mama'], ['蒸水蛋', 'mama'], ['蓝莓山药泥', 'linlin']]],
  ['工作日两菜一汤', '工作日', 4, 'xiaowang', 'DONE', 3, [['麻婆豆腐', 'xiaowang'], ['手撕包菜', 'linlin'], ['冬瓜排骨汤', 'mama']]],
  ['周末小聚', '周末', 4, 'xiaowang', 'DONE', 6, [['可乐鸡翅', 'duoduo'], ['土豆炖牛肉', 'xiaowang'], ['凉拌黄瓜', 'linlin']]],
];

// 近 30 天做饭记录：[距今天数, 时刻, 菜名, 做饭人, 人份系数, 备注, 归属食集名(可空)]
const COOKS = [
  [0, '18:42', '清蒸鲈鱼', 'xiaowang', 1, '蒸 8 分钟刚好，下次少放点豉油。', '今晚的饭'],
  [2, '18:20', '土豆炖牛肉', 'mama', 1, null, null], [2, '18:30', '牛奶南瓜羹', 'mama', 0.3, '给朵朵', null],
  [3, '18:50', '麻婆豆腐', 'xiaowang', 1, '林林那份少放油', '工作日两菜一汤'], [3, '19:00', '手撕包菜', 'linlin', 1, null, '工作日两菜一汤'],
  [3, '19:05', '冬瓜排骨汤', 'mama', 1, null, '工作日两菜一汤'], [4, '12:10', '滑蛋虾仁', 'xiaowang', 1, '朵朵吃了半碗', null],
  [5, '18:40', '凉拌菠菜', 'linlin', 1, null, null], [6, '18:30', '可乐鸡翅', 'xiaowang', 1, '朵朵最爱', '周末小聚'],
  [6, '18:35', '土豆炖牛肉', 'mama', 1, null, '周末小聚'], [6, '18:40', '凉拌黄瓜', 'linlin', 1, null, '周末小聚'],
  [8, '18:20', '西红柿鸡蛋汤', 'mama', 1, null, null], [9, '18:45', '糖醋排骨', 'xiaowang', 1, null, null],
  [10, '08:10', '小米粥', 'mama', 1, null, null], [11, '18:30', '蒜蓉西兰花', 'linlin', 1, null, null],
  [12, '18:50', '红烧肉', 'xiaowang', 1, '肥肉少一点', null], [13, '18:30', '蒸水蛋', 'mama', 0.3, '给朵朵', null],
  [15, '18:40', '鲫鱼豆腐汤', 'mama', 1, null, null], [16, '19:00', '鱼香肉丝', 'xiaowang', 1, null, null],
  [18, '18:30', '地三鲜', 'linlin', 1, null, null], [19, '12:00', '番茄鸡蛋炒饭', 'xiaowang', 1, null, null],
  [21, '18:40', '小鸡炖蘑菇', 'mama', 1, null, null], [23, '18:30', '酸辣土豆丝', 'linlin', 1, null, null],
  [25, '18:50', '可乐鸡翅', 'xiaowang', 1, null, null], [27, '18:30', '皮蛋瘦肉粥', 'mama', 1, null, null],
];
const FAVORITES = ['滑蛋虾仁', '可乐鸡翅', '糖醋排骨', '麻婆豆腐', '莲藕排骨汤', '清蒸鲈鱼'];
const NOTICES = [
  ['expiry', '鲈鱼明天到期', '今晚做「清蒸鲈鱼」刚好用掉，家里够 4/5 样', 0, 0],
  ['expiry', '菠菜、北豆腐 2 天内到期', '可以做「凉拌菠菜」「麻婆豆腐」', 0, 0],
  ['shopping', '采购清单已更新', '「今晚的饭」还差西兰花、小葱 2 样', 1, 1],
];

// ---------------- 执行 ----------------
const day = (offset, hm = '12:00') => {
  const d = new Date(); d.setDate(d.getDate() - offset);
  const [h, m] = hm.split(':');
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${h}:${m}:00`;
};
const dateAfter = (n) => { const d = new Date(); d.setDate(d.getDate() + n); return d.toISOString().slice(0, 10); };

function uploadAvatars() {
  const src = join(ROOT, 'menu-flutter/assets/avatar');
  if (!existsSync(src)) throw new Error('缺少头像素材，先运行 scripts/design/gen-assets.mjs');
  const { host, user, env: e } = sshEnv();
  const ssh = 'ssh -o StrictHostKeyChecking=no -o LogLevel=ERROR -o ConnectTimeout=30';
  const run = (args) => spawnSync('sshpass', ['-e', ...args], { env: e, encoding: 'utf8' });
  let r = run(['ssh', '-o', 'StrictHostKeyChecking=no', '-o', 'LogLevel=ERROR', '-o', 'ConnectTimeout=30', `${user}@${host}`, 'mkdir -p /root/gudu/staging-images/avatar']);
  if (r.status !== 0) throw new Error(`远端建目录失败：${r.stderr.trim()}`);
  r = run(['rsync', '-az', '-e', ssh, `${src}/`, `${user}@${host}:/root/gudu/staging-images/avatar/`]);
  if (r.status !== 0) throw new Error(`头像上传失败：${r.stderr.trim()}`);
}

async function demoIds(conn) {
  const [rows] = await conn.query("SELECT id, phone FROM member WHERE phone = ? OR phone LIKE ?", [DEMO_USER, `${DEMO_USER}\\_%`]);
  return rows;
}

async function clean(conn) {
  const members = (await demoIds(conn)).map((r) => r.id);
  if (!members.length) return { members: 0 };
  const [menus] = await conn.query('SELECT id FROM menu WHERE target_member_id IN (?)', [members]);
  const menuIds = menus.map((m) => m.id);
  const n = {};
  if (menuIds.length) {
    const [lists] = await conn.query('SELECT id FROM shopping_list WHERE source_menu_id IN (?)', [menuIds]);
    if (lists.length) {
      await conn.query('DELETE FROM shopping_item WHERE list_id IN (?)', [lists.map((l) => l.id)]);
      await conn.query('DELETE FROM shopping_list WHERE id IN (?)', [lists.map((l) => l.id)]);
    }
    await conn.query('DELETE FROM menu_prep_status WHERE menu_id IN (?)', [menuIds]);
    await conn.query('DELETE FROM menu_dish WHERE menu_id IN (?)', [menuIds]);
    [{ affectedRows: n.menus }] = await conn.query('DELETE FROM menu WHERE id IN (?)', [menuIds]);
  }
  [{ affectedRows: n.cooks }] = await conn.query('DELETE FROM cooking_record WHERE member_id IN (?) OR menu_id IN (?)', [members, menuIds.length ? menuIds : [0]]);
  [{ affectedRows: n.favorites }] = await conn.query('DELETE FROM favorite WHERE member_id IN (?)', [members]);
  [{ affectedRows: n.notices }] = await conn.query('DELETE FROM notification WHERE member_id IN (?)', [members]);
  n.members = members.length;
  return n;
}

const { conn, close, describe } = await connect(target);
try {
  console.log(`目标：${describe}${DRY ? '【dry-run】' : ''}${CLEAN ? '【只清理】' : ''}`);
  if (CLEAN) {
    await conn.beginTransaction();
    const n = await clean(conn);
    await conn.query('DELETE FROM member WHERE phone = ? OR phone LIKE ?', [DEMO_USER, `${DEMO_USER}\\_%`]);
    await conn.commit();
    console.log('已清理：', n);
    process.exit(0);
  }

  // 依赖检查：菜谱与食材
  const dishNames = [...new Set([...MENUS.flatMap((m) => m[6].map((x) => x[0])), ...COOKS.map((c) => c[2]), ...FAVORITES])];
  const [dishRows] = await conn.query(`SELECT id, name, source_url FROM dish WHERE deleted=0 AND name IN (?) AND cover_url IS NOT NULL ORDER BY id`, [dishNames]);
  const dishId = new Map();
  for (const n of dishNames) {
    const cands = dishRows.filter((d) => d.name === n);
    const pick = cands.find((d) => d.source_url === DISH_PICK[n]) || cands[0];
    if (pick) dishId.set(n, pick.id);
  }
  const missingDish = dishNames.filter((n) => !dishId.has(n));
  const [ingRows] = await conn.query('SELECT id, name FROM ingredient WHERE deleted=0 AND name IN (?)', [STOCK.map((s) => s[0])]);
  const ingId = new Map(ingRows.map((i) => [i.name, i.id]));
  const missingIng = STOCK.map((s) => s[0]).filter((n) => !ingId.has(n));
  const [types] = await conn.query("SELECT id, name FROM sys_dict WHERE dict_group='menu_type'");
  const typeId = new Map(types.map((t) => [t.name, t.id]));
  console.log(`依赖：菜谱 ${dishId.size}/${dishNames.length}，食材 ${ingId.size}/${STOCK.length}`);
  if (missingDish.length || missingIng.length) {
    console.error(`缺少菜谱：${missingDish.join('、') || '无'}；缺少食材：${missingIng.join('、') || '无'}（先跑 recipes.mjs）`);
    process.exit(1);
  }
  if (DRY) process.exit(0);

  uploadAvatars();
  const hash = bcrypt.hashSync(env.STAGING_DEMO_PASSWORD, 10);

  await conn.beginTransaction();
  // 成员：按 phone upsert，id 不变
  const mid = {};
  for (const m of MEMBERS) {
    const avatarUrl = `${IMAGE_BASE}/avatar/${m.avatar}.png`;
    const profile = JSON.stringify({ audiences: m.audiences });
    const [[row]] = await conn.query('SELECT id FROM member WHERE phone = ?', [m.phone]);
    if (row) {
      await conn.query('UPDATE member SET name=?, avatar_url=?, password_hash=?, is_admin=0, role_tags=?, health_profile=?, deleted=0 WHERE id=?',
        [m.name, avatarUrl, m.login ? hash : null, m.roleTags, profile, row.id]);
      mid[m.key] = row.id;
    } else {
      const [res] = await conn.query('INSERT INTO member(name, avatar_url, phone, password_hash, is_admin, role_tags, health_profile, deleted) VALUES (?, ?, ?, ?, 0, ?, ?, 0)',
        [m.name, avatarUrl, m.phone, m.login ? hash : null, m.roleTags, profile]);
      mid[m.key] = res.insertId;
    }
  }
  const removed = await clean(conn);

  // 余量（全局表，按 ingredient_id upsert）
  for (const [name, level, days, opened] of STOCK) {
    await conn.query(`INSERT INTO ingredient_stock(ingredient_id, level, best_before, opened_at) VALUES (?, ?, ?, ?)
      ON DUPLICATE KEY UPDATE level=VALUES(level), best_before=VALUES(best_before), opened_at=VALUES(opened_at)`,
    [ingId.get(name), level, days == null ? null : dateAfter(days), opened ? day(3) : null]);
  }

  // 食集
  const menuId = {};
  for (const [name, type, serving, who, status, ago, dishes] of MENUS) {
    const [res] = await conn.query(`INSERT INTO menu(name, type_id, target_member_id, serving_count, create_time, deleted, status, finished_at)
      VALUES (?, ?, ?, ?, ?, 0, ?, ?)`, [name, typeId.get(type) ?? null, mid[who], serving, day(ago, '09:00'), status, status === 'DONE' ? day(ago, '19:30') : null]);
    menuId[name] = res.insertId;
    for (const [dish, by] of dishes) {
      const nick = MEMBERS.find((m) => m.key === by).name;
      await conn.query('INSERT INTO menu_dish(menu_id, dish_id, added_by_member_id, added_by_nickname, serving_factor) VALUES (?, ?, ?, ?, ?)',
        [res.insertId, dishId.get(dish), mid[by], nick, by === 'duoduo' ? 0.3 : 1]);
    }
  }

  // 做饭记录
  for (const [ago, hm, dish, who, factor, note, menu] of COOKS) {
    await conn.query('INSERT INTO cooking_record(dish_id, menu_id, member_id, cooked_at, note, serving_factor, source) VALUES (?, ?, ?, ?, ?, ?, ?)',
      [dishId.get(dish), menu ? menuId[menu] : null, mid[who], day(ago, hm), note, factor, menu ? 'menu' : 'dish']);
  }
  for (const f of FAVORITES) await conn.query('INSERT INTO favorite(member_id, dish_id) VALUES (?, ?)', [mid.xiaowang, dishId.get(f)]);
  for (const [type, title, content, read, ago] of NOTICES) {
    await conn.query('INSERT INTO notification(member_id, type, channel, title, content, is_read, create_time) VALUES (?, ?, ?, ?, ?, ?, ?)',
      [mid.xiaowang, type, 'in_app', title, content, read, day(ago, '08:00')]);
  }
  await conn.commit();
  console.log(`演示家庭已重建：成员 ${MEMBERS.length}（账号 ${DEMO_USER}），余量 ${STOCK.length}，食集 ${MENUS.length}，做饭记录 ${COOKS.length}，收藏 ${FAVORITES.length}，通知 ${NOTICES.length}；清掉旧数据 ${JSON.stringify(removed)}`);
} catch (e) {
  try { await conn.rollback(); } catch { /* 未开事务 */ }
  throw e;
} finally {
  await close();
}
