import 'package:flutter_test/flutter_test.dart';

import 'package:menu_flutter/services/food_log_service.dart';
import '../helpers/mock_http.dart';

/// FoodLogService：月视图/按菜汇总/年视图/详情/再做一次 + 模型 fromJson 边界。
void main() {
  group('FoodLogService.month', () {
    test('GET /food-log/month → month=2026-07 默认分页 + 解析 summary/records', () async {
      final captor = installMock((_) => okResponse({
            'summary': {
              'meals': 8,
              'dishes': 20,
              'cookDays': 6,
              'topDishes': ['西红柿炒蛋', '土豆丝'],
            },
            'records': [
              {
                'menuId': 10,
                'name': '周日午餐',
                'cookedAt': '2026-07-05 12:30:00',
                'dishCount': 3,
                'servingCount': 4,
                'dishNames': ['西红柿炒蛋', '土豆丝', '紫菜汤'],
                'usedUpCount': 2,
                'partialCount': 1,
                'reviewed': true,
              },
            ],
            'total': 8,
            'size': 15,
          }));

      final m = await FoodLogService.month(2026, 7);

      expect(captor.last!.path, '/food-log/month');
      expect(captor.last!.queryParameters['month'], '2026-07');
      expect(captor.last!.queryParameters['pageNum'], 1);
      expect(captor.last!.queryParameters['pageSize'], 15);

      expect(m.summary.meals, 8);
      expect(m.summary.dishes, 20);
      expect(m.summary.cookDays, 6);
      expect(m.summary.topDishes, ['西红柿炒蛋', '土豆丝']);
      expect(m.records.length, 1);
      expect(m.records.first.menuId, 10);
      expect(m.records.first.name, '周日午餐');
      expect(m.records.first.cookedAt, DateTime(2026, 7, 5, 12, 30, 0));
      expect(m.records.first.dishCount, 3);
      expect(m.records.first.servingCount, 4);
      expect(m.records.first.dishNames, ['西红柿炒蛋', '土豆丝', '紫菜汤']);
      expect(m.records.first.usedUpCount, 2);
      expect(m.records.first.partialCount, 1);
      expect(m.records.first.reviewed, true);
      expect(m.total, 8);
      expect(m.size, 15);
    });

    test('month=0 → month 查询参数仅传年份 2026（不拼 -00）', () async {
      final captor = installMock((_) => okResponse({
            'summary': {'meals': 0, 'dishes': 0, 'cookDays': 0, 'topDishes': []},
            'records': [],
            'total': 0,
            'size': 15,
          }));

      await FoodLogService.month(2026, 0);

      expect(captor.last!.path, '/food-log/month');
      expect(captor.last!.queryParameters['month'], '2026');
    });

    test('自定义 pageNum/pageSize → queryParameters 反映', () async {
      final captor = installMock((_) => okResponse({
            'summary': {'meals': 0, 'dishes': 0, 'cookDays': 0, 'topDishes': []},
            'records': [],
            'total': 0,
            'size': 30,
          }));

      await FoodLogService.month(2026, 7, pageNum: 2, pageSize: 30);

      expect(captor.last!.queryParameters['month'], '2026-07');
      expect(captor.last!.queryParameters['pageNum'], 2);
      expect(captor.last!.queryParameters['pageSize'], 30);
    });
  });

  group('FoodLogService.byDish', () {
    test('GET /food-log/by-dish → month=2026-07 解析 totalKinds + items', () async {
      final captor = installMock((_) => okResponse({
            'totalKinds': 2,
            'items': [
              {
                'dishId': 1,
                'dishName': '西红柿炒蛋',
                'count': 5,
                'lastCookedAt': '2026-07-20 18:00:00',
                'avgStar': 4.5,
              },
              {
                'dishId': 2,
                'dishName': '土豆丝',
                'count': 3,
                'lastCookedAt': '2026-07-18 18:00:00',
                'avgStar': null,
              },
            ],
          }));

      final r = await FoodLogService.byDish(2026, 7);

      expect(captor.last!.path, '/food-log/by-dish');
      expect(captor.last!.queryParameters['month'], '2026-07');
      expect(r.totalKinds, 2);
      expect(r.items.length, 2);
      expect(r.items.first.dishId, 1);
      expect(r.items.first.dishName, '西红柿炒蛋');
      expect(r.items.first.count, 5);
      expect(r.items.first.lastCookedAt, DateTime(2026, 7, 20, 18, 0, 0));
      expect(r.items.first.avgStar, 4.5);
      expect(r.items.last.avgStar, isNull);
    });

    test('byDish month=0 → month 查询参数仅传年份 2026', () async {
      final captor = installMock((_) => okResponse({
            'totalKinds': 0,
            'items': [],
          }));

      await FoodLogService.byDish(2026, 0);

      expect(captor.last!.path, '/food-log/by-dish');
      expect(captor.last!.queryParameters['month'], '2026');
    });
  });

  group('FoodLogService.year', () {
    test('GET /food-log/year → year=2026 解析 year + monthCounts', () async {
      final captor = installMock((_) => okResponse({
            'year': 2026,
            'monthCounts': [0, 0, 0, 0, 0, 0, 8, 5, 0, 0, 0, 0],
          }));

      final y = await FoodLogService.year(2026);

      expect(captor.last!.path, '/food-log/year');
      expect(captor.last!.queryParameters['year'], 2026);
      expect(y.year, 2026);
      expect(y.monthCounts.length, 12);
      expect(y.monthCounts[6], 8);
      expect(y.monthCounts[7], 5);
    });
  });

  group('FoodLogService.detail', () {
    test('GET /food-log/detail → menuId=10 解析详情', () async {
      final captor = installMock((_) => okResponse({
            'menuId': 10,
            'name': '周日午餐',
            'cookedAt': '2026-07-05 12:30:00',
            'servingCount': 4,
            'dishes': [
              {'dishId': 1, 'dishName': '西红柿炒蛋', 'servingFactor': 1.5, 'note': '多放蛋'},
              {'dishId': 2, 'dishName': '土豆丝', 'servingFactor': null, 'note': null},
            ],
            'usedUp': ['西红柿炒蛋'],
            'partial': ['土豆丝'],
            'reviewed': true,
          }));

      final d = await FoodLogService.detail(10);

      expect(captor.last!.path, '/food-log/detail');
      expect(captor.last!.queryParameters['menuId'], 10);
      expect(d.menuId, 10);
      expect(d.name, '周日午餐');
      expect(d.cookedAt, DateTime(2026, 7, 5, 12, 30, 0));
      expect(d.servingCount, 4);
      expect(d.dishes.length, 2);
      expect(d.dishes.first.dishId, 1);
      expect(d.dishes.first.dishName, '西红柿炒蛋');
      expect(d.dishes.first.servingFactor, 1.5);
      expect(d.dishes.first.note, '多放蛋');
      expect(d.dishes.last.servingFactor, isNull);
      expect(d.dishes.last.note, isNull);
      expect(d.usedUp, ['西红柿炒蛋']);
      expect(d.partial, ['土豆丝']);
      expect(d.reviewed, true);
    });
  });

  group('FoodLogService.copyMenu', () {
    test('POST /menu/{id}/copy → 返回新食集 id', () async {
      final captor = installMock((_) => okResponse(7));

      final id = await FoodLogService.copyMenu(10);

      expect(captor.last!.method, 'POST');
      expect(captor.last!.path, '/menu/10/copy');
      expect(id, 7);
    });
  });

  group('模型 fromJson 边界', () {
    test('FoodLogSummary 缺字段 → 默认值（meals=0, topDishes=[]）', () {
      final s = FoodLogSummary.fromJson({});
      expect(s.meals, 0);
      expect(s.dishes, 0);
      expect(s.cookDays, 0);
      expect(s.topDishes, isEmpty);
    });

    test('FoodLogMonth 缺 size/total → size=15, total=0', () {
      final m = FoodLogMonth.fromJson({
        'summary': {'meals': 1},
      });
      expect(m.total, 0);
      expect(m.size, 15);
      expect(m.records, isEmpty);
      expect(m.summary.meals, 1);
    });

    test('FoodLogMeal cookedAt=null → DateTime? 为 null', () {
      final meal = FoodLogMeal.fromJson({'name': '无时间'});
      expect(meal.cookedAt, isNull);
      expect(meal.name, '无时间');
      expect(meal.menuId, isNull);
      expect(meal.servingCount, isNull);
      expect(meal.dishCount, 0);
      expect(meal.dishNames, isEmpty);
      expect(meal.usedUpCount, 0);
      expect(meal.partialCount, 0);
      expect(meal.reviewed, false);
    });

    test('FoodLogDishItem avgStar=null → null', () {
      final item = FoodLogDishItem.fromJson({
        'dishId': 5,
        'dishName': '土豆丝',
        'avgStar': null,
      });
      expect(item.avgStar, isNull);
      expect(item.count, 0);
      expect(item.lastCookedAt, isNull);
    });

    test('FoodLogByDish 缺 items/totalKinds → 空列表 + 0', () {
      final r = FoodLogByDish.fromJson({});
      expect(r.totalKinds, 0);
      expect(r.items, isEmpty);
    });

    test('FoodLogYear 缺 monthCounts → 空列表', () {
      final y = FoodLogYear.fromJson({'year': 2026});
      expect(y.year, 2026);
      expect(y.monthCounts, isEmpty);
    });

    test('FoodLogDetail 缺 usedUp/partial → 空列表', () {
      final d = FoodLogDetail.fromJson({'menuId': 1, 'name': 'x'});
      expect(d.menuId, 1);
      expect(d.name, 'x');
      expect(d.cookedAt, isNull);
      expect(d.servingCount, isNull);
      expect(d.dishes, isEmpty);
      expect(d.usedUp, isEmpty);
      expect(d.partial, isEmpty);
      expect(d.reviewed, false);
    });

    test('FoodLogDish 缺可选字段 → null', () {
      final dish = FoodLogDish.fromJson({'dishId': 1, 'dishName': '西红柿炒蛋'});
      expect(dish.dishId, 1);
      expect(dish.dishName, '西红柿炒蛋');
      expect(dish.servingFactor, isNull);
      expect(dish.note, isNull);
    });
  });
}
