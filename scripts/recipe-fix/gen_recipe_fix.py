# -*- coding: utf-8 -*-
"""下厨房导入菜谱用料补全：读线上导出的菜谱/食材/单位，生成补全 SQL。

用法见 README.md。输出单事务 SQL：新增常见食材 → 按 dish.note 里的「用料：…」补 dish_ingredient
→ 清掉 note 里的用料清单 → 下架无法补全的菜谱；所有改动登记到 recipe_fix_log，可用 rollback_recipe_fix.sql 撤回。
"""
import argparse
import csv
import re
import sys

from recipe_fix_map import ALIAS, NEW_INGREDIENTS

BATCH = 'xcf-ingredients-20261027'

# 只有图片没有文字、用料只剩「肉」的菜谱：下架（软删除）
HIDE_DISHES = {
    'https://www.xiachufang.com/recipe/107794864/': '农家一碗香：步骤只有图片没有文字，用料只有「肉」',
}

VAGUE_SOME = ('适量', '随意', '按个人口味', '依口味', '如图', '看个人', '根据', '按口味', '酌情')
VAGUE_LITTLE = ('少许', '少量', '一点点', '一点', '几')
CN_NUM = {'半': 0.5, '一': 1, '两': 2, '二': 2, '三': 3, '四': 4, '五': 5, '六': 6, '七': 7, '八': 8, '九': 9, '十': 10}
# 单位字典里没有的写法 → 字典里的近义单位（按前缀匹配，长的在前）
UNIT_ALIAS = [('ml', 'ml'), ('g', 'g'), ('l', '升'), ('公斤', '千克'), ('茶勺', '茶匙'),
              ('大汤匙', '汤匙'), ('小捏', '捏'), ('小袋', '袋'), ('大片', '片'), ('汤', '汤匙'),
              ('枚', '个'), ('支', '根'), ('枝', '根'), ('束', '把')]
PAREN = re.compile(r'[（(][^（()）]*[)）]|[（(][^)）]*$')


def read_tsv(path):
    with open(path, encoding='utf-8') as f:
        return [r for r in csv.reader(f, delimiter='\t', quoting=csv.QUOTE_NONE)]


def split_items(note):
    """note「来源：下厨房 | 用料：A 1个、B 适量」→ [(原始名, 用量原文)]。"""
    m = re.search(r'用料：(.*)$', note or '')
    if not m:
        return []
    out = []
    for raw in m.group(1).split('、'):
        raw = raw.strip()
        if not raw:
            continue
        name, _, amount = raw.partition(' ')
        # 「葱姜蒜，盐，香葱」「老抽，盐」：中文逗号连写的多个食材
        if '，' in name:
            for part in name.split('，'):
                if part.strip():
                    out.append((part.strip(), amount.strip()))
            continue
        out.append((name, amount.strip()))
    return out


def normalize(name):
    n = PAREN.sub('', name).strip()
    n = re.sub(r'^\d+\.?(?=[^\d.])', '', n).strip()   # 「1.料酒」「16淀粉」
    return n


def resolve(name, library):
    """原始名 → 食材库名称列表；None = 无法识别。"""
    for key in (name, normalize(name)):
        if key in ALIAS:
            return ALIAS[key]
    n = normalize(name)
    if n in library or n in NEW_INGREDIENTS:
        return [n]
    return None


