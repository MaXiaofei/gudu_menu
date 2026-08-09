import 'package:flutter_test/flutter_test.dart';

import 'package:menu_flutter/services/dish_service.dart';
import '../helpers/mock_http.dart';

/// DishService：搜索/详情/营养/标记做过/指标字典/录入/URL 导入。
void main() {
  group('DishService.search', () {
    test('带 keyword → query 含 keyword/pageNum/pageSize，解析分页', () async {
      final captor = installMock((_) => okResponse({
            'records': [
              {'id': 1, 'name': '番茄炒蛋'},
              {'id': 2, 'name': '番茄蛋汤'},
            ],
            'total': 2,
            'current': 1,
            'size': 20,
          }));

      final page = await DishService.search(keyword: '番茄', pageSize: 20);

      expect(captor.last!.path, '/dish/search');
      expect(captor.last!.queryParameters['keyword'], '番茄');
      expect(captor.last!.queryParameters['pageNum'], 1);
      expect(captor.last!.queryParameters['pageSize'], 20);
      expect(page.total, 2);
      expect(page.records.length, 2);
      expect(page.records[0].name, '番茄炒蛋');
    });

    test('keyword 为空串 → query 不含 keyword', () async {
      final captor = installMock((_) => okResponse({
            'records': [],
            'total': 0,
          }));

      await DishService.search(keyword: '');

      expect(captor.last!.queryParameters.containsKey('keyword'), isFalse);
    });

    test('keyword 为 null → query 不含 keyword', () async {
      final captor = installMock((_) => okResponse({
            'records': [],
            'total': 0,
          }));

      await DishService.search();

      expect(captor.last!.queryParameters.containsKey('keyword'), isFalse);
    });
  });

  group('DishService.detail', () {
    test('GET /dish/{id} → 解析 dish + steps', () async {
      final captor = installMock((_) => okResponse({
            'dish': {'id': 9, 'name': '红烧肉'},
            'steps': [
              {'seq': 1, 'text': '切块'},
              {'seq': 2, 'text': '炖煮', 'images': 'a.jpg,b.jpg'},
            ],
          }));

      final d = await DishService.detail(9);

      expect(captor.last!.path, '/dish/9');
      expect(d.dish.id, 9);
      expect(d.dish.name, '红烧肉');
      expect(d.steps.length, 2);
      expect(d.steps[1].imageList, ['a.jpg', 'b.jpg']);
    });
  });

  group('DishService.nutrition', () {
    test('GET /dish/{id}/nutrition?serving= → Map<String,num>', () async {
      final captor = installMock((_) => okResponse({
            'calorie': 250,
            'protein': 12.5,
          }));

      final m = await DishService.nutrition(3, serving: 2);

      expect(captor.last!.path, '/dish/3/nutrition');
      expect(captor.last!.queryParameters['serving'], 2);
      expect(m['calorie'], 250);
      expect(m['protein'], 12.5);
    });

    test('data 为 null → 空 Map', () async {
      installMock((_) => okResponse(null));

      expect(await DishService.nutrition(3), isEmpty);
    });

    test('value 为 null → 0', () async {
      installMock((_) => okResponse({'calorie': null}));

      final m = await DishService.nutrition(3);

      expect(m['calorie'], 0);
    });
  });

  group('DishService.metrics', () {
    test('GET /nutrition/metric → List<NutritionMetric>', () async {
      final captor = installMock((_) => okResponse([
            {'id': 1, 'name': 'calorie', 'unit': 'kcal'},
            {'id': 2, 'name': 'protein', 'unit': 'g'},
          ]));

      final list = await DishService.metrics();

      expect(captor.last!.path, '/nutrition/metric');
      expect(list.length, 2);
      expect(list[0].id, 1);
      expect(list[0].name, 'calorie');
      expect(list[0].unit, 'kcal');
    });
  });

  group('DishService.saveDish', () {
    test('POST /dish body 透传，返回新 id', () async {
      final captor = installMock((_) => okResponse(42));

      final id = await DishService.saveDish({'dish': {'name': '新菜'}});

      expect(captor.last!.path, '/dish');
      expect(captor.last!.data, {'dish': {'name': '新菜'}});
      expect(id, 42);
    });
  });

  group('DishService.importDishByUrl', () {
    test('POST /dish/import-url?url= ，返回新 id', () async {
      final captor = installMock((_) => okResponse(7));

      final id = await DishService.importDishByUrl('https://x.com/recipe');

      expect(captor.last!.path, '/dish/import-url');
      expect(captor.last!.queryParameters['url'], 'https://x.com/recipe');
      expect(id, 7);
    });
  });
}
