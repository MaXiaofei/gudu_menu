import 'package:flutter_test/flutter_test.dart';

import 'package:menu_flutter/services/menu_service.dart';
import '../helpers/mock_http.dart';

/// MenuService：列表/详情/增删改 + 加菜自动拼接名 + 整集做菜。
void main() {
  group('MenuService.list', () {
    test('GET /menu 默认分页，带 status 过滤', () async {
      final captor = installMock((_) => okResponse({
            'records': [
              {'id': 1, 'name': '番茄周', 'status': 'ACTIVE', 'dishCount': 2},
            ],
            'total': 1,
            'current': 1,
            'size': 15,
          }));

      final page = await MenuService.list(status: 'ACTIVE');

      expect(captor.last!.path, '/menu');
      expect(captor.last!.method, 'GET');
      expect(captor.last!.queryParameters['pageNum'], 1);
      expect(captor.last!.queryParameters['pageSize'], 15);
      expect(captor.last!.queryParameters['status'], 'ACTIVE');
      expect(page.records.length, 1);
      expect(page.records.first.id, 1);
      expect(page.records.first.name, '番茄周');
    });

    test('不带 status → query 不含 status', () async {
      final captor = installMock((_) => okResponse({
            'records': [],
            'total': 0,
          }));

      await MenuService.list();

      expect(captor.last!.queryParameters.containsKey('status'), isFalse);
    });
  });

  group('MenuService.detail', () {
    test('GET /menu/9 → 解析 menu + dishes + totalMinutes', () async {
      final captor = installMock((_) => okResponse({
            'menu': {
              'id': 9,
              'name': '周末餐',
              'servingCount': 2,
              'status': 'ACTIVE',
            },
            'dishes': [
              {'id': 100, 'menuId': 9, 'dishId': 1, 'dishName': '番茄炒蛋', 'servingFactor': 1},
              {'id': 101, 'menuId': 9, 'dishId': 2, 'dishName': '黄瓜', 'servingFactor': 2},
            ],
            'totalMinutes': 35,
          }));

      final detail = await MenuService.detail(9);

      expect(captor.last!.path, '/menu/9');
      expect(detail.menu.id, 9);
      expect(detail.menu.name, '周末餐');
      expect(detail.dishes.length, 2);
      expect(detail.dishes[0].dishId, 1);
      expect(detail.dishes[0].dishName, '番茄炒蛋');
      expect(detail.dishes[1].servingFactor, 2);
      expect(detail.totalMinutes, 35);
    });
  });

  group('MenuService.updateDishNote', () {
    test('PUT /menu/5/dish/3/note body 含 note', () async {
      final captor = installMock((_) => okResponse(null));

      await MenuService.updateDishNote(5, 3, '宝宝少盐');

      expect(captor.last!.method, 'PUT');
      expect(captor.last!.path, '/menu/5/dish/3/note');
      expect(captor.last!.data, {'note': '宝宝少盐'});
    });
  });

  group('MenuService.removeDishFromMenu', () {
    test('DELETE /menu/5/dish/3', () async {
      final captor = installMock((_) => okResponse(null));

      await MenuService.removeDishFromMenu(5, 3);

      expect(captor.last!.method, 'DELETE');
      expect(captor.last!.path, '/menu/5/dish/3');
    });
  });

  group('MenuService.getTogetherCount', () {
    test('GET /menu/5/together-count 返回整数', () async {
      final captor = installMock((_) => okResponse(3));

      final count = await MenuService.getTogetherCount(5);

      expect(captor.last!.path, '/menu/5/together-count');
      expect(count, 3);
    });

    test('data 为 null → 兜底 0', () async {
      installMock((_) => okResponse(null));

      final count = await MenuService.getTogetherCount(5);

      expect(count, 0);
    });
  });

  group('MenuService.deleteMenu', () {
    test('DELETE /menu/5', () async {
      final captor = installMock((_) => okResponse(null));

      await MenuService.deleteMenu(5);

      expect(captor.last!.method, 'DELETE');
      expect(captor.last!.path, '/menu/5');
    });
  });

  group('MenuService.createMenu', () {
    test('POST /menu 含 dishIds → 返回新 id，body 带 dishes', () async {
      final captor = installMock((_) => okResponse(7));

      final id = await MenuService.createMenu('番茄周', dishIds: [1, 2]);

      expect(captor.last!.method, 'POST');
      expect(captor.last!.path, '/menu');
      expect(id, 7);
      expect(captor.last!.data['menu'], {
        'name': '番茄周',
        'servingCount': 1,
        'status': 'ACTIVE',
      });
      expect(captor.last!.data['dishes'], [
        {'dishId': 1, 'servingFactor': 1},
        {'dishId': 2, 'servingFactor': 1},
      ]);
    });

    test('POST /menu 不带 dishIds → dishes 为空数组', () async {
      final captor = installMock((_) => okResponse(8));

      await MenuService.createMenu('空食集');

      expect(captor.last!.data['dishes'], isEmpty);
    });
  });

  group('MenuService.addDishToMenu', () {
    test('菜品已存在 → 只 GET 详情，不再 PUT', () async {
      final captor = installMock((options) {
        if (options.path == '/menu/5') {
          return okResponse({
            'menu': {'id': 5, 'name': '番茄炒蛋', 'servingCount': 2, 'status': 'ACTIVE'},
            'dishes': [
              {'id': 100, 'menuId': 5, 'dishId': 3, 'dishName': '番茄炒蛋', 'servingFactor': 1},
            ],
          });
        }
        return okResponse(null);
      });

      await MenuService.addDishToMenu(5, 3, dishName: '鸡蛋');

      // 只发了一次请求，且为 GET /menu/5
      expect(captor.all.length, 1);
      expect(captor.all.last.method, 'GET');
      expect(captor.all.last.path, '/menu/5');
    });

    test('当前名等于菜名拼接 → 重命名为含新菜的拼接名', () async {
      final captor = installMock((options) {
        if (options.path == '/menu/5') {
          return okResponse({
            'menu': {'id': 5, 'name': '番茄炒蛋_黄瓜', 'servingCount': 2, 'status': 'ACTIVE'},
            'dishes': [
              {'id': 100, 'menuId': 5, 'dishId': 1, 'dishName': '番茄炒蛋', 'servingFactor': 1},
              {'id': 101, 'menuId': 5, 'dishId': 2, 'dishName': '黄瓜', 'servingFactor': 1},
            ],
          });
        }
        return okResponse(null);
      });

      await MenuService.addDishToMenu(5, 3, dishName: '鸡蛋');

      // 第二次请求为 PUT /menu
      expect(captor.all.last.method, 'PUT');
      expect(captor.all.last.path, '/menu');
      // 重命名为含新菜的新拼接名
      expect(captor.all.last.data['menu']['name'], '番茄炒蛋_黄瓜_鸡蛋');
      // dishes 追加了新菜
      final dishes = captor.all.last.data['dishes'] as List;
      expect(dishes.length, 3);
      expect(dishes.last, {'dishId': 3, 'servingFactor': 1});
    });

    test('当前名为用户自定义 → 保留原名不重命名', () async {
      final captor = installMock((options) {
        if (options.path == '/menu/5') {
          return okResponse({
            'menu': {'id': 5, 'name': '我的私房菜', 'servingCount': 2, 'status': 'ACTIVE'},
            'dishes': [
              {'id': 100, 'menuId': 5, 'dishId': 1, 'dishName': '番茄炒蛋', 'servingFactor': 1},
              {'id': 101, 'menuId': 5, 'dishId': 2, 'dishName': '黄瓜', 'servingFactor': 1},
            ],
          });
        }
        return okResponse(null);
      });

      await MenuService.addDishToMenu(5, 3, dishName: '鸡蛋');

      expect(captor.all.last.method, 'PUT');
      expect(captor.all.last.path, '/menu');
      // 用户自定义名不等于拼接（番茄炒蛋_黄瓜），保留原名
      expect(captor.all.last.data['menu']['name'], '我的私房菜');
      final dishes = captor.all.last.data['dishes'] as List;
      expect(dishes.length, 3);
    });
  });

  group('MenuService.cookMenu', () {
    test('POST /menu/5/cook body 含 usedUp/partiallyUsed → CookResult', () async {
      final captor = installMock((_) => okResponse({
            'menuId': 5,
            'cookingRecordIds': [11, 12],
          }));

      final result = await MenuService.cookMenu(
        5,
        usedUp: [1, 2],
        partiallyUsed: [3],
      );

      expect(captor.last!.method, 'POST');
      expect(captor.last!.path, '/menu/5/cook');
      expect(captor.last!.data, {
        'usedUp': [1, 2],
        'partiallyUsed': [3],
      });
      expect(result.menuId, 5);
      expect(result.cookingRecordIds, [11, 12]);
    });
  });
}