def parse_amount(text, units):
    """用量原文 → (数量, 单位名或 None)。非数字量词记 0 + 适量/少许。"""
    t = PAREN.sub('', text or '').replace('约', '').replace('大概', '').strip()
    if not t:
        return 0, None
    if t.startswith(VAGUE_LITTLE):
        return 0, '少许'
    if t.startswith(VAGUE_SOME):
        return 0, '适量'
    m = re.match(r'(\d+(?:\.\d+)?)(?:\s*/\s*(\d+))?', t)
    if m:
        num = float(m.group(1)) / (float(m.group(2)) if m.group(2) else 1)
        rest = t[m.end():]
    elif t[0] in CN_NUM:
        num, rest = CN_NUM[t[0]], t[1:]
        if t.startswith('一半'):
            num, rest = 0.5, t[2:]
        elif rest[:1] == '十':
            num, rest = num * 10, rest[1:]
    else:
        if t.startswith(VAGUE_LITTLE):
            return 0, '少许'
        return 0, '适量'
    # 「3-4个」「10－15个」取下限；「两三片」「十几粒」「1又1/2大匙」去掉多余数字
    rest = re.sub(r'^\s*[-~～－—至到]\s*\d*(?:\.\d+)?', '', rest)
    rest = rest.strip()
    unit = next((u for u in units if rest.startswith(u)), None)
    if unit is None:
        rest = re.sub(r'^[又\d/一二两三四五六七八九十几]+', '', rest).strip()
        rest = re.sub(r'^整', '', rest)
        unit = next((u for u in units if rest.startswith(u)), None)
    if unit is None:
        low = rest.lower()
        unit = next((v for k, v in UNIT_ALIAS if low.startswith(k) and v in units), None)
    return round(num, 2), unit


def esc(s):
    return "'" + str(s).replace('\\', '\\\\').replace("'", "\\'") + "'"


def build(dishes, library, existing, units):
    """返回 (新增食材集合, 每道菜要补的行 [(url, 食材名, 数量, 单位)], 无法识别的原始名)。"""
    units = sorted(units, key=len, reverse=True)
    rows, unknown, used_new = [], {}, set()
    for dish_id, url, note in dishes:
        if url in HIDE_DISHES:
            continue
        seen = set(existing.get(dish_id, ()))
        for raw, amount_text in split_items(note):
            targets = resolve(raw, library)
            if targets is None:
                unknown.setdefault(raw, []).append(dish_id)
                continue
            amt, unit = parse_amount(amount_text, units)
            for name in targets:
                if name in seen:
                    continue
                seen.add(name)
                if name not in library:
                    used_new.add(name)
                rows.append((url, name, amt, unit))
    return used_new, rows, unknown


