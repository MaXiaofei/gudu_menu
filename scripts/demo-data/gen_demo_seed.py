#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""咕嘟小食单 · 演示数据造数脚本生成器。

读取公共参考数据（菜谱 / 食材 / 用料 TSV）和账号参数，输出一份 seed SQL（MySQL 8，单事务存储过程）：
  - 开头校验参数指向的账号 / 家庭 / 档案存在且关系正确，不对 SIGNAL 报错并回滚；
  - 每插入一行都登记到 demo_seed_registry（表名 + 主键 id），清除脚本据此逆序删除；
  - 对已有行的少量 UPDATE（老公默认进入我们家、补早账号创建时间）记录在 demo_seed_restore，清除时还原；
  - 随机数固定种子，同样的参数 + 同样的 TSV 输出完全一致（健康档案密文 IV 也是确定性派生）。

用法见 README.md。仅依赖标准库；写健康档案时需要 cryptography（pip install cryptography）。
"""
import argparse
import base64
import csv
import datetime as dt
import hashlib
import hmac
import json
import os
import random
import sys
from collections import OrderedDict, defaultdict
from decimal import Decimal

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import demo_content as C  # noqa: E402

START = dt.datetime(2026, 9, 27, 0, 0, 0)
CAP = dt.datetime(2026, 10, 27, 7, 40, 0)   # 「今天」早晨：之后的时间一律不写
SEED = 20261027
SENSITIVE_DOC_VERSION = '2026-10-26'          # 与 ConsentAppService.SENSITIVE_DOC_VERSION 一致
IGNORE_ING = {28}                             # 米饭：现成主食，不进库存 / 采购
SHELF_DAYS = {1: 5, 2: 3, 3: 2, 4: 30, 5: 3, 6: 7, 7: 180, 8: 7, 9: 180, 10: 90, 11: 180, 12: 90}
LEAFY = {'小葱', '大葱', '香菜', '韭菜', '菠菜', '芹菜', '油菜', '小白菜', '生菜', '娃娃菜', '上海青', '荷兰豆'}
PURCHASE_UNITS = ['斤', '把', '个', '少许', '盒', '袋', '块', '克']
DIMENSIONS = ['口味', '难度', '营养均衡', '外观']
MENU_TYPES = ['日常', '家宴', '节日', '周末', '工作日']

R = random.Random(SEED)


# ====================================================================== SQL 基础
class Raw(str):
    """原样输出的 SQL 片段（变量引用 / 子查询）。"""


def q(v):
    if v is None:
        return 'NULL'
    if isinstance(v, Raw):
        return str(v)
    if isinstance(v, bool):
        return '1' if v else '0'
    if isinstance(v, int):
        return str(v)
    if isinstance(v, (float, Decimal)):
        return str(v)
    if isinstance(v, dt.datetime):
        return "'%s'" % v.strftime('%Y-%m-%d %H:%M:%S')
    if isinstance(v, dt.date):
        return "'%s'" % v.strftime('%Y-%m-%d')
    s = str(v).replace('\\', '\\\\').replace("'", "''").replace('\n', '\\n')
    return "'%s'" % s


def dict_id(group, name):
    return Raw("(SELECT id FROM sys_dict WHERE dict_group='%s' AND name='%s' ORDER BY id LIMIT 1)" % (group, name))


class Seed:
    def __init__(self):
        self.lines = []
        self.counter = defaultdict(int)

    def raw(self, sql):
        self.lines.append(sql)

    def ins(self, table, **cols):
        """单行 INSERT + 取回主键到用户变量 + 登记；返回变量引用（可直接作为后续列值）。"""
        self.counter[table] += 1
        var = Raw('@%s_%d' % (table, self.counter[table]))
        keys = list(cols)
        self.lines.append('INSERT INTO %s (%s) VALUES (%s);' % (
            table, ', '.join('`%s`' % k for k in keys), ', '.join(q(cols[k]) for k in keys)))
        self.lines.append('SET %s := LAST_INSERT_ID();' % var)
        self.lines.append("INSERT INTO demo_seed_registry (table_name, row_id) VALUES ('%s', %s);" % (table, var))
        return var


# ====================================================================== 时间
def _fix(t):
    if t.minute == 0:
        t = t.replace(minute=R.randint(1, 6))
    if t.second == 0:
        t = t.replace(second=R.randint(1, 58))
    return t


def chk(t):
    if t < START or t > CAP:
        raise ValueError('时间越界：%s' % t)
    return t


def mk(m, d, h, mi, jit=3):
    t = dt.datetime(2026, m, d, h, mi, R.randint(1, 58)) + dt.timedelta(minutes=R.randint(-jit, jit))
    return chk(_fix(t))


def after(t, lo, hi):
    t2 = t + dt.timedelta(minutes=R.randint(lo, hi))
    t2 = t2.replace(second=R.randint(1, 58))
    return chk(_fix(t2))


def secs(t, lo=2, hi=40):
    return chk(_fix(t + dt.timedelta(seconds=R.randint(lo, hi))))


# ====================================================================== 参考数据
def read_tsv(path):
    with open(path, encoding='utf-8') as f:
        return list(csv.DictReader(f, delimiter='\t'))


class Ref:
    def __init__(self, refdir):
        self.dish = OrderedDict()
        self.dish_by_name = {}
        for r in read_tsv(os.path.join(refdir, 'ref-dish.tsv')):
            if r['deleted'] != '0' or r['family_id'] != 'NULL':
                continue
            self.dish[int(r['id'])] = r
            self.dish_by_name.setdefault(r['name'], int(r['id']))
        self.ing = OrderedDict()
        self.ing_by_name = {}
        for r in read_tsv(os.path.join(refdir, 'ref-ingredient.tsv')):
            if r['deleted'] != '0':
                continue
            self.ing[int(r['id'])] = r
            self.ing_by_name.setdefault(r['name'], int(r['id']))
        self.di = defaultdict(list)
        for r in read_tsv(os.path.join(refdir, 'ref-dish_ingredient.tsv')):
            self.di[int(r['dish_id'])].append(int(r['ingredient_id']))

    def dish_id(self, name):
        if name not in self.dish_by_name:
            raise KeyError('公共菜谱里没有：%s' % name)
        i = self.dish_by_name[name]
        if not [x for x in self.di[i] if x not in IGNORE_ING]:
            raise KeyError('菜谱没有可用用料：%s' % name)
        return i

    def ing_id(self, name):
        if name not in self.ing_by_name:
            raise KeyError('食材库里没有：%s' % name)
        return self.ing_by_name[name]

    def cat(self, ing_id):
        return int(self.ing[ing_id]['purchase_category_id'] or 0)

    def name(self, ing_id):
        return self.ing[ing_id]['name']

    def meal_ings(self, dish_ids):
        out = []
        for d in dish_ids:
            for i in self.di[d]:
                if i not in IGNORE_ING and i not in out:
                    out.append(i)
        return out


# ====================================================================== 健康档案加密（与 FieldCipher 同格式）
def parse_keys(spec):
    keys = {}
    for part in (spec or '').split(','):
        part = part.strip()
        if not part:
            continue
        v, _, b = part.partition(':')
        keys[int(v)] = base64.b64decode(b)
    return keys


class HealthCipher:
    """ENC:v<版本>:base64(12 字节 IV ‖ 密文 ‖ 16 字节 tag)，AES-256-GCM。IV 由 HMAC 确定性派生（可复现）。"""

    def __init__(self, keys, active):
        from cryptography.hazmat.primitives.ciphers.aead import AESGCM  # 延迟导入：不写健康档案时不需要
        if active not in keys or len(keys[active]) != 32:
            raise ValueError('字段加密密钥须为 32 字节且包含 active 版本')
        self.keys, self.active, self._aes = keys, active, AESGCM

    def encrypt(self, plain):
        key = self.keys[self.active]
        iv = hmac.new(key, b'demo-seed-iv|' + plain.encode('utf-8'), hashlib.sha256).digest()[:12]
        ct = self._aes(key).encrypt(iv, plain.encode('utf-8'), None)
        return 'ENC:v%d:%s' % (self.active, base64.b64encode(iv + ct).decode('ascii'))


def health_json(d):
    return json.dumps(d, ensure_ascii=False, separators=(',', ':'))


HUSBAND_HEALTH = dict(gender='male', age=33, height=178, weight=76, allergies=[])
FRIEND_HEALTH = dict(gender='female', age=29, height=163, weight=53, allergies=['芒果'])


# ====================================================================== 家庭上下文
class Fam:
    def __init__(self, key, fam, profiles, accounts, notify_to):
        self.key, self.fam, self.profiles, self.accounts, self.notify_to = key, fam, profiles, accounts, notify_to
        self.state = {}            # ing_id -> dict(level, bb, opened, upd)
        self.notified = defaultdict(int)
        self.last_use = {}


# ====================================================================== 构建
class Builder:
    def __init__(self, ref, cipher, backdate=True):
        self.ref = ref
        self.cipher = cipher
        self.backdate = backdate
        self.S = Seed()
        self.pre = []        # 事务外的校验语句（procedure 内、START TRANSACTION 之前）

    # -------------------------------------------------- 菜单 / 点菜
    def make_menu(self, ctx, name, kind, n, created, status, finished=None, target=None):
        return self.S.ins('menu', name=name, type_id=dict_id('menu_type', kind), target_member_id=target,
                          serving_count=n, create_time=created, deleted=0, status=status, finished_at=finished,
                          family_id=ctx.fam)

    def norm_dishes(self, dishes, default_who):
        out = []
        for d in dishes:
            if isinstance(d, str):
                out.append(dict(name=d, who=default_who, note=None))
            else:
                out.append(dict(name=d[0], who=d[1], note=d[2]))
            out[-1]['id'] = self.ref.dish_id(out[-1]['name'])
        return out

    def add_menu_dishes(self, ctx, menu, dishes, together_accounts=None, note_override=None):
        for d in dishes:
            cols = dict(menu_id=menu, dish_id=d['id'], custom_name=None, serving_factor=Decimal('1.00'),
                        note=note_override if note_override else d['note'], family_id=ctx.fam)
            if together_accounts and d['who'] in together_accounts:
                acct, nick = together_accounts[d['who']]
                cols.update(added_by_member_id=acct, added_by_nickname=nick)
            else:
                cols.update(added_by_member_id=ctx.profiles[d['who']], added_by_nickname=None)
            self.S.ins('menu_dish', **cols)

    # -------------------------------------------------- 库存 / 日志
    def stock_log(self, ctx, ing, action, before, after_lv, bb, note, ref_id, t):
        self.S.ins('stock_log', ingredient_id=ing, action=action, before_level=before, after_level=after_lv,
                   best_before=bb, note=note, ref_id=ref_id, create_time=t, family_id=ctx.fam)

    def set_state(self, ctx, ing, level, bb, t, opened=None):
        st = ctx.state.get(ing)
        if st is None:
            st = ctx.state[ing] = dict(level=level, bb=bb, opened=None, upd=t)
        st.update(level=level, bb=bb, upd=t)
        if opened:
            st['opened'] = opened
        elif level == 'ENOUGH':
            st['opened'] = None

    def stock_add(self, ctx, ing, action, note, ref_id, t, bb):
        st = ctx.state.get(ing)
        before = st['level'] if st else None
        self.stock_log(ctx, ing, action, before, 'ENOUGH', None, note, ref_id, t)
        self.set_state(ctx, ing, 'ENOUGH', bb, t)

    # -------------------------------------------------- 采购单位
    def unit_for(self, ing):
        name, cat = self.ref.name(ing), self.ref.cat(ing)
        if name in LEAFY:
            return '把', Decimal(R.choice(['1', '1', '2']))
        if cat in (1, 2, 3, 8):
            return '斤', Decimal(R.choice(['0.50', '1.00', '1.00', '1.50', '2.00']))
        if cat == 4:
            return '盒', Decimal('1.00')
        if cat == 5:
            return '块', Decimal(R.choice(['1', '2']))
        if cat == 6:
            return '盒', Decimal(R.choice(['1', '2']))
        return '袋', Decimal('1.00')

    # -------------------------------------------------- 一个家庭的全部流水
    def run_family(self, ctx, meals, singles, actives, trips, favorites, staples, manual_stock, log_days, skip_notes,
                   plan=None, reviewers=None):
        S, ref = self.S, self.ref
        # ---- 1. 餐 / 单菜对象
        M = []
        for spec in meals:
            t = mk(*spec['when'])
            dishes = self.norm_dishes(spec['dishes'], spec['cook'] if spec['cook'] in ctx.profiles else 'me')
            M.append(dict(spec=spec, t=t, cook=spec['cook'], dishes=dishes, single=False, menu=None))
        for spec in singles:
            t = mk(*spec['when'], jit=2)
            M.append(dict(spec=spec, t=t, cook=spec['cook'], single=True, menu=None,
                          dishes=[dict(name=spec['dish'], id=ref.dish_id(spec['dish']), who=spec['cook'], note=None)]))
        M.sort(key=lambda m: m['t'])
        trip_objs = []
        for tr in trips:
            trip_objs.append(dict(spec=tr, t=mk(*tr['when'], jit=2)))
        trip_objs.sort(key=lambda o: o['t'])
        for m in M:
            cand = [o for o in trip_objs if o['t'] < m['t'] - dt.timedelta(minutes=20)]
            if not cand:
                raise ValueError('餐没有对应的采购行程：%s' % m['t'])
            m['trip'] = cand[-1]
            cand[-1].setdefault('meals', []).append(m)

        # ---- 2. 一周餐程（草稿 + 槽位 + 应用后生成的食集）
        if plan:
            self.make_plan(ctx, M, plan)

        # ---- 3. 其余食集（按时间顺序）
        for m in M:
            if m['single'] or m['menu'] is not None:
                continue
            spec = m['spec']
            created = mk(*spec['created'], jit=2) if spec.get('created') else None
            if created is None:
                created = m['t'] - dt.timedelta(minutes=R.randint(40, 900))
                if m['trip']['spec']['kind'] == 'menu':
                    created = min(created, m['trip']['t'] - dt.timedelta(minutes=R.randint(40, 120)))
                lo = ctx.min_created + dt.timedelta(minutes=R.randint(0, 20), seconds=R.randint(1, 58))
                if created < lo:
                    created = lo
                created = chk(_fix(created))
            if spec.get('together'):
                created = mk(10, 8, 20, 55, jit=2)
            m['menu'] = self.make_menu(ctx, spec['name'], spec['kind'], spec['n'], created, 'DONE', m['t'],
                                       target=None)
            m['created'] = created
            if spec.get('together'):
                continue  # 点菜在 together 环节写
            self.add_menu_dishes(ctx, m['menu'], m['dishes'])

        for a in actives:
            t = mk(*a['when'], jit=1)
            dishes = self.norm_dishes(a['dishes'], a['who'])
            menu = self.make_menu(ctx, a['name'], a['kind'], a['n'], t, 'ACTIVE')
            self.add_menu_dishes(ctx, menu, dishes)
            a['_menu'], a['_t'], a['_dishes'] = menu, t, dishes
            for ing in ref.meal_ings([d['id'] for d in dishes]):
                status = 'PENDING'
                for dn, stt in a.get('prep', []):
                    if ing in ref.di[ref.dish_id(dn)]:
                        status = stt
                S.ins('menu_prep_status', menu_id=menu, ingredient_id=ing, status=status,
                      update_time=secs(t, 20, 600), family_id=ctx.fam)

        # ---- 4. together（邀请 / 加入 / 点菜 / 活动）
        for m in M:
            if m['spec'].get('together'):
                self.make_together(ctx, m)

        # ---- 5. 库存 / 采购 / 做饭 / 通知 事件模拟
        self.simulate(ctx, M, actives, trip_objs, staples, manual_stock)

        # ---- 6. 点评
        for m in M:
            if m['single']:
                continue
            self.make_reviews(ctx, m, reviewers or {})

        # ---- 7. 收藏
        for who, names in favorites.items():
            for n in names:
                S.ins('favorite', member_id=ctx.profiles[who], dish_id=ref.dish_id(n), family_id=ctx.fam)

        # ---- 8. 饮食记录
        self.make_daily_logs(ctx, M, log_days, skip_notes)

    # -------------------------------------------------- 一周餐程
    def make_plan(self, ctx, M, plan):
        S, ref = self.S, self.ref
        start = dt.date(2026, *C.PLAN_WEEK_START)
        apply_t = mk(*C.PLAN_APPLY_AT, jit=1)
        draft_t = chk(apply_t - dt.timedelta(minutes=R.randint(6, 11), seconds=R.randint(1, 50)))
        days = [start + dt.timedelta(days=i) for i in range(7)]
        by_day = {}
        for m in M:
            if m['spec'].get('plan'):
                by_day[m['t'].date()] = m
        stale = {dt.date(2026, *k): v for k, v in C.PLAN_STALE.items()}
        label = {0: '周一', 1: '周二', 2: '周三', 3: '周四', 4: '周五', 5: '周六', 6: '周日'}
        # 草稿先插（applied_menu_ids 用 CONCAT 引用稍后生成的食集变量）
        slot_rows, menu_vars = [], []
        for d in days:
            if d in by_day:
                dishes = by_day[d]['dishes']
            elif d in stale:
                dishes = self.norm_dishes(stale[d], 'me')
            else:
                continue
            fixed = [dict(x, who='me', note=None) for x in dishes]
            menu_name = '%d.%d %s · 一周餐程' % (d.month, d.day, label[d.weekday()])
            kind = '工作日' if d.weekday() < 5 else '周末'
            menu = self.make_menu(ctx, menu_name, kind, 2, apply_t, 'DONE' if d in by_day else 'ACTIVE',
                                  by_day[d]['t'] if d in by_day else None, target=ctx.profiles['me'])
            if d in by_day:
                by_day[d]['menu'] = menu
                by_day[d]['created'] = apply_t
                by_day[d]['cook_dishes'] = fixed
            self.add_menu_dishes(ctx, menu, fixed, note_override='晚餐')
            menu_vars.append(menu)
            slot_rows.append((d, fixed))
        total = sum(len(x[1]) for x in slot_rows)
        req = ('CONCAT(\'{"week":{"start":"%s"},"meals":["DINNER"],"servings":2,"memberIds":[\', %s, \',\', %s, '
               '\'],"weekdayMaxMinutes":40,"budgetFen":null,"preferExpiring":true}\')'
               % (start.isoformat(), ctx.profiles['me'], ctx.profiles['hu']))
        applied = Raw('CONCAT(%s)' % ", ',', ".join(str(v) for v in menu_vars))
        check = json.dumps(dict(passed=True, rounds=1, notes=['共 %d 道菜，一周内不重样' % total, '工作日单道菜均 ≤40 分钟']),
                           ensure_ascii=False, separators=(',', ':'))
        draft = S.ins('meal_plan_draft', member_id=ctx.profiles['me'], week_start=start,
                      week_end=start + dt.timedelta(days=6), request_json=Raw(req),
                      constraints_json=json.dumps(['2 人', '工作日 ≤40 分钟', '优先用快到期'], ensure_ascii=False),
                      check_json=check, status='APPLIED', applied_menu_ids=applied,
                      create_time=draft_t, update_time=apply_t, family_id=ctx.fam)
        for d, fixed in slot_rows:
            for i, x in enumerate(fixed):
                badge = 'EXPIRING' if (d.day == 22 and i == 1) else None
                S.ins('meal_plan_slot', draft_id=draft, plan_date=d, meal='DINNER', dish_id=x['id'],
                      badges=badge, family_id=ctx.fam)

    # -------------------------------------------------- 聚餐
    def make_together(self, ctx, m):
        S = self.S
        menu, spec = m['menu'], m['spec']
        fr = ctx.accounts['fr']
        hu = ctx.accounts['hu']
        me = ctx.accounts['me']
        hu_nick = Raw("(SELECT COALESCE(NULLIF(nickname,''),'老公') FROM account WHERE id=%s)" % hu)
        me_nick = Raw("(SELECT COALESCE(NULLIF(nickname,''),'我') FROM account WHERE id=%s)" % me)
        inv_t = mk(10, 8, 21, 12, jit=1)
        S.ins('menu_invite', menu_id=menu, code='K7M3QX', token=hashlib.md5(b'gudu-demo-invite-1').hexdigest(),
              created_by=me, create_time=inv_t, family_id=ctx.fam, expires_at=inv_t + dt.timedelta(days=7), revoked=0)
        acc = {'fr': (fr, '小林'), 'hu': (hu, hu_nick)}
        # 房主先点自己的三道菜（本家成员，added_by 为房主档案）
        own = [d for d in m['dishes'] if d['who'] == 'me']
        self.add_menu_dishes(ctx, menu, own)
        t_fr = mk(10, 8, 21, 31, jit=1)
        t_hu = mk(10, 8, 21, 52, jit=1)
        last = dict(me=mk(10, 10, 20, 48, jit=1), fr=mk(10, 10, 19, 22, jit=1), hu=mk(10, 10, 19, 41, jit=1))
        S.ins('menu_join', menu_id=menu, member_id=me, guest_key=None, nickname=me_nick, last_active_at=last['me'],
              family_id=ctx.fam)
        S.ins('menu_join', menu_id=menu, member_id=fr, guest_key=None, nickname='小林', last_active_at=last['fr'],
              family_id=ctx.fam)
        S.ins('menu_join', menu_id=menu, member_id=hu, guest_key=None, nickname=hu_nick, last_active_at=last['hu'],
              family_id=ctx.fam)
        ds = {d['name']: d for d in m['dishes']}

        def add(who, name, note, t):
            d = ds[name]
            self.add_menu_dishes(ctx, menu, [dict(d, who=who, note=note)], together_accounts=acc)
            nick = '小林' if who == 'fr' else hu_nick
            S.ins('menu_activity', menu_id=menu, member_id=acc[who][0], nickname=nick, action='add',
                  dish_id=d['id'], dish_name=name, create_time=t, family_id=ctx.fam)

        add('fr', '超下饭的麻婆豆腐', '我最爱吃这个，不放香菜哈哈', secs(t_fr, 190, 260))
        # 小林先加了一道自己输入的菜，第二天又删掉换成拍黄瓜
        t_c = secs(t_fr, 280, 330)
        S.ins('menu_activity', menu_id=menu, member_id=fr, nickname='小林', action='add', dish_id=None,
              dish_name='凉拌木耳', create_time=t_c, family_id=ctx.fam)
        add('hu', '酸辣土豆丝', '下饭菜来一个', secs(t_hu, 20, 90))
        t_rm = mk(10, 9, 12, 27, jit=1)
        S.ins('menu_activity', menu_id=menu, member_id=fr, nickname='小林', action='remove', dish_id=None,
              dish_name='凉拌木耳', create_time=t_rm, family_id=ctx.fam)
        add('fr', '凉拌黄瓜', '清爽点解解辣', secs(t_rm, 25, 70))

    # -------------------------------------------------- 模拟事件
    def simulate(self, ctx, M, actives, trips, staples, manual_stock):
        S, ref = self.S, self.ref
        ev = []   # (t, seq, kind, payload)
        seq = [0]

        def push(t, kind, payload):
            seq[0] += 1
            ev.append((t, seq[0], kind, payload))

        # 初始库存：一次性补登
        base_t = ctx.staples_at
        for i, name in enumerate(staples):
            push(base_t + dt.timedelta(seconds=25 * i + R.randint(1, 20)), 'staple', name)
        for (when, name, note) in manual_stock:
            push(mk(*when, jit=1), 'manual', (name, note))
        for o in trips:
            push(o['t'], 'trip', o)
        for m in M:
            push(m['t'], 'cook', m)
        # 今天的清单（进行中食集）
        for a in actives:
            if a['k'] == 'today':
                push(a['_t'] + dt.timedelta(minutes=1), 'todaylist', a)
        # 每日 08:00 扫描
        d = dt.date(2026, 9, 27)
        while d <= dt.date(2026, 10, 26):
            if d >= ctx.scan_from:
                push(dt.datetime(2026, d.month, d.day, 8, 0, R.randint(1, 9)), 'scan', d)
            d += dt.timedelta(days=1)
        ev.sort(key=lambda e: (e[0], e[1]))
        discards = []
        i = 0
        while i < len(ev):
            t, _, kind, p = ev[i]
            i += 1
            if kind == 'staple':
                ing = ref.ing_id(p)
                self.stock_add(ctx, ing, 'manual', '旧库存补登', None, t, t.date() + dt.timedelta(days=180))
            elif kind == 'manual':
                ing = ref.ing_id(p[0])
                self.stock_add(ctx, ing, 'manual', p[1], None, t,
                               t.date() + dt.timedelta(days=SHELF_DAYS.get(ref.cat(ing), 7) if ref.cat(ing) != 2 else 30))
            elif kind == 'trip':
                self.do_trip(ctx, p)
            elif kind == 'cook':
                self.do_cook(ctx, p)
            elif kind == 'todaylist':
                self.do_today_list(ctx, p)
            elif kind == 'scan':
                self.do_scan(ctx, t, p, ev, i, discards)
            elif kind == 'discard':
                self.do_discard(ctx, t, p)
            # 动态插入的 discard 事件：重新排序剩余部分
            if discards:
                for dd in discards:
                    ev.append(dd)
                discards.clear()
                rest = sorted(ev[i:], key=lambda e: (e[0], e[1]))
                ev[i:] = rest
        # 终态库存
        for ing, st in ctx.state.items():
            S.ins('ingredient_stock', ingredient_id=ing, level=st['level'], best_before=st['bb'],
                  opened_at=st['opened'], update_time=st['upd'], family_id=ctx.fam)

    def do_scan(self, ctx, t, d, ev, i, discards):
        ref = self.ref
        for ing, st in sorted(ctx.state.items()):
            if st['level'] == 'NONE' or st['bb'] is None:
                continue
            left = (st['bb'] - d).days
            if 0 <= left <= 3 and ctx.notified[ing] < 2:
                ctx.notified[ing] += 1
                read = 1 if d <= dt.date(2026, 10, 24) else R.choice([0, 0, 1])
                self.S.ins('notification', recipient_account_id=None, recipient_profile_id=ctx.notify_to, member_id=None,
                           type='expiry', channel='in_app', title='食材临期提醒',
                           content='「%s」将在 3 天内过期' % ref.name(ing), ref_id=None, is_read=read,
                           create_time=secs(t, 1, 25), family_id=None)
            elif left < 0:
                when = dt.datetime(2026, d.month, d.day, 9, 10, R.randint(1, 58)) if not (
                    dt.date(2026, 10, 1) <= d <= dt.date(2026, 10, 3) and ctx.away) else dt.datetime(
                    2026, 10, 4, 20, 40, R.randint(1, 58))
                if d == dt.date(2026, 10, 27):
                    continue
                discards.append((when, 10 ** 6 + ing, 'discard', ing))

    def do_discard(self, ctx, t, ing):
        st = ctx.state.get(ing)
        if not st or st['level'] == 'NONE':
            return
        t = chk(_fix(t))
        self.stock_log(ctx, ing, 'manual', st['level'], 'NONE', st['bb'], '过期了，扔掉', None, t)
        self.set_state(ctx, ing, 'NONE', None, t)

    def do_trip(self, ctx, o):
        S, ref = self.S, self.ref
        spec, t = o['spec'], o['t']
        meals = o.get('meals', [])
        needed, last_use = [], {}
        for m in meals:
            for ing in ref.meal_ings([d['id'] for d in m['dishes']]):
                if ing not in needed:
                    needed.append(ing)
                last_use[ing] = max(last_use.get(ing, m['t'].date()), m['t'].date())
        extra_ings = [ref.ing_id(n) for n in spec.get('extra_ing', [])]
        for e in extra_ings:
            if e not in needed:
                needed.append(e)
                last_use[e] = t.date()
        buy = []
        for ing in needed:
            st = ctx.state.get(ing)
            if ing in extra_ings:
                buy.append(ing)
            elif st is None or st['level'] == 'NONE':
                buy.append(ing)
            elif st['bb'] and st['bb'] < last_use[ing]:
                buy.append(ing)
            elif st['level'] == 'LOW' and ref.cat(ing) != 7:
                buy.append(ing)
        buy.sort(key=lambda i: (ref.cat(i), ref.name(i)))
        if not buy and not spec.get('extras'):
            return
        first_menu = None
        if spec['kind'] == 'menu':
            first_menu = next((m['menu'] for m in meals if m['menu'] is not None), None)
        menu_created = None
        if first_menu is not None:
            menu_created = next(m['created'] for m in meals if m['menu'] is first_menu)
        list_t = chk(_fix(t - dt.timedelta(minutes=R.randint(15, 100), seconds=R.randint(1, 50))))
        if menu_created and list_t < menu_created:
            list_t = after(menu_created, 3, 20)
        if list_t >= t:
            list_t = t - dt.timedelta(minutes=5)
        sl = S.ins('shopping_list', source_plan_id=None, source_menu_id=first_menu if spec['kind'] == 'menu' else None,
                   name=spec.get('name'), time_range='menu' if spec['kind'] == 'menu' else 'custom',
                   start_date=list_t.date(), end_date=list_t.date(), created_at=list_t, deleted=0, family_id=ctx.fam)
        if spec['kind'] == 'menu':
            S.ins('notification', recipient_account_id=None, recipient_profile_id=ctx.notify_to, member_id=None,
                  type='shopping', channel='in_app', title='采购清单已生成',
                  content='菜单已生成采购清单，共 %d 项' % len(buy), ref_id=None,
                  is_read=1 if t.date() <= dt.date(2026, 10, 24) else 0, create_time=secs(list_t, 1, 20),
                  family_id=None)
        tick = t
        for ing in buy:
            unit, amount = self.unit_for(ing)
            item = S.ins('shopping_item', list_id=sl, ingredient_id=ing, total_amount=Decimal('0.00'), unit_id=None,
                         purchase_category_id=ref.cat(ing) or None, purchased=1, purchase_amount=amount,
                         purchase_unit_id=dict_id('purchase_unit', unit), reference_grams=None, custom_name=None,
                         family_id=ctx.fam)
            tick = chk(_fix(tick + dt.timedelta(seconds=R.randint(25, 170))))
            cat = ref.cat(ing)
            shelf = SHELF_DAYS.get(cat, 7)
            bb = max(t.date() + dt.timedelta(days=shelf), last_use[ing] + dt.timedelta(days=1)) if cat != 7 else \
                t.date() + dt.timedelta(days=shelf)
            self.stock_add(ctx, ing, 'purchase', None, item, tick, bb)
        for name in spec.get('extras', []):
            done = name not in spec.get('unticked_extras', [])
            S.ins('shopping_item', list_id=sl, ingredient_id=None, total_amount=Decimal('0.00'), unit_id=None,
                  purchase_category_id=None, purchased=1 if done else 0, purchase_amount=None, purchase_unit_id=None,
                  reference_grams=None, custom_name=name, family_id=ctx.fam)

    def do_today_list(self, ctx, a):
        S, ref = self.S, self.ref
        t = chk(_fix(a['_t'] + dt.timedelta(minutes=R.randint(3, 9), seconds=R.randint(1, 50))))
        need = ref.meal_ings([d['id'] for d in a['_dishes']])
        missing = [i for i in need if ctx.state.get(i) is None or ctx.state[i]['level'] == 'NONE']
        missing.sort(key=lambda i: (ref.cat(i), ref.name(i)))
        sl = S.ins('shopping_list', source_plan_id=None, source_menu_id=a['_menu'], name=None, time_range='menu',
                   start_date=t.date(), end_date=t.date(), created_at=t, deleted=0, family_id=ctx.fam)
        S.ins('notification', recipient_account_id=None, recipient_profile_id=ctx.notify_to, member_id=None,
              type='shopping', channel='in_app', title='采购清单已生成',
              content='菜单已生成采购清单，共 %d 项' % len(missing), ref_id=None, is_read=0,
              create_time=secs(t, 1, 15), family_id=None)
        for ing in missing:
            unit, amount = self.unit_for(ing)
            S.ins('shopping_item', list_id=sl, ingredient_id=ing, total_amount=Decimal('0.00'), unit_id=None,
                  purchase_category_id=ref.cat(ing) or None, purchased=0, purchase_amount=amount,
                  purchase_unit_id=dict_id('purchase_unit', unit), reference_grams=None, custom_name=None,
                  family_id=ctx.fam)

    def do_cook(self, ctx, m):
        S, ref = self.S, self.ref
        t = m['t']
        ings = ref.meal_ings([d['id'] for d in m['dishes']])
        used, partial = [], []
        later = set()
        for lm in m['trip'].get('meals', []):
            if lm['t'] > t:
                later.update(ref.meal_ings([d['id'] for d in lm['dishes']]))
        for ing in ings:
            st = ctx.state.get(ing)
            if st is None or st['level'] == 'NONE':
                raise ValueError('做饭时缺料：%s %s' % (t, ref.name(ing)))
            if st['bb'] and st['bb'] < t.date():
                raise ValueError('做饭时过期：%s %s' % (t, ref.name(ing)))
            cond = ref.cat(ing) == 7
            if ing not in later and ((not cond and R.random() < 0.45) or (cond and R.random() < 0.04)):
                used.append(ing)
            else:
                partial.append(ing)
        ct = secs(t, 3, 30)
        for ing in used:
            st = ctx.state[ing]
            self.stock_log(ctx, ing, 'cook', st['level'], 'NONE', st['bb'], None, None, ct)
            self.set_state(ctx, ing, 'NONE', None, ct)
        for ing in partial:
            st = ctx.state[ing]
            if st['level'] == 'ENOUGH':
                self.stock_log(ctx, ing, 'cook_partial', 'ENOUGH', 'LOW', st['bb'], None, None, ct)
                self.set_state(ctx, ing, 'LOW', st['bb'], ct, opened=ct)
        memo_parts = []
        if used:
            memo_parts.append('用完:' + ','.join(str(i) for i in used))
        if partial:
            memo_parts.append('用了一些:' + ','.join(str(i) for i in partial))
        memo = ';'.join(memo_parts) or None
        event_id = str(__import__('uuid').UUID(int=R.getrandbits(128), version=4))
        who = ctx.profiles[m['cook']]
        m['records'] = {}
        for d in m['dishes']:
            if d['name'] not in [x['name'] for x in m.get('cook_dishes', m['dishes'])]:
                continue
            rec = S.ins('cooking_record', dish_id=d['id'], menu_id=None if m['single'] else m['menu'], member_id=who,
                        cooked_at=t, note=None, create_time=ct, serving_factor=Decimal('1.00'),
                        source='dish' if m['single'] else 'menu', memo=memo, event_id=event_id, family_id=ctx.fam)
            m['records'][d['name']] = rec
        if m['single']:
            return
        menu = m['menu']
        for ing in ings:
            S.ins('menu_prep_status', menu_id=menu, ingredient_id=ing, status='READY', update_time=ct,
                  family_id=ctx.fam)
        for ing in used:
            S.ins('menu_cook_usage', menu_id=menu, ingredient_id=ing, action='used_up', create_time=ct,
                  family_id=ctx.fam)
        for ing in partial:
            S.ins('menu_cook_usage', menu_id=menu, ingredient_id=ing, action='partial', create_time=ct,
                  family_id=ctx.fam)

    # -------------------------------------------------- 点评
    def make_reviews(self, ctx, m, reviewers):
        S, ref = self.S, self.ref
        for (who, target, stars, text, dims, delay) in m['spec'].get('reviews', []):
            t = after(m['t'], max(delay - 4, 1), delay + 4)
            names = [d['name'] for d in m['dishes']]
            if target is not None and target not in names:
                raise ValueError('点评的菜不在这顿饭里：%s' % target)
            prof = ctx.profiles[who]
            rv = S.ins('review', dish_id=ref.dish_id(target) if target else None,
                       menu_id=m['menu'] if target is None else None, member_id=prof, star_rating=stars, text=text,
                       images=None, create_time=t, deleted=0, family_id=ctx.fam)
            for dim, score in (dims or {}).items():
                S.ins('review_score', review_id=rv, dimension_id=dict_id('review_dimension', dim), score=score,
                      family_id=ctx.fam)

    # -------------------------------------------------- 饮食记录
    def make_daily_logs(self, ctx, M, log_days, skip_notes):
        S, ref = self.S, self.ref
        by_day = defaultdict(list)
        for m in M:
            by_day[(m['t'].month, m['t'].day)].extend(m['dishes'])
        for who in ('me', 'hu', 'fr'):
            if who not in ctx.profiles:
                continue
            days = log_days.get(who, set())
            for day in sorted(set(days) | {k for k, v in skip_notes.items() if any(w == who for w, _ in v)}):
                note = None
                items = []
                for w, n in skip_notes.get(day, []):
                    if w == who:
                        note = n
                if day in days and day in by_day:
                    note = note or R.choice(C.LOG_NOTES)
                    seen = set()
                    for d in by_day[day]:
                        if d['id'] in seen:
                            continue
                        seen.add(d['id'])
                        items.append(d['id'])
                t = chk(_fix(dt.datetime(2026, day[0], day[1], R.randint(20, 22), R.randint(5, 55), R.randint(1, 58))))
                lg = S.ins('daily_log', member_id=ctx.profiles[who], date=dt.date(2026, *day), note=note,
                           create_time=t, deleted=0, family_id=ctx.fam)
                for did in items:
                    S.ins('daily_log_item', log_id=lg, dish_id=did, ingredient_id=None,
                          amount=Decimal(R.choice(['0.50', '0.80', '1.00', '1.00', '1.20'])),
                          serving_factor=Decimal('1.00'), family_id=ctx.fam)
                if day in skip_notes and not items and who == 'me' and day == (10, 9):
                    S.ins('daily_log_item', log_id=lg, dish_id=None, ingredient_id=ref.ing_id('牛奶'),
                          amount=Decimal('200.00'), serving_factor=Decimal('1.00'), family_id=ctx.fam)


# ====================================================================== 顶层
def build(refdir, ids, cipher, backdate=True, health=True):
    ref = Ref(refdir)
    B = Builder(ref, cipher, backdate)
    S = B.S
    ME_A, ME_F, ME_P, HU_A = (Raw('@ME_ACCOUNT_ID'), Raw('@ME_FAMILY_ID'), Raw('@ME_PROFILE_ID'),
                              Raw('@HUSBAND_ACCOUNT_ID'))

    # ---------------- 回溯我们这边已有账号的创建时间（让「用了一个月」自洽）
    t_me_acc = dt.datetime(2026, 9, 27, 8, 52, 37)
    t_me_fam = dt.datetime(2026, 9, 27, 8, 53, 14)
    t_hu_acc = dt.datetime(2026, 9, 27, 19, 46, 21)
    t_hu_join = dt.datetime(2026, 9, 27, 20, 41, 53)
    if backdate:
        for tbl, col, key, where_id, val in [
                ('account', 'created_at', 'id', ME_A, t_me_acc), ('family', 'created_at', 'id', ME_F, t_me_fam),
                ('member', 'create_time', 'id', ME_P, dt.datetime(2026, 9, 27, 8, 53, 15)),
                ('account', 'created_at', 'id', HU_A, t_hu_acc)]:
            S.raw("INSERT INTO demo_seed_restore (table_name, row_id, column_name, old_value, new_value) "
                  "SELECT '%s', %s, '%s', %s, '%s' FROM %s WHERE %s = %s AND %s > '%s';"
                  % (tbl, key, col, col, val, tbl, key, where_id, col, val))
            S.raw("UPDATE %s SET %s = '%s' WHERE %s = %s AND %s > '%s';" % (tbl, col, val, key, where_id, col, val))
        S.raw("INSERT INTO demo_seed_restore (table_name, row_id, column_name, old_value, new_value) "
              "SELECT 'family_membership', id, 'joined_at', joined_at, '2026-09-27 08:53:16' FROM family_membership "
              "WHERE family_id = @ME_FAMILY_ID AND account_id = @ME_ACCOUNT_ID AND joined_at > '2026-09-27 08:53:16';")
        S.raw("UPDATE family_membership SET joined_at = '2026-09-27 08:53:16' WHERE family_id = @ME_FAMILY_ID "
              "AND account_id = @ME_ACCOUNT_ID AND joined_at > '2026-09-27 08:53:16';")

    # ---------------- 老公加入我们家
    hu_mem = S.ins('family_membership', family_id=ME_F, account_id=HU_A, role='MEMBER', joined_at=t_hu_join)
    hu_prof = S.ins('member', name='老公', role_tags='34', is_admin=0, family_id=ME_F, account_id=HU_A,
                    create_time=secs(t_hu_join, 1, 4), deleted=0)
    _ = hu_mem
    # 登录默认进入我们家（记录原值，清除时还原）
    S.raw("INSERT INTO demo_seed_restore (table_name, row_id, column_name, old_value, new_value) "
          "SELECT 'account', id, 'last_family_id', last_family_id, @ME_FAMILY_ID FROM account "
          "WHERE id = @HUSBAND_ACCOUNT_ID;")
    S.raw("UPDATE account SET last_family_id = @ME_FAMILY_ID WHERE id = @HUSBAND_ACCOUNT_ID;")

    # ---------------- 朋友：账号 / 家庭 / 档案
    t_fr_acc = dt.datetime(2026, 9, 29, 20, 33, 18)
    t_fr_fam = dt.datetime(2026, 9, 29, 20, 34, 2)
    fr_acc = S.ins('account', nickname='小林', platform_role='USER', status='ACTIVE', last_family_id=None,
                   created_at=t_fr_acc)
    fr_fam = S.ins('family', name='小林家', owner_account_id=fr_acc, status='ACTIVE', version=0, created_at=t_fr_fam)
    S.ins('family_membership', family_id=fr_fam, account_id=fr_acc, role='OWNER', joined_at=t_fr_fam)
    fr_prof = S.ins('member', name='小林', role_tags='34', is_admin=0, family_id=fr_fam, account_id=fr_acc,
                    create_time=secs(t_fr_fam, 1, 3), deleted=0)
    S.raw("UPDATE account SET last_family_id = %s WHERE id = %s;" % (fr_fam, fr_acc))

    # ---------------- 健康档案（加密）+ 单独同意（同意早于写入）
    if health and cipher is not None:
        t_c1 = dt.datetime(2026, 9, 27, 21, 8, 44)
        S.ins('consent_record', account_id=HU_A, doc_type='sensitive_health', doc_version=SENSITIVE_DOC_VERSION,
              granted=1, channel='mp-weixin', client_ip=None, created_at=t_c1)
        S.raw("UPDATE member SET health_profile = %s, goal = 'LOSE', activity_level = 'LIGHT', "
              "health_consent_account_id = @HUSBAND_ACCOUNT_ID WHERE id = %s;"
              % (q(cipher.encrypt(health_json(HUSBAND_HEALTH))), hu_prof))
        t_c2 = dt.datetime(2026, 9, 29, 21, 2, 9)
        S.ins('consent_record', account_id=fr_acc, doc_type='sensitive_health', doc_version=SENSITIVE_DOC_VERSION,
              granted=1, channel='mp-weixin', client_ip=None, created_at=t_c2)
        S.raw("UPDATE member SET health_profile = %s, goal = 'MAINTAIN', activity_level = 'MODERATE', "
              "health_consent_account_id = %s WHERE id = %s;"
              % (q(cipher.encrypt(health_json(FRIEND_HEALTH))), fr_acc, fr_prof))

    # ---------------- 我们家
    me = Fam('me', ME_F, dict(me=ME_P, hu=hu_prof), dict(me=ME_A, hu=HU_A, fr=fr_acc), ME_P)
    me.staples_at = dt.datetime(2026, 9, 27, 10, 12, 8)
    me.min_created = dt.datetime(2026, 9, 27, 10, 40, 0)
    me.scan_from = dt.date(2026, 9, 28)
    me.away = True
    B.run_family(me, C.ME_MEALS, C.ME_SINGLE, C.ME_ACTIVE, C.ME_TRIPS, C.FAVORITES, C.INIT_STAPLES, C.MANUAL_STOCK,
                 dict(me=C.ME_LOG_DAYS, hu=C.HU_LOG_DAYS), C.DAILY_SKIP_NOTES, plan=True)

    # ---------------- 小林家
    fr = Fam('fr', fr_fam, dict(fr=fr_prof), dict(fr=fr_acc), fr_prof)
    fr.staples_at = dt.datetime(2026, 10, 2, 20, 15, 31)
    fr.min_created = dt.datetime(2026, 10, 2, 20, 30, 0)
    fr.scan_from = dt.date(2026, 10, 3)
    fr.away = False
    B.run_family(fr, C.FR_MEALS, [], [], C.FR_TRIPS, {'fr': C.FR_FAVORITES}, C.FR_INIT_STAPLES, [],
                 dict(fr=C.FR_LOG_DAYS), {})
    return ref, B


# ====================================================================== 输出
def validations(ref):
    dish_ids = sorted({i for m in C.ME_MEALS + C.FR_MEALS for i in [ref.dish_id(n if isinstance(n, str) else n[0])
                                                                      for n in m['dishes']]}
                      | {ref.dish_id(s['dish']) for s in C.ME_SINGLE}
                      | {ref.dish_id(n if isinstance(n, str) else n[0]) for a in C.ME_ACTIVE for n in a['dishes']}
                      | {ref.dish_id(n) for v in C.PLAN_STALE.values() for n in v}
                      | {ref.dish_id(n) for v in C.FAVORITES.values() for n in v}
                      | {ref.dish_id(n) for n in C.FR_FAVORITES})
    ing_ids = sorted(ref.meal_ings(dish_ids) + [ref.ing_id(n) for n in
                                                 C.INIT_STAPLES + C.FR_INIT_STAPLES + ['牛奶', '北豆腐', '菠菜', '草莓', '腊肉', '香肠']])
    ing_ids = sorted(set(ing_ids))
    v = []

    def need(count_sql, cmp, msg):
        v.append("SELECT COUNT(*) INTO v_n FROM %s;" % count_sql)
        v.append("IF NOT (v_n %s) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '%s'; END IF;" % (cmp, msg))

    need("account WHERE id = @ME_ACCOUNT_ID AND status = 'ACTIVE'", '= 1', '参数错误：ME_ACCOUNT_ID 不是有效账号')
    need("account WHERE id = @HUSBAND_ACCOUNT_ID AND status = 'ACTIVE'", '= 1', '参数错误：HUSBAND_ACCOUNT_ID 不是有效账号')
    need("family WHERE id = @ME_FAMILY_ID AND status = 'ACTIVE'", '= 1', '参数错误：ME_FAMILY_ID 不是有效家庭')
    need("family_membership WHERE family_id = @ME_FAMILY_ID AND account_id = @ME_ACCOUNT_ID AND role = 'OWNER'",
         '= 1', '参数错误：ME_ACCOUNT_ID 不是该家庭的 OWNER')
    need("member WHERE id = @ME_PROFILE_ID AND family_id = @ME_FAMILY_ID AND account_id = @ME_ACCOUNT_ID AND deleted = 0",
         '= 1', '参数错误：ME_PROFILE_ID 不是该账号在该家庭的成员档案')
    need("family_membership WHERE family_id = @ME_FAMILY_ID AND account_id = @HUSBAND_ACCOUNT_ID", '= 0',
         '老公已经在这个家庭里了')
    need("member WHERE family_id = @ME_FAMILY_ID AND account_id = @HUSBAND_ACCOUNT_ID", '= 0', '老公在这个家庭已有成员档案')
    need("family_membership WHERE family_id = @ME_FAMILY_ID", '< 18', '家庭成员数已接近上限')
    need("dish WHERE family_id IS NULL AND deleted = 0 AND id IN (%s)" % ','.join(map(str, dish_ids)),
         '= %d' % len(dish_ids), '公共菜谱缺失：当前库与参考数据不一致')
    need("ingredient WHERE deleted = 0 AND id IN (%s)" % ','.join(map(str, ing_ids)), '= %d' % len(ing_ids),
         '食材库缺失：当前库与参考数据不一致')
    need("sys_dict WHERE dict_group = 'review_dimension' AND name IN (%s)" % ','.join("'%s'" % d for d in DIMENSIONS),
         '= %d' % len(DIMENSIONS), '点评维度字典缺失')
    need("sys_dict WHERE dict_group = 'purchase_unit' AND name IN (%s)" % ','.join("'%s'" % d for d in PURCHASE_UNITS),
         '= %d' % len(PURCHASE_UNITS), '采购单位字典缺失')
    need("sys_dict WHERE dict_group = 'menu_type' AND name IN ('日常','家宴','节日')", '= 3', '菜单类型字典缺失')
    need("ingredient_stock WHERE family_id = @ME_FAMILY_ID", '= 0', '我们家已有库存记录：请先确认是否需要清理再造数')
    need("favorite WHERE family_id = @ME_FAMILY_ID AND member_id = @ME_PROFILE_ID", '= 0',
         '我们家已有收藏记录：请先确认是否需要清理再造数')
    need("menu_invite WHERE code = 'K7M3QX'", '= 0', '邀请口令冲突')
    return v


def write_sql(path, refdir, ids, cipher, backdate, health):
    R.seed(SEED)
    ref, B = build(refdir, ids, cipher, backdate, health)
    head = ['-- 咕嘟小食单 · 演示数据（生成于固定种子 %d，勿手改）' % SEED,
            '-- 用法见 README.md；清除用 cleanup_demo_seed.sql',
            'SET NAMES utf8mb4;',
            'SET @ME_ACCOUNT_ID = %d;' % ids['ME_ACCOUNT_ID'],
            'SET @ME_FAMILY_ID = %d;' % ids['ME_FAMILY_ID'],
            'SET @ME_PROFILE_ID = %d;' % ids['ME_PROFILE_ID'],
            'SET @HUSBAND_ACCOUNT_ID = %d;' % ids['HUSBAND_ACCOUNT_ID'],
            'CREATE TABLE IF NOT EXISTS demo_seed_registry (seq BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, '
            'table_name VARCHAR(64) NOT NULL, row_id BIGINT NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, '
            'KEY idx_demo_registry_tbl (table_name, row_id)) DEFAULT CHARSET=utf8mb4;',
            'CREATE TABLE IF NOT EXISTS demo_seed_restore (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, '
            'table_name VARCHAR(64) NOT NULL, row_id BIGINT NOT NULL, column_name VARCHAR(64) NOT NULL, '
            'old_value VARCHAR(64) NULL, new_value VARCHAR(64) NULL) DEFAULT CHARSET=utf8mb4;',
            'DROP PROCEDURE IF EXISTS demo_seed_run;',
            'DELIMITER //',
            'CREATE PROCEDURE demo_seed_run()',
            'BEGIN',
            'DECLARE v_n BIGINT DEFAULT 0;',
            'DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;',
            "SELECT COUNT(*) INTO v_n FROM demo_seed_registry;",
            "IF v_n > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '已经造过演示数据：请先执行 cleanup_demo_seed.sql'; END IF;"]
    head += validations(ref)
    head.append('START TRANSACTION;')
    tail = ['COMMIT;', 'END//', 'DELIMITER ;', 'CALL demo_seed_run();', 'DROP PROCEDURE demo_seed_run;',
            "SELECT table_name, COUNT(*) AS rows_inserted FROM demo_seed_registry GROUP BY table_name ORDER BY table_name;"]
    with open(path, 'w', encoding='utf-8') as f:
        f.write('\n'.join(head + B.S.lines + tail) + '\n')
    return B


def make_cipher():
    keys = parse_keys(os.environ.get('GUDU_FIELD_CRYPTO_KEYS', ''))
    if not keys:
        return None
    return HealthCipher(keys, int(os.environ.get('GUDU_FIELD_CRYPTO_ACTIVE', '1')))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--ref-dir', help='参考数据目录（ref-dish.tsv / ref-ingredient.tsv / ref-dish_ingredient.tsv）')
    ap.add_argument('--me-account-id', type=int)
    ap.add_argument('--me-family-id', type=int)
    ap.add_argument('--me-profile-id', type=int)
    ap.add_argument('--husband-account-id', type=int)
    ap.add_argument('--out', default='seed_demo.sql')
    ap.add_argument('--no-backdate', action='store_true', help='不回溯已有账号 / 家庭 / 档案的创建时间')
    ap.add_argument('--no-health', action='store_true', help='不写健康档案（也不写单独同意记录）')
    ap.add_argument('--crypto-fixture', help='只输出一份加密样本 JSON（供 Java 测试验证格式）并退出，需 GUDU_FIELD_CRYPTO_KEYS')
    a = ap.parse_args()
    cipher = None if a.no_health else make_cipher()
    if a.crypto_fixture:
        if cipher is None:
            sys.exit('需要环境变量 GUDU_FIELD_CRYPTO_KEYS（如 1:<base64 32 字节>）')
        plain = health_json(HUSBAND_HEALTH)
        with open(a.crypto_fixture, 'w', encoding='utf-8') as f:
            json.dump(dict(keys=os.environ['GUDU_FIELD_CRYPTO_KEYS'], active=cipher.active, plain=plain,
                           cipher=cipher.encrypt(plain)), f, ensure_ascii=False, indent=2)
            f.write('\n')
        return
    for k in ('ref_dir', 'me_account_id', 'me_family_id', 'me_profile_id', 'husband_account_id'):
        if getattr(a, k) is None:
            ap.error('缺少参数 --%s' % k.replace('_', '-'))
    if not a.no_health and cipher is None:
        print('提示：未设置 GUDU_FIELD_CRYPTO_KEYS，健康档案与单独同意记录一律留空（--no-health 可消除本提示）', file=sys.stderr)
    ids = dict(ME_ACCOUNT_ID=a.me_account_id, ME_FAMILY_ID=a.me_family_id, ME_PROFILE_ID=a.me_profile_id,
               HUSBAND_ACCOUNT_ID=a.husband_account_id)
    B = write_sql(a.out, a.ref_dir, ids, cipher, not a.no_backdate, not a.no_health)
    print('已生成 %s：%d 行数据，%d 条语句' % (a.out, sum(B.S.counter.values()), len(B.S.lines)))
    for t, n in sorted(B.S.counter.items()):
        print('  %-20s %d' % (t, n))


if __name__ == '__main__':
    main()
