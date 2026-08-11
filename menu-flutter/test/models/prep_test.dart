import 'package:flutter_test/flutter_test.dart';

import 'package:menu_flutter/models/prep.dart';

/// PrepItem model 测试（V55 P2-8 补）。
/// 重点：copyWithStatus（备菜状态切换）保留 usageTexts/dishNames 等 V55 新字段。
void main() {
  group('PrepItem.copyWithStatus', () {
    test('切换状态时保留 usageTexts / dishNames / shared 等 V55 字段', () {
      final item = PrepItem(
        ingredientId: 10,
        ingredientName: '鸡蛋',
        stockLevel: 'ENOUGH',
        usageTexts: const ['番茄炒蛋 2个', '蛋花汤 3个'],
        dishCount: 2,
        dishNames: const ['番茄炒蛋', '蛋花汤'],
        status: PrepStatus.pending,
        shared: true,
      );

      final copied = item.copyWithStatus(PrepStatus.ready);

      // status 已切换
      expect(copied.status, PrepStatus.ready);
      // V55 新字段原样保留（防 copyWithStatus 漏字段）
      expect(copied.usageTexts, const ['番茄炒蛋 2个', '蛋花汤 3个']);
      expect(copied.dishNames, const ['番茄炒蛋', '蛋花汤']);
      expect(copied.dishCount, 2);
      expect(copied.shared, isTrue);
      expect(copied.ingredientName, '鸡蛋');
      expect(copied.stockLevel, 'ENOUGH');
    });

    test('原对象不被修改（不可变 copy）', () {
      final item = PrepItem(
        ingredientId: 1,
        ingredientName: '番茄',
        usageTexts: const ['番茄炒蛋 100g'],
        dishCount: 1,
        dishNames: const ['番茄炒蛋'],
        status: PrepStatus.pending,
        shared: false,
      );
      item.copyWithStatus(PrepStatus.ready);
      // 原对象 status 仍是 pending
      expect(item.status, PrepStatus.pending);
    });
  });

  group('PrepStatus', () {
    test('fromName: 未知/空 → pending', () {
      expect(PrepStatus.fromName(null), PrepStatus.pending);
      expect(PrepStatus.fromName(''), PrepStatus.pending);
      expect(PrepStatus.fromName('READY'), PrepStatus.ready);
      expect(PrepStatus.fromName('THAWING'), PrepStatus.thawing);
      expect(PrepStatus.fromName('MARINATING'), PrepStatus.marinating);
    });

    test('name: 提交后端大写名', () {
      expect(PrepStatus.pending.name, 'PENDING');
      expect(PrepStatus.ready.name, 'READY');
    });
  });
}
