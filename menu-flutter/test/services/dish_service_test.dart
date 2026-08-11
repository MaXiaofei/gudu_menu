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

  group('DishService.saveDraft', () {
    test('POST /dish/draft body 透传，返回草稿 id', () async {
      final captor = installMock((_) => okResponse(99));

      final id = await DishService.saveDraft({'name': '草稿1'});

      expect(captor.last!.method, 'POST');
      expect(captor.last!.path, '/dish/draft');
      expect(captor.last!.data, {'name': '草稿1'});
      expect(id, 99);
    });
  });

  group('DishService.listDrafts', () {
    test('GET /dish/draft/list?pageNum=&pageSize= ，解析分页', () async {
      final captor = installMock((_) => okResponse({
            'records': [
              {
                'id': 11,
                'name': '草稿A',
                'coverUrl': 'c.jpg',
                'ingredientCount': 3,
                'stepCount': 5,
                'updateTime': '2024-01-02 03:04:05',
              },
            ],
            'total': 1,
            'current': 1,
            'size': 10,
          }));

      final page = await DishService.listDrafts(pageNum: 1, pageSize: 10);

      expect(captor.last!.path, '/dish/draft/list');
      expect(captor.last!.queryParameters['pageNum'], 1);
      expect(captor.last!.queryParameters['pageSize'], 10);
      expect(page.total, 1);
      expect(page.records.length, 1);
      expect(page.records[0].id, 11);
      expect(page.records[0].name, '草稿A');
    });
  });

  group('DishService.draftDetail', () {
    test('GET /dish/draft/{id} → 解析 DishDraftDetail', () async {
      final captor = installMock((_) => okResponse({
            'id': 7,
            'name': '草稿详情',
            'tagIds': [1, 2],
            'cuisineIds': [9],
            'ingredients': [
              {'ingredientId': 10, 'ingredientName': '盐', 'amount': '1', 'unitText': '勺'},
            ],
            'steps': [
              {'seq': 1, 'text': '下锅', 'images': 'a.jpg,b.jpg'},
            ],
          }));

      final d = await DishService.draftDetail(7);

      expect(captor.last!.path, '/dish/draft/7');
      expect(d.id, 7);
      expect(d.name, '草稿详情');
      expect(d.tagIds, [1, 2]);
      expect(d.cuisineIds, [9]);
      expect(d.ingredients.length, 1);
      expect(d.ingredients[0].ingredientId, 10);
      expect(d.ingredients[0].ingredientName, '盐');
      expect(d.ingredients[0].amount, '1');
      expect(d.ingredients[0].unitText, '勺');
      expect(d.steps.length, 1);
      expect(d.steps[0].text, '下锅');
      expect(d.steps[0].imageList, ['a.jpg', 'b.jpg']);
    });
  });

  group('DishService.deleteDraft', () {
    test('DELETE /dish/draft/{id}', () async {
      final captor = installMock((_) => okResponse(null));

      await DishService.deleteDraft(7);

      expect(captor.last!.method, 'DELETE');
      expect(captor.last!.path, '/dish/draft/7');
    });
  });

  group('DishService.cookMaterials', () {
    test('GET /menu/{menuId}/cook-materials → 解析 CookMaterials', () async {
      final captor = installMock((_) => okResponse({
            'menuId': 5,
            'items': [
              {
                'ingredientId': 1,
                'ingredientName': '番茄',
                'usageTexts': ['番茄炒蛋 2个'],
                'level': 'ENOUGH',
                'isCondiment': false,
              },
              {
                'ingredientId': 2,
                'ingredientName': '酱油',
                'isCondiment': true,
              },
            ],
          }));

      final m = await DishService.cookMaterials(5);

      expect(captor.last!.path, '/menu/5/cook-materials');
      expect(m.menuId, 5);
      expect(m.items.length, 2);
      expect(m.items[0].ingredientId, 1);
      expect(m.items[0].ingredientName, '番茄');
      expect(m.items[0].usageTexts, ['番茄炒蛋 2个']);
      expect(m.items[0].level, 'ENOUGH');
      expect(m.items[0].isCondiment, isFalse);
      expect(m.items[1].isCondiment, isTrue);
    });
  });

  group('DishDraftItem.fromJson', () {
    test('完整字段解析', () {
      final item = DishDraftItem.fromJson({
        'id': 3,
        'name': '鱼香肉丝',
        'coverUrl': 'cover.jpg',
        'ingredientCount': 4,
        'stepCount': 6,
        'updateTime': '2024-05-06 07:08:09',
      });

      expect(item.id, 3);
      expect(item.name, '鱼香肉丝');
      expect(item.coverUrl, 'cover.jpg');
      expect(item.ingredientCount, 4);
      expect(item.stepCount, 6);
      expect(item.updateTime, DateTime.parse('2024-05-06 07:08:09'));
    });

    test('缺 ingredientCount/stepCount → 默认 0；coverUrl 可空', () {
      final item = DishDraftItem.fromJson({
        'id': 3,
        'name': '无名',
        'updateTime': '2024-05-06 07:08:09',
      });

      expect(item.coverUrl, isNull);
      expect(item.ingredientCount, 0);
      expect(item.stepCount, 0);
    });
  });

  group('DishDraftDetail.fromJson', () {
    test('完整字段解析', () {
      final d = DishDraftDetail.fromJson({
        'id': 1,
        'name': 'x',
        'coverUrl': 'c.jpg',
        'prepTime': 10,
        'cookTime': 20,
        'difficulty': 3,
        'note': '备注',
        'tagIds': [1, 2],
        'cuisineIds': [3],
        'ingredients': [
          {'ingredientId': 5, 'ingredientName': '油', 'amount': '', 'unitText': '少许'},
        ],
        'steps': [
          {'seq': 1, 'text': '热锅'},
        ],
      });

      expect(d.id, 1);
      expect(d.coverUrl, 'c.jpg');
      expect(d.prepTime, 10);
      expect(d.cookTime, 20);
      expect(d.difficulty, 3);
      expect(d.note, '备注');
      expect(d.tagIds, [1, 2]);
      expect(d.cuisineIds, [3]);
      expect(d.ingredients.length, 1);
      expect(d.steps.length, 1);
    });

    test('tagIds/ingredients/steps 为 null → 空列表', () {
      final d = DishDraftDetail.fromJson({'id': 1, 'name': 'x'});

      expect(d.tagIds, isEmpty);
      expect(d.cuisineIds, isEmpty);
      expect(d.ingredients, isEmpty);
      expect(d.steps, isEmpty);
    });
  });

  group('DraftStepItem.imageList', () {
    test('逗号分隔图片 → 列表', () {
      final step = DraftStepItem.fromJson({
        'seq': 1,
        'text': 't',
        'images': 'a.jpg,b.jpg',
      });

      expect(step.imageList, ['a.jpg', 'b.jpg']);
    });

    test('images 为 null → 空列表', () {
      final step = DraftStepItem.fromJson({'seq': 1, 'text': 't'});

      expect(step.imageList, isEmpty);
    });

    test('images 为空串 → 空列表', () {
      final step = DraftStepItem.fromJson({
        'seq': 1,
        'text': 't',
        'images': '',
      });

      expect(step.imageList, isEmpty);
    });
  });

  group('CookMaterialItem.fromJson', () {
    test('缺 level/isCondiment/usageTexts → 默认值', () {
      final item = CookMaterialItem.fromJson({
        'ingredientId': 8,
        'ingredientName': '盐',
      });

      expect(item.ingredientId, 8);
      expect(item.ingredientName, '盐');
      expect(item.usageTexts, isEmpty);
      expect(item.level, 'NONE');
      expect(item.isCondiment, isFalse);
    });

    test('完整字段解析', () {
      final item = CookMaterialItem.fromJson({
        'ingredientId': 9,
        'ingredientName': '糖',
        'usageTexts': ['番茄炒蛋 10g'],
        'level': 'LOW',
        'isCondiment': true,
      });

      expect(item.usageTexts, ['番茄炒蛋 10g']);
      expect(item.level, 'LOW');
      expect(item.isCondiment, isTrue);
    });
  });
}
