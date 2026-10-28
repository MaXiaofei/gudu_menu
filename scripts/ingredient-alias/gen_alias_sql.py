# -*- coding: utf-8 -*-
"""从 scripts/recipe-fix/recipe_fix_map.py 的别名映射生成 V95 迁移里的别名种子 SQL 片段。

用法：python3 -I scripts/ingredient-alias/gen_alias_sql.py > 种子.sql
规则：只取「单一目标、原始名 != 目标名、非小标题/残片」的条目；
规整规则与后端 IngredientNameNormalizer 一致（去首尾空格、全角括号转半角、多余空白折叠为一个）。
目标名按名称关联公共食材（family_id IS NULL、未删除），库里没有的自然跳过；
别名恰好等于某个公共食材名时也跳过（避免歧义）。
"""
import os
import re
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'recipe-fix'))
import recipe_fix_map  # noqa: E402

RAW = recipe_fix_map.ALIAS

# 品牌/残片/过于笼统（一个字的「肉/面/鱼/虾」会吞掉用户想新建的具体食材）的原始名，不进别名表
EXCLUDE = {
    '蜂蜜水', '炸油', '六月鲜生抽', '生抽王', '老干妈红油豆豉', '鹃城牌香辣酱', '特辣王', '海南黄灯笼辣椒酱',
    '百梦多咖喱', '泰国绿咖喱酱', 'cook100新奥尔良烤肉料', '奶香土司', '涪陵榨菜丝', '中等身形的茄子',
    '槟樃芋头', '靑椒', '去皮大蒜瓣', '牛里脊肉肉', '番茄牛肉馅', '肉沫-五花肉', '肉沫-前腿肉', '青辣椒圈',
    '肉', '面', '鱼', '虾', '咖啡', '意大利浓缩咖啡', '纯黑咖啡粉', '大蒜丁', '洋葱丁', '中等土豆',
    '郫县红油豆瓣酱', '四川郫县豆瓣酱', '郫县豆瓣辣酱', '红心番薯',
}
FRAGMENT = re.compile(r'[:：\d]|[-—]')


def norm(s):
    s = s.strip().replace('（', '(').replace('）', ')')
    return re.sub(r'\s+', ' ', s)


def q(s):
    return "'" + s.replace("\\", "\\\\").replace("'", "''") + "'"


def pairs():
    out = []
    for k, v in RAW.items():
        if len(v) != 1:
            continue
        a, t = norm(k), norm(v[0])
        if a == t or k in EXCLUDE or FRAGMENT.search(a):
            continue
        out.append((a, t))
    return out


if __name__ == '__main__':
    ps = pairs()
    print('-- 别名种子：%d 条（由 scripts/ingredient-alias/gen_alias_sql.py 生成）' % len(ps))
    for a, t in ps:
        print("INSERT IGNORE INTO ingredient_alias (alias, ingredient_id) "
              "SELECT %s, t.id FROM ingredient t WHERE t.name = %s AND t.family_id IS NULL AND t.deleted = 0 "
              "AND NOT EXISTS (SELECT 1 FROM ingredient x WHERE x.name = %s AND x.family_id IS NULL AND x.deleted = 0) "
              "ORDER BY t.id LIMIT 1;" % (q(a), q(t), q(a)))
