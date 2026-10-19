import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:menu_flutter/core/ingredient_icons.dart';

/// 守护：Dart 内置规则与单一来源 docs/design/ingredient-icons.json、素材清单 assets.json 一致。
void main() {
  final rules = jsonDecode(File('../docs/design/ingredient-icons.json').readAsStringSync()) as Map<String, dynamic>;
  final assets = jsonDecode(File('../docs/design/assets.json').readAsStringSync()) as Map<String, dynamic>;

  test('插画 key 与素材清单一致，且素材已打包', () {
    final ing = (assets['ing'] as List).cast<String>().toSet();
    expect(IngredientIcons.keys, ing);
    for (final k in ing) {
      expect(File('assets/ing/$k.png').existsSync(), isTrue, reason: k);
    }
  });

  test('关键词与品类兜底与 JSON 一致', () {
    final kw = (rules['keywords'] as List).map((e) => (e[0] as String, (e[1] as List).cast<String>())).toList();
    expect(IngredientIcons.keywords.map((e) => '${e.$1}:${e.$2.join(',')}').toList(),
        kw.map((e) => '${e.$1}:${e.$2.join(',')}').toList());
    expect(IngredientIcons.categoryFallback, (rules['categoryFallback'] as Map).cast<String, String>());
  });

  test('解析顺序：iconKey → 名称 → 品类 → null', () {
    expect(IngredientIcons.resolve(iconKey: 'fish', name: '番茄'), 'fish');
    expect(IngredientIcons.resolve(iconKey: 'unknown', name: '小番茄'), 'tomato');
    expect(IngredientIcons.resolve(name: '胡萝卜'), 'carrot'); // 最长关键词胜过「萝卜」
    expect(IngredientIcons.resolve(name: '秋葵', category: '蔬菜'), 'cabbage');
    expect(IngredientIcons.resolve(name: '秋葵'), isNull);
  });
}
