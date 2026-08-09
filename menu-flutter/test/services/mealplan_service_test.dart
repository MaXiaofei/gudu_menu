import 'package:flutter_test/flutter_test.dart';

import 'package:menu_flutter/services/mealplan_service.dart';
import '../helpers/mock_http.dart';

/// MealPlanService：建计划/详情/挂菜(含重复)/删/复制/列表 + 模型逻辑。
void main() {
  group('MealPlanService.createPlan', () {
    test('默认 name=本周计划，返回 id', () async {
      final captor = installMock((_) => okResponse(1));

      final id = await MealPlanService.createPlan('2026-06-30');

      expect(captor.last!.path, '/mealplan');
      expect(captor.last!.data, {'weekStart': '2026-06-30', 'name': '本周计划'});
      expect(id, 1);
    });

    test('自定义 name', () async {
      final captor = installMock((_) => okResponse(2));

      await MealPlanService.createPlan('2026-06-30', name: '减脂周');

      expect(captor.last!.data['name'], '减脂周');
    });
  });

  group('MealPlanService.getPlan', () {
    test('GET /mealplan/{id} → 解析 plan + items', () async {
      final captor = installMock((_) => okResponse({
            'plan': {'id': 1, 'weekStart': '2026-06-30', 'name': '本周计划'},
            'items': [
              {'id': 10, 'date': '2026-06-30', 'meal': '早餐', 'dishId': 5, 'dishName': '粥'},
              {'id': 11, 'date': '2026-06-30', 'meal': '午餐', 'dishId': 6},
            ],
          }));

      final detail = await MealPlanService.getPlan(1);

      expect(captor.last!.path, '/mealplan/1');
      expect(detail.plan.id, 1);
      expect(detail.plan.name, '本周计划');
      expect(detail.items.length, 2);
      expect(detail.items[0].dishName, '粥');
      expect(detail.items[0].servingFactor, isNull);
    });

    test('items 缺省 → 空数组', () async {
      installMock((_) => okResponse({
            'plan': {'id': 1},
          }));

      final detail = await MealPlanService.getPlan(1);

      expect(detail.items, isEmpty);
    });
  });

  group('MealPlanService.addItem', () {
    test('挂菜 body 含 date/meal/dishId/servingFactor，返回 itemId + duplicates', () async {
      final captor = installMock((_) => okResponse({
            'itemId': 100,
            'duplicates': [
              {'date': '2026-06-30', 'meal': '早餐', 'dishId': 5},
            ],
          }));

      const item = MealPlanItem(
        date: '2026-06-30',
        meal: '早餐',
        dishId: 5,
        servingFactor: 1.5,
      );
      final result = await MealPlanService.addItem(1, item);

      expect(captor.last!.path, '/mealplan/1/item');
      expect(captor.last!.data, {
        'date': '2026-06-30',
        'meal': '早餐',
        'dishId': 5,
        'servingFactor': 1.5,
      });
      expect(result.itemId, 100);
      expect(result.hasDuplicate, isTrue);
      expect(result.duplicates.length, 1);
    });

    test('无重复 → hasDuplicate=false', () async {
      installMock((_) => okResponse({'itemId': 101, 'duplicates': []}));

      final result = await MealPlanService.addItem(
        1,
        const MealPlanItem(date: '2026-06-30', meal: '午餐', dishId: 6),
      );

      expect(result.itemId, 101);
      expect(result.hasDuplicate, isFalse);
    });

    test('duplicates 缺省 → hasDuplicate=false', () async {
      installMock((_) => okResponse({'itemId': 102}));

      final result = await MealPlanService.addItem(
        1,
        const MealPlanItem(date: '2026-06-30', meal: '晚餐', dishId: 7),
      );

      expect(result.hasDuplicate, isFalse);
    });
  });

  group('MealPlanService.deleteItem', () {
    test('DELETE /mealplan/item/{itemId}', () async {
      final captor = installMock((_) => okResponse(null));

      await MealPlanService.deleteItem(50);

      expect(captor.last!.path, '/mealplan/item/50');
      expect(captor.last!.method, 'DELETE');
    });
  });

  group('MealPlanService.copyFrom', () {
    test('POST /mealplan/{planId}/copy-from/{srcPlanId}，返回 count', () async {
      final captor = installMock((_) => okResponse(7));

      final count = await MealPlanService.copyFrom(2, 1);

      expect(captor.last!.path, '/mealplan/1/copy-from/2');
      expect(count, 7);
    });

    test('返回 null → 0', () async {
      installMock((_) => okResponse(null));

      expect(await MealPlanService.copyFrom(2, 1), 0);
    });
  });

  group('MealPlanService.list', () {
    test('GET /mealplan → 从 records 解析', () async {
      final captor = installMock((_) => okResponse({
            'records': [
              {'id': 1, 'weekStart': '2026-06-30', 'name': '本周计划'},
            ],
          }));

      final list = await MealPlanService.list();

      expect(captor.last!.queryParameters['pageSize'], 15);
      expect(list.length, 1);
      expect(list[0].id, 1);
      expect(list[0].weekStart, '2026-06-30');
    });

    test('非 IPage → 空列表', () async {
      installMock((_) => okResponse(null));

      expect(await MealPlanService.list(), isEmpty);
    });
  });

  group('PlanDetail.itemsByDate', () {
    test('按 date 分组', () {
      final detail = PlanDetail.fromJson({
        'plan': {'id': 1},
        'items': [
          {'date': '2026-06-30', 'meal': '早餐', 'dishId': 1},
          {'date': '2026-06-30', 'meal': '午餐', 'dishId': 2},
          {'date': '2026-07-01', 'meal': '早餐', 'dishId': 3},
        ],
      });

      final grouped = detail.itemsByDate();

      expect(grouped.length, 2);
      expect(grouped['2026-06-30']!.length, 2);
      expect(grouped['2026-07-01']!.length, 1);
    });
  });
}
