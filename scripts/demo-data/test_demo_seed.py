# -*- coding: utf-8 -*-
"""对生成的 seed SQL 做静态自检（不连数据库）。

环境变量：
  DEMO_REF_DIR      参考数据目录（ref-*.tsv），必需
  DEMO_PROD_SCHEMA  生产库表结构 dump（prod-schema.sql），必需
运行：python3 -m unittest -v test_demo_seed   （在 scripts/demo-data 目录下）
"""
import datetime as dt
import os
import re
import sys
import unittest
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gen_demo_seed as G  # noqa: E402

_SCRATCH = ('/private/tmp/claude-501/-Users-maxiaofei-mygithub-menu-new/29734f3b-4c5f-4c78-9c19-e7a150bc6b4a/'
            'scratchpad/demo')
REF_DIR = os.environ.get('DEMO_REF_DIR', _SCRATCH)
SCHEMA = os.environ.get('DEMO_PROD_SCHEMA', os.path.join(_SCRATCH, 'prod-schema.sql'))
IDS = dict(ME_ACCOUNT_ID=10000001, ME_FAMILY_ID=5, ME_PROFILE_ID=7, HUSBAND_ACCOUNT_ID=10000002)
START, CAP = G.START, G.CAP
FORBIDDEN_TABLES = re.compile(r'^(agent_|ai_|community_|feedback|content_|admin_operation_log|dish_|sys_)')
INS = re.compile(r'^INSERT INTO (\w+) \((.*?)\) VALUES \((.*)\);$')


# ---------------------------------------------------------------- 解析
def split_values(s):
    out, cur, depth, i, inq = [], [], 0, 0, False
    while i < len(s):
        c = s[i]
        if inq:
            cur.append(c)
            if c == '\\':
                cur.append(s[i + 1])
                i += 1
            elif c == "'":
                if i + 1 < len(s) and s[i + 1] == "'":
                    cur.append("'")
                    i += 1
                else:
                    inq = False
        elif c == "'":
            inq = True
            cur.append(c)
        elif c == '(':
            depth += 1
            cur.append(c)
        elif c == ')':
            depth -= 1
            cur.append(c)
        elif c == ',' and depth == 0:
            out.append(''.join(cur).strip())
            cur = []
        else:
            cur.append(c)
        i += 1
    out.append(''.join(cur).strip())
    return out


def unq(v):
    if v == 'NULL':
        return None
    if v.startswith("'") and v.endswith("'"):
        return v[1:-1].replace("''", "'").replace('\\\\', '\\')
    return v


def parse_schema(path):
    text = open(path, encoding='utf-8').read()
    tables = {}
    for m in re.finditer(r'CREATE TABLE `(\w+)` \((.*?)\n\) ENGINE', text, re.S):
        cols = {}
        for line in m.group(2).split('\n'):
            line = line.strip()
            mm = re.match(r'`(\w+)` ([^ ,]+)', line)
            if not mm:
                continue
            required = ('NOT NULL' in line and 'DEFAULT' not in line and 'AUTO_INCREMENT' not in line)
            cols[mm.group(1)] = dict(type=mm.group(2), required=required)
        tables[m.group(1)] = cols
    return tables


def parse_sql(path):
    rows = []   # (lineno, table, {col: raw})
    lines = open(path, encoding='utf-8').read().split('\n')
    for no, line in enumerate(lines):
        m = INS.match(line)
        if not m:
            continue
        cols = [c.strip().strip('`') for c in m.group(2).split(',')]
        vals = split_values(m.group(3))
        assert len(cols) == len(vals), (no, line[:200])
        rows.append((no, m.group(1), dict(zip(cols, vals))))
    return lines, rows


class SeedTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not (os.path.isdir(REF_DIR) and os.path.exists(SCHEMA)):
            raise unittest.SkipTest('缺少 DEMO_REF_DIR / DEMO_PROD_SCHEMA')
        cls.out = os.path.join(HERE, '_test_seed.sql')
        cls.out2 = os.path.join(HERE, '_test_seed2.sql')
        G.R.seed(G.SEED)
        G.write_sql(cls.out, REF_DIR, IDS, None, True, False)
        G.R.seed(G.SEED)
        G.write_sql(cls.out2, REF_DIR, IDS, None, True, False)
        cls.schema = parse_schema(SCHEMA)
        cls.ref = G.Ref(REF_DIR)
        cls.lines, cls.rows = parse_sql(cls.out)

    @classmethod
    def tearDownClass(cls):
        for p in (cls.out, cls.out2):
            if os.path.exists(p):
                os.remove(p)

    # -------- 结构
    def test_reproducible(self):
        self.assertEqual(open(self.out, encoding='utf-8').read(), open(self.out2, encoding='utf-8').read())

    def test_tables_columns_and_required(self):
        self.assertGreater(len(self.rows), 1000)
        for no, table, cols in self.rows:
            if table == 'demo_seed_registry':
                continue
            self.assertIn(table, self.schema, '表不存在：%s' % table)
            for c in cols:
                self.assertIn(c, self.schema[table], '%s.%s 列不存在' % (table, c))
            for c, meta in self.schema[table].items():
                if meta['required']:
                    self.assertIn(c, cols, '%s 缺必填列 %s (行 %d)' % (table, c, no + 1))
                    self.assertNotEqual(cols[c], 'NULL', '%s.%s 必填却为 NULL (行 %d)' % (table, c, no + 1))

    def test_no_forbidden_content(self):
        used = {t for _, t, _ in self.rows if t != 'demo_seed_registry'}
        for t in used:
            self.assertIsNone(FORBIDDEN_TABLES.match(t), '不应造这类数据：%s' % t)
        body = '\n'.join(self.lines)
        for w in ('测试', 'demo', 'E2E', '示例', 'Demo'):
            data = '\n'.join(l for l in self.lines if l.startswith(('INSERT INTO', 'UPDATE ')))
            data = re.sub(r"demo_seed_(registry|restore)", '', data)
            self.assertNotIn(w, data, '数据里出现敏感字样：%s' % w)
        self.assertNotIn('phone', '\n'.join(l for l in self.lines if l.startswith('INSERT INTO member')))
        for _, t, cols in self.rows:
            if t == 'account':
                for bad in ('phone', 'phone_hash', 'wx_openid', 'wx_unionid', 'password_hash'):
                    self.assertNotIn(bad, cols)
            if t == 'consent_record':
                self.assertEqual(unq(cols['doc_type']), 'sensitive_health')
        self.assertTrue(body)

    # -------- 引用的参考数据
    def test_dish_and_ingredient_ids_exist(self):
        dish_cols = {('cooking_record', 'dish_id'), ('menu_dish', 'dish_id'), ('review', 'dish_id'),
                     ('favorite', 'dish_id'), ('meal_plan_slot', 'dish_id'), ('daily_log_item', 'dish_id'),
                     ('menu_activity', 'dish_id')}
        ing_cols = {('ingredient_stock', 'ingredient_id'), ('stock_log', 'ingredient_id'),
                    ('shopping_item', 'ingredient_id'), ('menu_prep_status', 'ingredient_id'),
                    ('menu_cook_usage', 'ingredient_id'), ('daily_log_item', 'ingredient_id')}
        n_d = n_i = 0
        for _, t, cols in self.rows:
            for c, v in cols.items():
                if (t, c) in dish_cols and v != 'NULL':
                    n_d += 1
                    self.assertIn(int(v), self.ref.dish, '%s.%s=%s 不在公共菜谱' % (t, c, v))
                if (t, c) in ing_cols and v != 'NULL':
                    n_i += 1
                    self.assertIn(int(v), self.ref.ing, '%s.%s=%s 不在食材库' % (t, c, v))
        self.assertGreater(n_d, 200)
        self.assertGreater(n_i, 500)

    def test_menu_dish_names_match_ref(self):
        names = {}
        for _, t, cols in self.rows:
            if t == 'menu_activity' and cols['dish_id'] != 'NULL':
                names[int(cols['dish_id'])] = unq(cols['dish_name'])
        for did, name in names.items():
            self.assertEqual(self.ref.dish[did]['name'], name)

    # -------- 时间
    def test_all_times_in_range_and_not_round(self):
        pat = re.compile(r"'(2026-\d\d-\d\d)(?: (\d\d:\d\d:\d\d))?'")
        total = round_ = 0
        for no, line in enumerate(self.lines):
            if not line.startswith(('INSERT INTO', 'UPDATE ')) or 'demo_seed_registry' in line[:40]:
                continue
            m = INS.match(line)
            skip_cols = set()
            if m:
                cols = [c.strip().strip('`') for c in m.group(2).split(',')]
                vals = split_values(m.group(3))
                items = zip(cols, vals)
            else:
                items = [('x', line)]
            for c, v in items:
                if c in ('best_before',):
                    continue
                for d, tm in pat.findall(v):
                    if c in ('week_start', 'week_end', 'plan_date', 'date', 'start_date', 'end_date'):
                        t = dt.datetime.strptime(d, '%Y-%m-%d')
                        self.assertTrue(START.date() <= t.date() <= CAP.date(), (no, c, d))
                        continue
                    if not tm:
                        continue
                    t = dt.datetime.strptime(d + ' ' + tm, '%Y-%m-%d %H:%M:%S')
                    self.assertTrue(START <= t <= CAP, '时间越界 行%d %s %s' % (no + 1, c, t))
                    total += 1
                    if t.second == 0:
                        round_ += 1
        self.assertGreater(total, 1000)
        # 手写的回溯时间 / 同意时间也都带分秒；允许极少数整点
        self.assertLess(round_ / total, 0.01, '整点时间太多：%d/%d' % (round_, total))

    def test_create_before_update(self):
        for _, t, cols in self.rows:
            if t == 'meal_plan_draft':
                self.assertLessEqual(unq(cols['create_time']), unq(cols['update_time']))

    def test_parent_before_child(self):
        menus = {}
        lists = {}
        items = {}
        for _, t, cols in self.rows:
            pass
        var_of = {}
        counter = defaultdict(int)
        for _, t, cols in self.rows:
            if t == 'demo_seed_registry':
                continue
            counter[t] += 1
            var_of[(t, counter[t])] = '@%s_%d' % (t, counter[t])
        counter.clear()
        for _, t, cols in self.rows:
            if t == 'demo_seed_registry':
                continue
            counter[t] += 1
            v = '@%s_%d' % (t, counter[t])
            if t == 'menu':
                menus[v] = (unq(cols['create_time']), unq(cols['finished_at']))
            elif t == 'shopping_list':
                lists[v] = unq(cols['created_at'])
            elif t == 'shopping_item':
                items[v] = cols['list_id']
            elif t == 'cooking_record' and cols['menu_id'] != 'NULL':
                self.assertLessEqual(menus[cols['menu_id']][0], unq(cols['cooked_at']))
            elif t == 'review' and cols['menu_id'] != 'NULL':
                self.assertLessEqual(menus[cols['menu_id']][1], unq(cols['create_time']))
            elif t == 'stock_log' and cols['ref_id'] != 'NULL':
                self.assertLessEqual(lists[items[cols['ref_id']]], unq(cols['create_time']))
            elif t == 'shopping_list' and cols['source_menu_id'] != 'NULL':
                self.assertLessEqual(menus[cols['source_menu_id']][0], unq(cols['created_at']))
        self.assertGreater(len(menus), 20)

    def test_consent_before_health_write(self):
        # 无密钥时不写健康档案；这里用测试密钥再生成一份检查顺序
        if not self._cryptography():
            self.skipTest('未安装 cryptography')
        import base64
        key = base64.b64encode(b'0123456789abcdef0123456789abcdef').decode()
        cipher = G.HealthCipher(G.parse_keys('1:' + key), 1)
        out = os.path.join(HERE, '_test_seed3.sql')
        try:
            G.R.seed(G.SEED)
            G.write_sql(out, REF_DIR, IDS, cipher, True, True)
            lines = open(out, encoding='utf-8').read().split('\n')
            consent_idx = [i for i, l in enumerate(lines) if l.startswith('INSERT INTO consent_record')]
            update_idx = [i for i, l in enumerate(lines) if l.startswith('UPDATE member SET health_profile')]
            self.assertEqual(len(consent_idx), 2)
            self.assertEqual(len(update_idx), 2)
            for c, u in zip(consent_idx, update_idx):
                self.assertLess(c, u)
            self.assertTrue(all("'ENC:v1:" in lines[u] for u in update_idx))
        finally:
            if os.path.exists(out):
                os.remove(out)

    def test_crypto_fixture_in_sync(self):
        # Java 测试 DemoSeedCipherFixtureTest 用的 fixture 必须和当前加密实现一致（确定性 IV）
        if not self._cryptography():
            self.skipTest('未安装 cryptography')
        import json
        path = os.path.join(HERE, '..', '..', 'menu-api', 'src', 'test', 'resources', 'crypto',
                            'demo-seed-health-cipher.json')
        fx = json.load(open(path, encoding='utf-8'))
        cipher = G.HealthCipher(G.parse_keys(fx['keys']), fx['active'])
        self.assertEqual(fx['plain'], G.health_json(G.HUSBAND_HEALTH))
        self.assertEqual(fx['cipher'], cipher.encrypt(fx['plain']))

    @staticmethod
    def _cryptography():
        try:
            import cryptography  # noqa: F401
            return True
        except ImportError:
            return False

    # -------- 登记
    def test_registry_covers_every_insert(self):
        lines = self.lines
        n = 0
        for i, line in enumerate(lines):
            m = INS.match(line)
            if not m or m.group(1) == 'demo_seed_registry':
                continue
            n += 1
            self.assertRegex(lines[i + 1], r'^SET @%s_\d+ := LAST_INSERT_ID\(\);$' % m.group(1))
            var = re.match(r'^SET (@\S+) :=', lines[i + 1]).group(1)
            self.assertEqual(lines[i + 2],
                             "INSERT INTO demo_seed_registry (table_name, row_id) VALUES ('%s', %s);" % (m.group(1), var))
        reg = [l for l in lines if l.startswith('INSERT INTO demo_seed_registry')]
        self.assertEqual(n, len(reg))
        cleanup = open(os.path.join(HERE, 'cleanup_demo_seed.sql'), encoding='utf-8').read()
        for t in {t for _, t, _ in self.rows if t != 'demo_seed_registry'}:
            self.assertIn("r.table_name = '%s'" % t, cleanup, '清除脚本漏了 %s' % t)

    def test_every_update_is_restorable_or_own_row(self):
        for l in self.lines:
            if l.startswith('UPDATE '):
                ok = (l.startswith(('UPDATE member SET health_profile', 'UPDATE account SET last_family_id = %s'
                                    % '@family_1')) or 'demo_seed' in l)
                if not ok:
                    # 已有行的更新必须有同表同列的 restore 登记
                    tbl, col = re.match(r'UPDATE (\w+) SET (\w+) =', l).groups()
                    self.assertTrue(any(("INSERT INTO demo_seed_restore" in x and "'%s'" % tbl in x and "'%s'" % col in x)
                                        for x in self.lines), l[:100])

    # -------- 家庭隔离
    def test_family_isolation(self):
        ME = '@ME_FAMILY_ID'
        counter = defaultdict(int)
        fam_of = {}
        prof_fam = {'@ME_PROFILE_ID': ME}
        fr_fam = None
        child_parent = {'menu_dish': 'menu_id', 'menu_prep_status': 'menu_id', 'menu_activity': 'menu_id',
                        'menu_invite': 'menu_id', 'menu_join': 'menu_id', 'menu_cook_usage': 'menu_id',
                        'cooking_record': 'menu_id', 'review': 'menu_id', 'review_score': 'review_id',
                        'shopping_item': 'list_id', 'daily_log_item': 'log_id', 'meal_plan_slot': 'draft_id'}
        member_cols = {'cooking_record': 'member_id', 'review': 'member_id', 'favorite': 'member_id',
                       'daily_log': 'member_id', 'meal_plan_draft': 'member_id'}
        for _, t, cols in self.rows:
            if t == 'demo_seed_registry':
                continue
            counter[t] += 1
            v = '@%s_%d' % (t, counter[t])
            if t == 'family':
                fr_fam = v
            if t == 'member':
                prof_fam[v] = cols['family_id']
            fam = cols.get('family_id')
            if fam is None:
                continue
            fam_of[v] = fam
            if t != 'notification':
                self.assertIn(fam, (ME, fr_fam), '未知家庭 %s' % fam)
            pc = child_parent.get(t)
            if pc and cols.get(pc, 'NULL') != 'NULL':
                self.assertEqual(fam_of[cols[pc]], fam, '%s 的父记录属于另一个家庭' % t)
            mc = member_cols.get(t)
            if mc and cols.get(mc, 'NULL') != 'NULL':
                self.assertEqual(prof_fam[cols[mc]], fam, '%s 的成员档案属于另一个家庭' % t)
            if t == 'menu_dish':
                pass
            if t == 'shopping_list' and cols['source_menu_id'] != 'NULL':
                self.assertEqual(fam_of[cols['source_menu_id']], fam)
            if t == 'stock_log' and cols['ref_id'] != 'NULL':
                self.assertEqual(fam_of[cols['ref_id']], fam)
        # 通知收件人（档案）所属家庭与上下文一致：朋友档案只出现在朋友家的流水之后
        mem_vars = [v for v, f in prof_fam.items() if f == fr_fam]
        self.assertEqual(len(mem_vars), 1)
        fr_prof = mem_vars[0]
        counter.clear()
        seen_fr_run = False
        for _, t, cols in self.rows:
            if t == 'demo_seed_registry':
                continue
            if cols.get('family_id') == fr_fam and t in ('menu', 'ingredient_stock', 'stock_log'):
                seen_fr_run = True
            if t == 'notification':
                if cols['recipient_profile_id'] == fr_prof:
                    self.assertTrue(seen_fr_run)
                else:
                    self.assertEqual(cols['recipient_profile_id'], '@ME_PROFILE_ID')

    def test_friend_only_owns_her_family_stuff(self):
        # 朋友通过邀请加入后点的菜记在我们家的 menu_dish 里（added_by 是她的账号），不应有她家 family_id 的行出现在我们的食集下
        counter = defaultdict(int)
        fr_acct = None
        for _, t, cols in self.rows:
            if t == 'demo_seed_registry':
                continue
            counter[t] += 1
            if t == 'account':
                fr_acct = '@account_%d' % counter[t]
            if t == 'menu_dish' and cols['added_by_member_id'] == fr_acct:
                self.assertEqual(cols['family_id'], '@ME_FAMILY_ID')
                self.assertEqual(unq(cols['added_by_nickname']), '小林')
        inv = [c for _, t, c in self.rows if t == 'menu_invite'][0]
        self.assertEqual(inv['family_id'], '@ME_FAMILY_ID')
        self.assertEqual(len(unq(inv['code'])), 6)
        self.assertLess(unq(inv['create_time']), unq(inv['expires_at']))
        self.assertEqual(inv['revoked'], '0')

    # -------- 生活节奏
    def test_life_rhythm(self):
        cook = [unq(c['cooked_at']) for _, t, c in self.rows if t == 'cooking_record']
        days = {x[:10] for x in cook}
        self.assertFalse(days & {'2026-10-01', '2026-10-02', '2026-10-04'}, '回老家期间不应有做饭记录')
        self.assertIn('2026-10-05', days)
        by_hour = defaultdict(int)
        for x in cook:
            by_hour[int(x[11:13])] += 1
        evening = sum(v for h, v in by_hour.items() if 17 <= h <= 20)
        self.assertGreater(evening / len(cook), 0.5)
        self.assertGreaterEqual(len(days), 20)
        stars = [int(c['star_rating']) for _, t, c in self.rows if t == 'review']
        self.assertTrue(min(stars) <= 2 and max(stars) == 5)


if __name__ == '__main__':
    unittest.main()
