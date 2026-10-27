# -*- coding: utf-8 -*-
"""用料补全生成器自检（不连数据库）：python3 -m unittest test_recipe_fix"""
import unittest

from gen_recipe_fix import BATCH, HIDE_DISHES, build, parse_amount, render, resolve, split_items
from recipe_fix_map import ALIAS, NEW_INGREDIENTS

UNITS = ['个', '克', 'g', 'ml', '勺', '小勺', '汤匙', '匙', '根', '瓣', '块', '两', '斤', '片', '粒', '朵', '颗',
         '适量', '少许', '把', '升', '千克']
LIB = {'食盐', '大蒜', '小葱', '茄子', '鸡蛋', '豆瓣酱', '淀粉', '猪肉(肥瘦)', '料酒', '红辣椒'}


class SplitTest(unittest.TestCase):
    def test_split_note(self):
        note = '来源：下厨房 | 用料：茄子 2条、葱姜蒜，盐，香葱、肉末 适量'
        self.assertEqual(split_items(note), [
            ('茄子', '2条'), ('葱姜蒜', ''), ('盐', ''), ('香葱', ''), ('肉末', '适量')])

    def test_no_list(self):
        self.assertEqual(split_items('来源：下厨房'), [])


class ResolveTest(unittest.TestCase):
    def test_alias_and_numbered_and_paren(self):
        self.assertEqual(resolve('盐', LIB), ['食盐'])
        self.assertEqual(resolve('3.盐', LIB), ['食盐'])
        self.assertEqual(resolve('14.蒜(切碎)', LIB), ['大蒜'])
        self.assertEqual(resolve('葱姜蒜', LIB), ['小葱', '生姜', '大蒜'])
        self.assertEqual(resolve('清水', LIB), [])
        self.assertEqual(resolve('鸡蛋', LIB), ['鸡蛋'])
        self.assertIsNone(resolve('不存在的东西', LIB))

    def test_alias_targets_are_known(self):
        # 映射目标要么在新增清单里，要么是线上食材库已有名称（下方名单取自 2026-10-27 生产库）
        known_library = {
            '食盐', '酱油', '老抽', '蚝油', '醋', '陈醋', '料酒', '豆瓣酱', '黄豆酱', '芝麻酱', '番茄酱', '鸡精',
            '花椒', '干辣椒', '八角', '孜然', '芝麻油', '红糖', '白胡椒', '蜂蜜', '淀粉', '豆豉', '标准粉',
            '鲜面条', '年糕', '米饭', '面包', '粉丝(干)', '小葱', '大葱', '大蒜', '马铃薯', '番茄', '圣女果',
            '红辣椒', '青椒', '彩椒', '洋葱', '圆白菜', '大白菜', '小白菜', '四季豆', '荷兰豆', '绿豆芽', '莲藕',
            '甜玉米(鲜)', '豌豆(鲜)', '香菜', '蒜薹', '莴笋', '菜花', '白萝卜', '胡萝卜', '红薯', '芋头', '荸荠',
            '黄瓜', '海带(鲜)', '油菜', '茄子', '香菇(鲜)', '白蘑菇', '蟹味菇', '木耳(干)', '榛蘑(干)', '紫菜(干)',
            '猪肉(肥瘦)', '猪肉(瘦)', '猪里脊', '猪排骨', '牛肉(瘦)', '牛肉(肥瘦)', '牛腱', '羊肉(肥瘦)', '鸡腿肉',
            '鸭肉', '鸡肉', '鸡胸肉', '鸡翅', '兔肉', '香肠', '火腿肠', '杏仁', '虾仁(鲜)', '基围虾', '草鱼',
            '三文鱼', '黄颡鱼', '鸡蛋黄', '鸡蛋白', '鸡蛋', '北豆腐', '南豆腐', '内酯豆腐', '千张', '豆腐干',
            '油豆腐', '黄豆', '淡奶油', '牛奶', '酸奶', '奶酪', '白芝麻', '黑芝麻', '花生', '松子仁', '核桃',
            '栗子(鲜)', '红枣(干)', '芒果', '紫薯', '柠檬',
        }
        for raw, targets in ALIAS.items():
            for t in targets:
                self.assertTrue(t in NEW_INGREDIENTS or t in known_library, f'{raw} → {t} 不在食材库也不在新增清单')


class AmountTest(unittest.TestCase):
    def p(self, text):
        return parse_amount(text, sorted(UNITS, key=len, reverse=True))

    def test_numbers_and_units(self):
        self.assertEqual(self.p('2个'), (2, '个'))
        self.assertEqual(self.p('1/3根'), (0.33, '根'))
        self.assertEqual(self.p('半块'), (0.5, '块'))
        self.assertEqual(self.p('一块 (大概380g)'), (1, '块'))
        self.assertEqual(self.p('2-3瓣'), (2, '瓣'))
        self.assertEqual(self.p('6－8朵'), (6, '朵'))
        self.assertEqual(self.p('二两'), (2, '两'))
        self.assertEqual(self.p('两三片'), (2, '片'))
        self.assertEqual(self.p('400ML'), (400, 'ml'))
        self.assertEqual(self.p('三枚'), (3, '个'))
        self.assertEqual(self.p('3'), (3, None))

    def test_vague(self):
        self.assertEqual(self.p('适量'), (0, '适量'))
        self.assertEqual(self.p('随意'), (0, '适量'))
        self.assertEqual(self.p('一点点'), (0, '少许'))
        self.assertEqual(self.p('少量'), (0, '少许'))
        self.assertEqual(self.p(''), (0, None))


class BuildTest(unittest.TestCase):
    def test_skip_existing_and_duplicates_and_hidden(self):
        hidden = next(iter(HIDE_DISHES))
        dishes = [
            ('1', 'u1', '来源：下厨房 | 用料：茄子 2条、蒜 3瓣、蒜末 适量、盐 少许、清水 1碗'),
            ('2', hidden, '来源：下厨房 | 用料：肉'),
        ]
        new, rows, unknown = build(dishes, LIB, {'1': {'茄子'}}, UNITS)
        self.assertEqual(unknown, {})
        self.assertEqual(rows, [('u1', '大蒜', 3, '瓣'), ('u1', '食盐', 0, '少许')])
        self.assertEqual(new, set())

    def test_new_ingredient_collected(self):
        new, rows, _ = build([('1', 'u1', '用料：姜 1块')], LIB, {}, UNITS)
        self.assertEqual(new, {'生姜'})
        self.assertEqual(rows, [('u1', '生姜', 1, '块')])

    def test_render_is_single_transaction_and_logged(self):
        sql = render({'生姜'}, [('u1', '生姜', 1, '块')], '-- v68')
        self.assertEqual(sql.count('START TRANSACTION;'), 1)
        self.assertEqual(sql.count('COMMIT;'), 1)
        for kind in ('ingredient_added', 'dish_ingredient_added', 'note_cleared', 'dish_hidden'):
            self.assertIn(f"'{BATCH}', '{kind}'", sql)
        self.assertIn('d.family_id IS NULL', sql)
        self.assertIn('NOT EXISTS (SELECT 1 FROM dish_ingredient x', sql)


if __name__ == '__main__':
    unittest.main()
