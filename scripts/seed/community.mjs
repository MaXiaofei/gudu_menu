#!/usr/bin/env node
// 厨房圈演示内容（仅测试环境）：社区作者 7 位 + 约 24 条帖子 + 赞 / 收藏 / 照着做 / 评论 / 关注 + 本周「一周不重样」打卡。
//
// 用法：
//   node community.mjs --env=staging            # 重建（可重复执行：作者按 phone upsert，帖子与关系删后重灌）
//   node community.mjs --env=staging --dry-run  # 只检查依赖（菜谱、话题、演示账号）
//   node community.mjs --env=staging --clean    # 只清理
//
// 说明：
// - 作者是 member 表里的社区账号（phone 前缀 cm_，无密码、无健康档案），不计入任何家庭（MemberService.household 排除）。
// - 帖子图片取 staging 已导入的下厨房菜谱封面与步骤图（本站域名，SiteImage 校验通过）；版权归原作者，只用于测试环境。
// - 赞 / 收藏数 = 演示账号之间的真实关系行 + 模拟的站外热度基数（让 Feed 看起来像在运营），liked/collected 只看真实行。
// - 关注流是写扩散：脚本把作者的帖子写进关注者的 Redis 收件箱（gudu:community:inbox:<userId>，经 SSH 在 staging 的 redis 容器执行）。
// - 日期相对运行日计算（本地日期）；话题挑战按周一至周日。
import { spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { join } from 'node:path';
import { connect, parseEnvArg, sshEnv } from './lib/db.mjs';
import { ROOT, readEnv } from './lib/env.mjs';

const argv = process.argv.slice(2);
const target = parseEnvArg(argv);
const CLEAN = argv.includes('--clean');
const DRY = argv.includes('--dry-run');
const env = readEnv();
const IMAGE_BASE = (env.SEED_IMAGE_BASE || 'https://staging.imxf.cloud/images').replace(/\/$/, '');
const DEMO_USER = env.STAGING_DEMO_USER || 'demo';
const PHONE_PREFIX = 'cm_';
const REDIS_CONTAINER = 'gudu-redis-staging';
const INBOX_KEY = 'gudu:community:inbox:';

// ---------------- 演示数据（与原型 Community / Post / Topic 一致） ----------------
const AUTHORS = [
  { key: 'tangtang', name: '糖糖酱', city: '上海' },
  { key: 'chenchu', name: '陈师傅', city: '杭州' },
  { key: 'xiaoman', name: '小满爱下厨', city: '北京' },
  { key: 'amay', name: '阿May', city: '广州' },
  { key: 'dabai', name: '大白的厨房', city: '成都' },
  { key: 'linayi', name: '林阿姨家常菜', city: '南京' },
  { key: 'mimi', name: '米米妈', city: '苏州' },
];

// [作者, 菜名, 标题, 正文, 话题, 距今天数, 时刻]
// 话题：W=一周不重样（周挑战，本周的帖子会打卡）、B=宝宝辅食、Q=快手菜
// 距今天数为负表示「本周周一 + n 天」（打卡帖），用 wk(n) 表示
const wk = (n) => ({ week: n });
const POSTS = [
  ['tangtang', '清蒸鲈鱼', '第一次做清蒸鲈鱼就成功了！', '一斤二两的鲈鱼，背上划刀摆成孔雀开屏，水开后大火蒸 8 分钟，关火再焖 2 分钟。倒掉盘里的腥水，铺葱丝淋热油，「滋啦」一声就成了。关键：油一定要烧到微微冒烟。', ['W'], wk(0), '18:40'],
  ['tangtang', '可乐鸡翅', '可乐鸡翅，孩子连吃五个', '翅中两面划刀，冷水下锅焯掉血沫。煎到两面金黄再倒可乐，没过鸡翅，大火收汁到能挂在翅上就关火。', ['W'], wk(1), '18:20'],
  ['tangtang', '蒜蓉西兰花', '十分钟快手菜：蒜蓉西兰花', '西兰花掰小朵，盐水泡 10 分钟。焯水 1 分钟捞出过凉，蒜末小火炒香再下西兰花，翠绿不发黄。', ['W', 'Q'], wk(2), '18:10'],
  ['tangtang', '土豆炖牛肉', '周四的土豆炖牛肉', '牛腩冷水下锅焯水，炒糖色后加开水炖一个小时，最后半小时放土豆。汤汁拌饭一绝。', ['W'], wk(3), '19:00'],
  ['tangtang', '糖醋排骨', '糖醋排骨，酸甜刚好', '比例记住 1 料酒 2 酱油 3 糖 4 醋 5 水，收汁时一直翻，每块都裹上。', ['W'], wk(4), '18:30'],
  ['chenchu', '红烧肉', '老上海红烧肉，不加一滴水', '五花肉切 3 厘米块，冷锅慢慢煸出油，冰糖炒到琥珀色，黄酒代替水小火焖 50 分钟。', ['W'], wk(0), '12:10'],
  ['chenchu', '麻婆豆腐', '麻婆豆腐的灵魂是最后那把花椒面', '豆腐先在淡盐水里焯一下不易碎；勾芡分两次，出锅前撒现磨花椒面。', ['W'], wk(1), '18:50'],
  ['chenchu', '鱼香肉丝', '没有鱼的鱼香肉丝', '碗汁提前调好：糖醋各两勺、生抽一勺、淀粉一勺。肉丝滑油要快，泡椒末是灵魂。', ['W', 'Q'], wk(2), '19:10'],
  ['chenchu', '油焖大虾', '油焖大虾，虾油是精华', '剪掉虾枪和虾线，煎出红油后加少许糖和生抽，盖盖焖两分钟。', ['W'], wk(3), '18:45'],
  ['linayi', '莲藕排骨汤', '秋天就该喝一锅莲藕排骨汤', '粉藕选孔少的，排骨焯水后和藕一起砂锅小火炖两小时，出锅前再放盐。', ['W'], wk(0), '17:30'],
  ['linayi', '地三鲜', '地三鲜少油版', '茄子先撒盐腌 10 分钟挤掉水分，空气炸锅 180 度 12 分钟代替油炸，一点不比饭店差。', ['W'], wk(2), '18:20'],
  ['linayi', '手撕包菜', '手撕包菜，下饭神器', '包菜用手撕不用刀切，锅要烧到冒烟，沿锅边淋醋，香得很。', ['W', 'Q'], wk(3), '18:05'],
  ['xiaoman', '番茄菜花', '番茄菜花，汤汁拌饭', '番茄去皮炒出沙，菜花焯水后下锅，少许番茄酱提色，加盖焖三分钟。', ['W'], wk(1), '18:35'],
  ['xiaoman', '酸辣土豆丝', '酸辣土豆丝刀工练习中', '土豆丝泡水洗掉淀粉，大火快炒一分钟，沿锅边下醋，脆！', ['W', 'Q'], wk(3), '19:20'],
  ['mimi', '牛奶南瓜羹', '1 岁宝宝也能吃的牛奶南瓜羹', '南瓜蒸熟压泥，加配方奶搅匀，小火煮开就好。不加糖，南瓜本身就甜。', ['B'], 1, '10:20'],
  ['mimi', '蒸水蛋', '蒸水蛋嫩得像布丁', '蛋液和温水 1:1.5，过筛去泡，盖保鲜膜扎几个孔，中火蒸 10 分钟。', ['B'], 3, '11:40'],
  ['mimi', '香菇鸡肉粥', '香菇鸡肉粥，宝宝一顿吃一碗', '鸡腿肉剁碎，香菇切末，米粥熬开花后放进去煮十分钟，出锅前一点点香油。', ['B'], 6, '08:30'],
  ['amay', '白灼虾', '白灼虾，鲜就完事', '水里放姜片葱段和少许料酒，虾下锅变色卷曲马上捞出，蘸汁用生抽加小米辣。', ['Q'], 2, '19:05'],
  ['amay', '凉拌黄瓜', '三分钟上桌的拍黄瓜', '黄瓜拍裂切段，撒盐腌 5 分钟倒掉水，加蒜末、香醋、生抽和一点辣椒油拌匀。', ['Q'], 5, '18:15'],
  ['dabai', '酸汤肥牛', '酸汤肥牛，冬天第一锅', '黄灯笼辣椒酱是灵魂，金针菇垫底，肥牛最后下锅变色即关火。', [], 4, '19:30'],
  ['dabai', '鱼香肉丝', '大白版鱼香肉丝，多放木耳', '木耳和胡萝卜丝多放，肉少一点也很下饭。', [], 9, '18:40'],
  ['amay', '滑蛋虾仁', '滑蛋虾仁，嫩滑秘诀在这里', '蛋液里加一勺水淀粉，锅里油温低一点，蛋液半凝固时就关火，用余温推熟。', ['Q'], 8, '12:30'],
  ['xiaoman', '凉拌菠菜', '凉拌菠菜，焯水 30 秒就够', '菠菜焯水去草酸，挤干后加蒜末、香醋和一点芝麻油，清爽解腻。', [], 7, '18:00'],
  ['demo', '滑蛋虾仁', '10 分钟的滑蛋虾仁，宝宝抢着吃', '今天下班晚，做了个最快的滑蛋虾仁。虾仁提前用蛋清和淀粉抓一下，蛋液半凝固就关火，宝宝吃了一大碗。', ['W', 'Q'], wk(4), '18:40'],
];

// 评论：[帖子序号, 作者, 内容, 回复给第几条（同帖内，null = 顶层）]
const COMMENTS = [
  [0, 'chenchu', '蒸完倒掉腥水这步太关键了，很多人忽略。', null],
  [0, 'tangtang', '对！试过不倒，腥味重很多～', 0],
  [0, 'demo', '照着做了，一次成功，谢谢分享！', null],
  [0, 'mimi', '请问宝宝能吃吗？豉油要少放吧', null],
  [0, 'tangtang', '宝宝那份单独盛出来，不淋豉油就行', 3],
  [1, 'xiaoman', '可乐要用普通的，无糖的收不出汁', null],
  [5, 'linayi', '黄酒代水这个方法学到了', null],
  [5, 'chenchu', '黄酒要用花雕，香味更足', 6],
  [9, 'demo', '妈妈说这个汤最适合她，少盐也好喝', null],
  [14, 'demo', '朵朵最近就爱喝这个', null],
  [14, 'mimi', '可以再加一点点小米粥，更稠', 9],
  [23, 'tangtang', '看着就好嫩！', null],
  [23, 'linayi', '虾仁腌一下确实更弹', null],
];

// 关注：[关注者, 被关注者]
const FOLLOWS = [
  ['demo', 'tangtang'], ['demo', 'chenchu'], ['demo', 'xiaoman'], ['demo', 'linayi'], ['demo', 'mimi'],
  ['tangtang', 'chenchu'], ['chenchu', 'tangtang'], ['xiaoman', 'tangtang'], ['mimi', 'tangtang'],
  ['amay', 'chenchu'], ['dabai', 'chenchu'], ['linayi', 'demo'], ['tangtang', 'demo'], ['mimi', 'linayi'],
];

// 演示账号收藏 / 点赞（真实行，详情页显示已赞 / 已收藏）
const DEMO_LIKES = [0, 1, 5, 9, 14, 17];
const DEMO_COLLECTS = [0, 5, 14];
// 照着做（真实行，「他们照着做了」）：[帖子序号, 用户]
const COPIES = [[0, 'demo'], [0, 'xiaoman'], [0, 'mimi'], [1, 'amay'], [5, 'tangtang'], [14, 'demo']];
// 模拟站外热度基数（让 Feed 像在运营）：按帖子序号给赞 / 收藏 / 照着做的基数
const HEAT = [[1180, 386, 128], [920, 240, 562], [310, 88, 40], [540, 140, 76], [660, 210, 95], [1400, 520, 309],
  [880, 260, 120], [450, 120, 60], [600, 150, 70], [720, 300, 110], [260, 70, 30], [380, 90, 45], [300, 80, 35],
  [220, 60, 20], [856, 410, 220], [640, 300, 150], [420, 180, 90], [350, 90, 40], [210, 50, 20], [480, 130, 60],
  [150, 30, 10], [690, 230, 100], [280, 70, 25], [24, 6, 2]];
const RATIOS = [0.75, 1.0, 0.8, 1.33, 0.9, 0.75, 1.0, 1.2];

// ---------------- 工具 ----------------
const pad = (n) => String(n).padStart(2, '0');
const fmtDate = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const today = new Date();
const monday = new Date(today); monday.setDate(today.getDate() - ((today.getDay() + 6) % 7));
/** 帖子时间：数字 = 距今天数；{week:n} = 本周周一 + n 天（不晚于今天）。 */
function postDate(when) {
  const d = typeof when === 'number' ? new Date(today.getTime() - when * 86400000) : new Date(monday.getTime() + when.week * 86400000);
  return d > today ? new Date(today) : d;
}
const at = (d, hm) => `${fmtDate(d)} ${hm}:00`;

function remote(cmd) {
  const { host, user, env: e } = sshEnv();
  const r = spawnSync('sshpass', ['-e', 'ssh', '-o', 'StrictHostKeyChecking=no', '-o', 'LogLevel=ERROR', '-o', 'ConnectTimeout=30',
    `${user}@${host}`, cmd], { env: e, encoding: 'utf8' });
  if (r.status !== 0) throw new Error(`远端命令失败：${(r.stderr || '').trim()}`);
  return r.stdout;
}

function uploadAvatars() {
  const src = join(ROOT, 'scripts/seed/assets/community-avatar');
  if (!existsSync(src)) throw new Error('缺少社区头像 PNG（scripts/seed/assets/community-avatar）');
  const { host, user, env: e } = sshEnv();
  const ssh = 'ssh -o StrictHostKeyChecking=no -o LogLevel=ERROR -o ConnectTimeout=30';
  remote('mkdir -p /root/gudu/staging-images/avatar');
  const r = spawnSync('sshpass', ['-e', 'rsync', '-az', '-e', ssh, `${src}/`, `${user}@${host}:/root/gudu/staging-images/avatar/`],
    { env: e, encoding: 'utf8' });
  if (r.status !== 0) throw new Error(`头像上传失败：${r.stderr.trim()}`);
}

async function seedPostIds(conn, authorIds, demoId) {
  const [rows] = await conn.query(
    'SELECT id FROM community_post WHERE author_id IN (?) OR (author_id = ? AND title IN (?))',
    [authorIds.length ? authorIds : [0], demoId ?? 0, POSTS.filter((p) => p[0] === 'demo').map((p) => p[2])]);
  return rows.map((r) => r.id);
}

async function clean(conn, authorIds, demoId) {
  const ids = await seedPostIds(conn, authorIds, demoId);
  const n = { posts: ids.length };
  if (ids.length) {
    for (const t of ['community_post_topic', 'community_comment', 'community_reaction', 'community_copy', 'community_report',
      'community_topic_checkin', 'community_moderation_log', 'community_appeal']) {
      await conn.query(`DELETE FROM ${t} WHERE post_id IN (?)`, [ids]);
    }
    await conn.query('DELETE FROM community_post WHERE id IN (?)', [ids]);
  }
  const users = [...authorIds, ...(demoId ? [demoId] : [])];
  if (users.length) {
    const [{ affectedRows }] = await conn.query(
      'DELETE FROM community_follow WHERE (follower_id IN (?) AND followee_id IN (?))', [users, users]);
    n.follows = affectedRows;
  }
  return n;
}

function redisRebuildInboxes(userIds, entries) {
  // entries: Map<userId, [{postId, ms}]>
  const cmds = [];
  for (const uid of userIds) cmds.push(`DEL ${INBOX_KEY}${uid}`);
  for (const [uid, list] of entries) {
    for (const e of list) cmds.push(`ZADD ${INBOX_KEY}${uid} ${e.ms} ${e.postId}`);
  }
  if (!cmds.length) return;
  remote(`docker exec -i ${REDIS_CONTAINER} redis-cli <<'EOF'\n${cmds.join('\n')}\nEOF`);
}

// ---------------- 执行 ----------------
const { conn, close, describe } = await connect(target);
try {
  console.log(`目标：${describe}${DRY ? '【dry-run】' : ''}${CLEAN ? '【只清理】' : ''}`);
  const [[demo]] = await conn.query('SELECT id FROM member WHERE phone = ?', [DEMO_USER]);
  const [authorRows] = await conn.query('SELECT id, phone FROM member WHERE phone LIKE ?', [`${PHONE_PREFIX}%`]);
  if (CLEAN) {
    await conn.beginTransaction();
    const n = await clean(conn, authorRows.map((r) => r.id), demo?.id);
    await conn.query('DELETE FROM member WHERE phone LIKE ?', [`${PHONE_PREFIX}%`]);
    await conn.commit();
    redisRebuildInboxes([...authorRows.map((r) => r.id), ...(demo ? [demo.id] : [])], new Map());
    console.log('已清理：', n);
    process.exit(0);
  }
  if (!demo) { console.error(`缺少演示账号 ${DEMO_USER}（先跑 demo-family.mjs）`); process.exit(1); }

  // 依赖：菜谱（有封面、有步骤，同名取步骤最多的）与话题
  const dishNames = [...new Set(POSTS.map((p) => p[1]))];
  const [dishRows] = await conn.query(`SELECT d.id, d.name, d.cover_url,
      (SELECT COUNT(*) FROM dish_step s WHERE s.dish_id = d.id) steps
    FROM dish d WHERE d.deleted = 0 AND d.cover_url IS NOT NULL AND d.name IN (?)`, [dishNames]);
  const dish = new Map();
  for (const d of dishRows.filter((x) => x.steps > 0).sort((a, b) => b.steps - a.steps || a.id - b.id)) {
    if (!dish.has(d.name)) dish.set(d.name, d);
  }
  const missing = dishNames.filter((n) => !dish.has(n));
  const [topicRows] = await conn.query("SELECT id, name FROM community_topic WHERE name IN ('一周不重样', '宝宝辅食', '快手菜')");
  const topic = { W: topicRows.find((t) => t.name === '一周不重样')?.id, B: topicRows.find((t) => t.name === '宝宝辅食')?.id,
    Q: topicRows.find((t) => t.name === '快手菜')?.id };
  console.log(`依赖：菜谱 ${dish.size}/${dishNames.length}，话题 ${topicRows.length}/3，演示账号 #${demo.id}`);
  if (missing.length || topicRows.length < 3) {
    console.error(`缺少菜谱：${missing.join('、') || '无'}${topicRows.length < 3 ? '；话题不全（先执行 V71）' : ''}`);
    process.exit(1);
  }
  const [stepRows] = await conn.query('SELECT dish_id, images FROM dish_step WHERE dish_id IN (?) AND images IS NOT NULL ORDER BY dish_id, sort_order, seq',
    [[...dish.values()].map((d) => d.id)]);
  const stepImages = new Map();
  for (const s of stepRows) {
    const urls = String(s.images).replace(/[[\]"]/g, '').split(',').map((u) => u.trim()).filter((u) => u.startsWith('https://'));
    if (!stepImages.has(s.dish_id)) stepImages.set(s.dish_id, []);
    stepImages.get(s.dish_id).push(...urls);
  }
  if (DRY) process.exit(0);

  uploadAvatars();
  await conn.beginTransaction();
  // 作者：按 phone upsert，id 不变；无密码、无健康档案（不进任何家庭）
  const uid = { demo: demo.id };
  for (const a of AUTHORS) {
    const phone = `${PHONE_PREFIX}${a.key}`;
    const avatar = `${IMAGE_BASE}/avatar/c-${a.key}.png`;
    const [[row]] = await conn.query('SELECT id FROM member WHERE phone = ?', [phone]);
    if (row) {
      await conn.query('UPDATE member SET name=?, avatar_url=?, password_hash=NULL, is_admin=0, health_profile=NULL, deleted=0 WHERE id=?',
        [a.name, avatar, row.id]);
      uid[a.key] = row.id;
    } else {
      const [res] = await conn.query('INSERT INTO member(name, avatar_url, phone, password_hash, is_admin, deleted) VALUES (?, ?, ?, NULL, 0, 0)',
        [a.name, avatar, phone]);
      uid[a.key] = res.insertId;
    }
  }
  const authorIds = AUTHORS.map((a) => uid[a.key]);
  const removed = await clean(conn, authorIds, demo.id);

  // 帖子
  const postIds = [];
  const postMeta = [];
  for (let i = 0; i < POSTS.length; i++) {
    const [who, dishName, title, text, topics, when, hm] = POSTS[i];
    const d = dish.get(dishName);
    const images = [d.cover_url, ...(stepImages.get(d.id) || []).slice(-2)].slice(0, 3);
    const created = postDate(when);
    const city = AUTHORS.find((a) => a.key === who)?.city ?? '上海';
    const [like, collect, copy] = HEAT[i] || [20, 5, 1];
    const [res] = await conn.query(`INSERT INTO community_post(author_id, title, text, images, dish_id, visibility, status, risk,
        reviewed, city, cover_ratio, like_count, collect_count, comment_count, copy_count, create_time)
      VALUES (?, ?, ?, ?, ?, 'PUBLIC', 'PUBLISHED', 'LOW', 1, ?, ?, ?, ?, 0, ?, ?)`,
    [uid[who], title, text, JSON.stringify(images), d.id, city, RATIOS[i % RATIOS.length], like, collect, copy, at(created, hm)]);
    postIds.push(res.insertId);
    postMeta.push({ id: res.insertId, author: uid[who], created: new Date(`${fmtDate(created)}T${hm}:00`), topics, date: fmtDate(created) });
    for (const t of topics) await conn.query('INSERT INTO community_post_topic(post_id, topic_id) VALUES (?, ?)', [res.insertId, topic[t]]);
  }

  // 话题打卡：本周（周一起）的「一周不重样」帖子，每人每天一次
  const weekStart = fmtDate(monday);
  let checkins = 0;
  for (const p of postMeta) {
    if (!p.topics.includes('W') || p.date < weekStart) continue;
    const [r] = await conn.query('INSERT IGNORE INTO community_topic_checkin(topic_id, user_id, checkin_date, post_id, create_time) VALUES (?, ?, ?, ?, ?)',
      [topic.W, p.author, p.date, p.id, at(p.created, '20:00')]);
    checkins += r.affectedRows;
  }

  // 评论（两级）
  const commentIds = [];
  const commentCount = new Map();
  for (const [pi, who, text, replyTo] of COMMENTS) {
    const parent = replyTo == null ? null : commentIds[replyTo];
    const t = new Date(Math.min(postMeta[pi].created.getTime() + (commentIds.length + 1) * 37 * 60000, Date.now()));
    const [res] = await conn.query('INSERT INTO community_comment(post_id, author_id, parent_id, text, create_time) VALUES (?, ?, ?, ?, ?)',
      [postIds[pi], uid[who], parent, text, at(t, `${pad(t.getHours())}:${pad(t.getMinutes())}`)]);
    commentIds.push(res.insertId);
    commentCount.set(pi, (commentCount.get(pi) || 0) + 1);
  }
  for (const [pi, n] of commentCount) await conn.query('UPDATE community_post SET comment_count = ? WHERE id = ?', [n, postIds[pi]]);

  // 赞 / 收藏 / 照着做（真实行 + 计数基数已含在 HEAT 里）
  let reactions = 0;
  const reactors = ['tangtang', 'chenchu', 'xiaoman', 'amay', 'dabai', 'linayi', 'mimi'];
  for (let i = 0; i < postIds.length; i++) {
    for (let k = 0; k < reactors.length; k++) {
      const who = reactors[k];
      if (uid[who] === postMeta[i].author || (i + k) % 3 !== 0) continue;
      await conn.query("INSERT IGNORE INTO community_reaction(post_id, user_id, type) VALUES (?, ?, 'LIKE')", [postIds[i], uid[who]]);
      reactions++;
    }
  }
  for (const i of DEMO_LIKES) { await conn.query("INSERT IGNORE INTO community_reaction(post_id, user_id, type) VALUES (?, ?, 'LIKE')", [postIds[i], demo.id]); reactions++; }
  for (const i of DEMO_COLLECTS) { await conn.query("INSERT IGNORE INTO community_reaction(post_id, user_id, type) VALUES (?, ?, 'COLLECT')", [postIds[i], demo.id]); reactions++; }
  for (const [i, who] of COPIES) {
    await conn.query('INSERT IGNORE INTO community_copy(post_id, user_id, dish_id) VALUES (?, ?, ?)', [postIds[i], uid[who], dish.get(POSTS[i][1]).id]);
  }

  // 关注
  for (const [a, b] of FOLLOWS) await conn.query('INSERT IGNORE INTO community_follow(follower_id, followee_id) VALUES (?, ?)', [uid[a], uid[b]]);
  await conn.commit();

  // 关注流收件箱（写扩散）：关注者收件箱写入被关注者的帖子
  const inbox = new Map();
  for (const [a, b] of FOLLOWS) {
    const list = inbox.get(uid[a]) || [];
    for (const p of postMeta.filter((x) => x.author === uid[b])) list.push({ postId: p.id, ms: p.created.getTime() });
    inbox.set(uid[a], list);
  }
  redisRebuildInboxes([...authorIds, demo.id], inbox);

  console.log(`厨房圈演示内容已重建：作者 ${AUTHORS.length}，帖子 ${postIds.length}（本周打卡 ${checkins}），评论 ${COMMENTS.length}，`
    + `赞/收藏行 ${reactions}，照着做 ${COPIES.length}，关注 ${FOLLOWS.length}；清掉旧数据 ${JSON.stringify(removed)}`);
} catch (e) {
  try { await conn.rollback(); } catch { /* ignore */ }
  throw e;
} finally {
  await close();
}