def render(used_new, rows, v68_sql):
    out = [
        '-- 下厨房导入菜谱用料补全（scripts/recipe-fix 生成，勿手改）',
        f'-- 批次 {BATCH}：新增食材 {len(used_new)} 种，补用料 {len(rows)} 行，下架 {len(HIDE_DISHES)} 道',
        'SET NAMES utf8mb4;',
        'CREATE TABLE IF NOT EXISTS recipe_fix_log (',
        '  id BIGINT PRIMARY KEY AUTO_INCREMENT,',
        '  batch VARCHAR(64) NOT NULL,',
        "  kind VARCHAR(32) NOT NULL COMMENT 'ingredient_added/dish_ingredient_added/note_cleared/dish_hidden',",
        '  ref_id BIGINT NOT NULL,',
        '  old_value TEXT NULL,',
        '  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,',
        '  KEY idx_batch_kind (batch, kind)',
        ");",
        'START TRANSACTION;',
        'SET @ing_max := (SELECT IFNULL(MAX(id), 0) FROM ingredient);',
        'SET @di_max := (SELECT IFNULL(MAX(id), 0) FROM dish_ingredient);',
        '',
        '-- 1) 新增食材（已存在同名则跳过）',
    ]
    for name in sorted(used_new):
        out.append(
            "INSERT INTO ingredient(name, purchase_category_id, edible) SELECT {n}, "
            "(SELECT id FROM sys_dict WHERE dict_group='purchase_category' AND name={c} LIMIT 1), 1 "
            "FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient WHERE name={n} AND deleted=0);"
            .format(n=esc(name), c=esc(NEW_INGREDIENTS[name])))
    out += [
        f"INSERT INTO recipe_fix_log(batch, kind, ref_id) SELECT '{BATCH}', 'ingredient_added', id FROM ingredient WHERE id > @ing_max;",
        '',
        '-- 2) 补用料（只补公共菜谱；同一道菜已有该食材则跳过）',
    ]
    for url, name, amt, unit in rows:
        unit_sql = 'NULL' if unit is None else \
            f"(SELECT id FROM sys_dict WHERE dict_group='unit' AND name={esc(unit)} LIMIT 1)"
        out.append(
            "INSERT INTO dish_ingredient(dish_id, ingredient_id, amount, unit_id) "
            f"SELECT d.id, (SELECT MIN(id) FROM ingredient WHERE name={esc(name)} AND deleted=0), {amt:g}, {unit_sql} "
            f"FROM dish d WHERE d.source_url={esc(url)} AND d.deleted=0 AND d.family_id IS NULL "
            f"AND NOT EXISTS (SELECT 1 FROM dish_ingredient x WHERE x.dish_id=d.id "
            f"AND x.ingredient_id=(SELECT MIN(id) FROM ingredient WHERE name={esc(name)} AND deleted=0));")
    out += [
        f"INSERT INTO recipe_fix_log(batch, kind, ref_id) SELECT '{BATCH}', 'dish_ingredient_added', di.id FROM dish_ingredient di "
        "JOIN dish d ON d.id = di.dish_id AND d.source_name = '下厨房' AND d.family_id IS NULL WHERE di.id > @di_max;",
        '',
        '-- 3) 用料已进「用料」卡片，清掉 note 里的用料清单（来源已单独展示，只剩来源时置空）',
        f"INSERT INTO recipe_fix_log(batch, kind, ref_id, old_value) SELECT '{BATCH}', 'note_cleared', id, note "
        "FROM dish WHERE source_name='下厨房' AND family_id IS NULL AND note LIKE '%| 用料：%';",
        "UPDATE dish SET note = NULLIF(TRIM(SUBSTRING_INDEX(note, ' | 用料：', 1)), '来源：下厨房') "
        "WHERE source_name='下厨房' AND family_id IS NULL AND note LIKE '%| 用料：%';",
        '',
        '-- 4) 下架无法补全的菜谱（软删除）',
    ]
    for url, why in HIDE_DISHES.items():
        out += [
            f'-- {why}',
            f"INSERT INTO recipe_fix_log(batch, kind, ref_id) SELECT '{BATCH}', 'dish_hidden', id "
            f"FROM dish WHERE source_url={esc(url)} AND deleted=0 AND family_id IS NULL;",
            f"UPDATE dish SET deleted=1 WHERE source_url={esc(url)} AND deleted=0 AND family_id IS NULL;",
        ]
    out += ['COMMIT;', '', '-- 5) 新食材按名称补插画 key（原样复用 V68，幂等，只动 icon_key 为空的行）', v68_sql]
    return '\n'.join(out) + '\n'


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--dish', required=True, help='dish.tsv：id, source_url, note（下厨房公共菜谱）')
    ap.add_argument('--ingredient', required=True, help='ingredient.tsv：id, name, deleted')
    ap.add_argument('--dish-ingredient', required=True, help='dish_ing.tsv：dish_id, source_url, 食材名')
    ap.add_argument('--unit', required=True, help='unit.tsv：sys_dict unit 名称，一行一个')
    ap.add_argument('--v68', required=True, help='menu-api/sql/V68__ingredient_icon_key.sql')
    ap.add_argument('--out', required=True)
    a = ap.parse_args()

    dishes = [(r[0], r[1], r[2]) for r in read_tsv(a.dish) if len(r) >= 3 and r[0].isdigit()]
    library = {r[1] for r in read_tsv(a.ingredient) if len(r) >= 3 and r[2] == '0'}
    existing = {}
    for r in read_tsv(a.dish_ingredient):
        existing.setdefault(r[0], set()).add(r[2])
    units = [r[0] for r in read_tsv(a.unit) if r]

    used_new, rows, unknown = build(dishes, library, existing, units)
    if unknown:
        for raw, ids in sorted(unknown.items()):
            print(f'无法识别：{raw}（菜谱 {",".join(ids)}）', file=sys.stderr)
        sys.exit(1)
    with open(a.v68, encoding='utf-8') as f:
        v68 = f.read()
    with open(a.out, 'w', encoding='utf-8') as f:
        f.write(render(used_new, rows, v68))
    print(f'新增食材 {len(used_new)} 种，补用料 {len(rows)} 行，涉及菜谱 {len({r[0] for r in rows})} 道 → {a.out}')


if __name__ == '__main__':
    main()
