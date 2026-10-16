import 'package:flutter_test/flutter_test.dart';

import 'package:menu_flutter/models/dish.dart';
import 'package:menu_flutter/services/dish_service.dart';
import 'package:menu_flutter/services/feedback_service.dart';
import 'package:menu_flutter/services/ingredient_service.dart';
import 'package:menu_flutter/services/member_service.dart';
import 'package:menu_flutter/services/pantry_service.dart';
import 'package:menu_flutter/services/recommend_service.dart';
import '../helpers/mock_http.dart';

/// feature-gaps-closure（api-contract.md）新增/变更接口的 service 层契约测试。
void main() {
  group('单菜直做（§2）', () {
    test('GET /cook/dish/{id}/materials?servings= → CookMaterials(menuId=null)', () async {
      final captor = installMock((_) => okResponse({
            'menuId': null,
            'items': [
              {
                'ingredientId': 3,
                'ingredientName': '番茄',
                'usageTexts': ['2个'],
                'level': 'ENOUGH',
                'isCondiment': false,
              },
            ],
          }));
      final m = await DishService.dishCookMaterials(7, servings: 2);
      expect(captor.last!.path, '/cook/dish/7/materials');
      expect(captor.last!.queryParameters['servings'], 2);
      expect(m.menuId, isNull);
      expect(m.items.single.ingredientName, '番茄');
    });

    test('POST /cook/dish/{id} body 含 servings/usedUp/partiallyUsed → recordId', () async {
      final captor = installMock((_) => okResponse({'recordId': 55, 'dishId': 7}));
      final r = await DishService.cookDish(7,
          usedUp: [3], partiallyUsed: [9], note: '少放盐');
      expect(captor.last!.method, 'POST');
      expect(captor.last!.path, '/cook/dish/7');
      expect(captor.last!.data, {
        'servings': 1,
        'usedUp': [3],
        'partiallyUsed': [9],
        'note': '少放盐',
      });
      expect(r.recordId, 55);
      expect(r.dishId, 7);
    });
  });

  group('覆盖率（§3）', () {
    test('search sort=coverage 透传；记录解析 coverage', () async {
      final captor = installMock((_) => okResponse({
            'records': [
              {
                'id': 1,
                'name': '番茄炒蛋',
                'coverage': {
                  'enough': 1,
                  'total': 2,
                  'missing': [
                    {'ingredientId': 5, 'name': '鸡蛋'},
                  ],
                },
              },
              {'id': 2, 'name': '白粥', 'coverage': null},
            ],
            'total': 2,
          }));
      final page = await DishService.search(sort: 'coverage');
      expect(captor.last!.queryParameters['sort'], 'coverage');
      final c = page.records.first.coverage!;
      expect(c.label, '家里够 1/2 样');
      expect(c.isFull, isFalse);
      expect(c.missingNames, ['鸡蛋']);
      expect(page.records[1].coverage, isNull);
    });

    test('Coverage：全够 isFull；total=0（全是调料）label 为空', () {
      expect(const Coverage(enough: 3, total: 3).isFull, isTrue);
      expect(const Coverage(enough: 0, total: 0).label, '');
    });
  });

  group('常用食材（§4）', () {
    test('GET /ingredient/frequent?limit=8 → IngredientItem 列表', () async {
      final captor = installMock((_) => okResponse([
            {'id': 1, 'name': '鸡蛋'},
            {'id': 2, 'name': '番茄'},
          ]));
      final list = await IngredientService.frequent();
      expect(captor.last!.path, '/ingredient/frequent');
      expect(captor.last!.queryParameters['limit'], 8);
      expect(list.map((e) => e.name), ['鸡蛋', '番茄']);
    });
  });

  group('余量保质期（§1）', () {
    test('GET /pantry/shelf-life → suggestedDays', () async {
      final captor = installMock((_) => okResponse({'suggestedDays': 7}));
      expect(await PantryService.shelfLife(3), 7);
      expect(captor.last!.queryParameters['ingredientId'], 3);
    });

    test('shelf-life 无建议 → null', () async {
      installMock((_) => okResponse({'suggestedDays': null}));
      expect(await PantryService.shelfLife(3), isNull);
    });

    test('setLevel 三态：不传=不带字段；null=清除；日期=YYYY-MM-DD', () async {
      final captor = installMock((_) => okResponse(null));
      await PantryService.setLevel(1, 'ENOUGH');
      expect((captor.last!.data as Map).containsKey('bestBefore'), isFalse);

      await PantryService.setLevel(1, 'ENOUGH', bestBefore: null);
      expect((captor.last!.data as Map).containsKey('bestBefore'), isTrue);
      expect((captor.last!.data as Map)['bestBefore'], isNull);

      await PantryService.setLevel(1, 'LOW', bestBefore: DateTime(2026, 3, 5));
      expect((captor.last!.data as Map)['bestBefore'], '2026-03-05');
      expect(captor.last!.path, '/pantry/1/level');
    });

    test('manualAdd 带 bestBefore', () async {
      final captor = installMock((_) => okResponse(null));
      await PantryService.manualAdd(
          ingredientId: 2, level: 'ENOUGH', bestBefore: DateTime(2026, 10, 20));
      expect((captor.last!.data as Map)['bestBefore'], '2026-10-20');
    });

    test('grouped / item 解析 bestBefore + daysLeft', () async {
      installMock((o) => o.path == '/pantry/item'
          ? okResponse({
              'ingredientId': 1,
              'ingredientName': '牛奶',
              'level': 'ENOUGH',
              'bestBefore': '2026-10-18',
              'daysLeft': 2,
              'changes': [],
            })
          : okResponse({
              'summary': {'enough': 1, 'low': 0, 'none': 0},
              'items': [
                {
                  'ingredientId': 1,
                  'ingredientName': '牛奶',
                  'level': 'ENOUGH',
                  'bestBefore': '2026-10-18',
                  'daysLeft': 2,
                },
              ],
            }));
      final g = await PantryService.listGrouped();
      expect(g.items.single.daysLeft, 2);
      expect(g.items.single.bestBefore, '2026-10-18');
      final d = await PantryService.itemDetail(1);
      expect(d.daysLeft, 2);
    });

    test('daysLeftLabel 文案', () {
      expect(daysLeftLabel(-2), '已过期 2 天');
      expect(daysLeftLabel(0), '今天到期');
      expect(daysLeftLabel(3), '剩 3 天');
    });
  });

  group('个人资料（§6）', () {
    test('GET /member/me', () async {
      installMock((_) => okResponse({
            'id': 9,
            'name': '妈妈',
            'avatarUrl': '/gudu/uploads/original/a.jpg',
            'phone': '138****0001',
            'isAdmin': false,
          }));
      final me = await MemberService.me();
      expect(me.name, '妈妈');
      expect(me.phone, '138****0001');
    });

    test('PUT /member/me 只带传入字段', () async {
      final captor = installMock((_) => okResponse(null));
      await MemberService.updateMe(name: '老妈');
      expect(captor.last!.method, 'PUT');
      expect(captor.last!.path, '/member/me');
      expect(captor.last!.data, {'name': '老妈'});
    });
  });

  group('意见反馈（§7）', () {
    test('POST /feedback：空联系方式不带；图片最多 3 张', () async {
      final captor = installMock((_) => okResponse(12));
      final id = await FeedbackService.submit(
        content: '菜谱页加载很慢',
        contact: '',
        images: ['/u/1.jpg', '/u/2.jpg', '/u/3.jpg', '/u/4.jpg'],
        clientInfo: 'flutter/ios v1.0.0',
      );
      expect(id, 12);
      expect(captor.last!.path, '/feedback');
      final body = captor.last!.data as Map;
      expect(body.containsKey('contact'), isFalse);
      expect((body['images'] as List).length, 3);
      expect(body['clientInfo'], 'flutter/ios v1.0.0');
    });
  });

  group('智荐首页（§4/§5）', () {
    test('GET /recommend/home 解析四个分区', () async {
      installMock((_) => okResponse({
            'expiring': [
              {
                'ingredientId': 1,
                'ingredientName': '豆腐',
                'level': 'ENOUGH',
                'bestBefore': '2026-10-17',
                'daysLeft': 1,
              },
            ],
            'expiringPicks': [
              {
                'dishId': 3,
                'name': '麻婆豆腐',
                'coverImage': null,
                'cookMinutes': 15,
                'difficulty': 2,
                'cookedCount': 1,
                'coverage': {'enough': 2, 'total': 3, 'missing': []},
              },
            ],
            'frequent': [],
            'seasonal': [],
          }));
      final h = await RecommendService.home();
      expect(h.expiring.single.ingredientName, '豆腐');
      expect(h.expiringPicks.single.coverage!.label, '家里够 2/3 样');
      expect(h.frequent, isEmpty);
      expect(h.isEmpty, isFalse);
    });

    test('GET /cookbook/recent?limit= 上限 30', () async {
      final captor = installMock((_) => okResponse([
            {
              'dishId': 1,
              'name': '番茄炒蛋',
              'coverImage': null,
              'lastCookedAt': '2026-10-15T18:00:00',
              'cookedCount': 4,
            },
          ]));
      final list = await RecommendService.recent(limit: 99);
      expect(captor.last!.path, '/cookbook/recent');
      expect(captor.last!.queryParameters['limit'], 30);
      expect(list.single.cookedCount, 4);
      expect(list.single.lastCookedLabel(DateTime(2026, 10, 16, 9)), '昨天');
    });

    test('RecentCooked.lastCookedLabel：今天 / N 天前 / M/D', () {
      final now = DateTime(2026, 10, 16, 20);
      RecentCooked at(DateTime d) =>
          RecentCooked(dishId: 1, name: 'x', lastCookedAt: d);
      expect(at(DateTime(2026, 10, 16, 8)).lastCookedLabel(now), '今天');
      expect(at(DateTime(2026, 10, 12)).lastCookedLabel(now), '4 天前');
      expect(at(DateTime(2026, 9, 1)).lastCookedLabel(now), '9/1');
    });
  });
}
