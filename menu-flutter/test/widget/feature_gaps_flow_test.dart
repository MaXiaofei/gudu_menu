import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:menu_flutter/core/app_theme.dart';
import 'package:menu_flutter/pages/ai/smart_home_sections.dart';
import 'package:menu_flutter/pages/dish/detail_page.dart';
import 'package:menu_flutter/pages/dish/list_page.dart';
import 'package:menu_flutter/pages/pantry/detail_page.dart';
import 'package:menu_flutter/pages/pantry/list_page.dart';
import 'package:menu_flutter/pages/pantry/manual_add_page.dart';
import 'package:menu_flutter/services/pantry_service.dart' show fmtDate;
import 'package:menu_flutter/widgets/error_view.dart';
import 'package:menu_flutter/widgets/loading_empty.dart';
import '../helpers/mock_http.dart';

Widget _themed(Widget child) => MaterialApp(
      theme: buildBrandTheme(AppTokens.cream),
      home: child,
    );

/// 先放一个底页再 push 目标页（页面保存后 pop 需要有落点）。
Future<void> _pushed(WidgetTester tester, Widget page) async {
  await tester.pumpWidget(_themed(Builder(
    builder: (ctx) => Scaffold(
      body: Center(
        child: TextButton(
          onPressed: () =>
              Navigator.of(ctx).push(MaterialPageRoute(builder: (_) => page)),
          child: const Text('open'),
        ),
      ),
    ),
  )));
  await tester.tap(find.text('open'));
  await tester.pumpAndSettle();
}

DateTime _today() {
  final n = DateTime.now();
  return DateTime(n.year, n.month, n.day);
}

/// feature-gaps-closure Flutter 页面流程：单菜直做 / 覆盖率 / 保质期 / 智荐首页。
void main() {
  group('单菜直做（G1）', () {
    dynamic responder(RequestOptions o) {
      if (o.path == '/dish/1') {
        return okResponse({
          'dish': {'id': 1, 'name': '番茄炒蛋', 'cookTime': 10, 'difficulty': 2},
          'steps': [
            {'seq': 1, 'text': '番茄切块'},
          ],
        });
      }
      if (o.path == '/cook/dish/1/materials') {
        return okResponse({
          'menuId': null,
          'items': [
            {
              'ingredientId': 3,
              'ingredientName': '番茄',
              'usageTexts': ['番茄炒蛋 2个'],
              'level': 'ENOUGH',
              'isCondiment': false,
            },
            {
              'ingredientId': 9,
              'ingredientName': '盐',
              'usageTexts': [],
              'level': 'ENOUGH',
              'isCondiment': true,
            },
          ],
        });
      }
      if (o.path == '/cook/dish/1' && o.method == 'POST') {
        return okResponse({'recordId': 77, 'dishId': 1});
      }
      return okResponse({});
    }

    testWidgets('现在就做 → 确认弹窗默认 食材=用完了/调料=用了一些 → POST /cook/dish/1 → 结果页',
        (tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 3;
      addTearDown(tester.view.reset);
      final captor = installMock(responder);
      await tester.pumpWidget(_themed(const DishDetailPage(id: 1)));
      await tester.pumpAndSettle();

      await tester.ensureVisible(find.byKey(const Key('dish-cook-now')));
      await tester.tap(find.byKey(const Key('dish-cook-now')));
      await tester.pumpAndSettle();

      expect(captor.all.any((o) => o.path == '/cook/dish/1/materials'), isTrue);
      expect(find.text('这顿饭用了什么'), findsOneWidget);
      expect(find.text('确认已做完 · 1 样用完'), findsOneWidget);

      await tester.tap(find.text('确认已做完 · 1 样用完'));
      await tester.pumpAndSettle();

      final post = captor.all.lastWhere((o) => o.path == '/cook/dish/1');
      expect(post.method, 'POST');
      expect(post.data, {
        'servings': 1,
        'usedUp': [3],
        'partiallyUsed': [9],
      });
      // 单菜结果页：已记入食记 + 「完成」按钮（不出现「返回食集」）
      expect(find.text('已记入食记'), findsOneWidget);
      expect(find.text('完成'), findsOneWidget);
      expect(find.text('返回食集'), findsNothing);
    });

    testWidgets('跳过不更新库存 → POST 空 usedUp/partiallyUsed（只记食记）', (tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 3;
      addTearDown(tester.view.reset);
      final captor = installMock(responder);
      await tester.pumpWidget(_themed(const DishDetailPage(id: 1)));
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.byKey(const Key('dish-cook-now')));
      await tester.tap(find.byKey(const Key('dish-cook-now')));
      await tester.pumpAndSettle();

      await tester.tap(find.text('跳过，不更新库存'));
      await tester.pumpAndSettle();
      final post = captor.all.lastWhere((o) => o.path == '/cook/dish/1');
      expect(post.data['usedUp'], isEmpty);
      expect(post.data['partiallyUsed'], isEmpty);
      expect(find.text('做好了，已记入食记'), findsOneWidget);
    });

    testWidgets('拉用料失败 → 提示失败，不弹确认', (tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 3;
      addTearDown(tester.view.reset);
      installMock((o) => o.path == '/cook/dish/1/materials'
          ? errResponse(500, '菜谱已下架')
          : responder(o));
      await tester.pumpWidget(_themed(const DishDetailPage(id: 1)));
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.byKey(const Key('dish-cook-now')));
      await tester.tap(find.byKey(const Key('dish-cook-now')));
      await tester.pumpAndSettle();
      expect(find.text('这顿饭用了什么'), findsNothing);
      expect(find.text('做菜记录失败：菜谱已下架'), findsOneWidget);
    });
  });

  group('菜谱覆盖率（G3）', () {
    testWidgets('列表显示「家里够 X/Y 样」；点「家里够的优先」→ sort=coverage', (tester) async {
      final captor = installMock((o) {
        if (o.path == '/dish/search') {
          return okResponse({
            'records': [
              {
                'id': 1,
                'name': '番茄炒蛋',
                'cookTime': 10,
                'coverage': {'enough': 2, 'total': 2, 'missing': []},
              },
              {
                'id': 2,
                'name': '红烧肉',
                'cookTime': 60,
                'coverage': {
                  'enough': 1,
                  'total': 4,
                  'missing': [
                    {'ingredientId': 8, 'name': '五花肉'},
                  ],
                },
              },
              {'id': 3, 'name': '白开水', 'coverage': null},
            ],
            'total': 3,
          });
        }
        return okResponse({'records': [], 'total': 0});
      });
      await tester.pumpWidget(_themed(const DishListPage()));
      await tester.pumpAndSettle();

      expect(find.text('家里够 2/2 样'), findsOneWidget);
      expect(find.text('家里够 1/4 样'), findsOneWidget);

      await tester.tap(find.text('家里够的优先'));
      await tester.pumpAndSettle();
      final last = captor.all.lastWhere((o) => o.path == '/dish/search');
      expect(last.queryParameters['sort'], 'coverage');
    });

    testWidgets('加载失败 → 错误态 + 重试（不再静默成空列表）', (tester) async {
      var fail = true;
      installMock((o) {
        if (o.path == '/dish/search') {
          return fail
              ? errResponse(500, 'boom')
              : okResponse({
                  'records': [
                    {'id': 1, 'name': '番茄炒蛋'},
                  ],
                  'total': 1,
                });
        }
        return okResponse({'records': [], 'total': 0});
      });
      await tester.pumpWidget(_themed(const DishListPage()));
      await tester.pumpAndSettle();
      expect(find.text('菜谱加载失败'), findsOneWidget);

      fail = false;
      await tester.tap(find.text('重试'));
      await tester.pumpAndSettle();
      expect(find.text('番茄炒蛋'), findsOneWidget);
    });
  });

  group('保质期（G2）', () {
    testWidgets('余量列表：有 daysLeft 显示剩余天数徽章，已用完不显示', (tester) async {
      installMock((o) {
        if (o.path == '/pantry/grouped') {
          final level = o.queryParameters['level'] as String?;
          final all = [
            {'ingredientId': 1, 'ingredientName': '牛奶', 'level': 'ENOUGH', 'bestBefore': '2026-10-17', 'daysLeft': 1},
            {'ingredientId': 2, 'ingredientName': '豆腐', 'level': 'LOW', 'bestBefore': '2026-10-14', 'daysLeft': -2},
            {'ingredientId': 3, 'ingredientName': '鸡蛋', 'level': 'NONE', 'bestBefore': '2026-10-10', 'daysLeft': -6},
            {'ingredientId': 4, 'ingredientName': '大米', 'level': 'ENOUGH', 'bestBefore': null, 'daysLeft': null},
          ];
          return okResponse({
            'summary': {'enough': 2, 'low': 1, 'none': 1},
            'items': all.where((it) => level == null || it['level'] == level).toList(),
          });
        }
        return okResponse({});
      });
      await tester.pumpWidget(_themed(const PantryListPage()));
      await tester.pumpAndSettle();

      expect(find.text('剩 1 天'), findsOneWidget);
      expect(find.text('已过期 2 天'), findsOneWidget);
      expect(find.byKey(const ValueKey('days-left-3')), findsNothing); // 已用完
      expect(find.byKey(const ValueKey('days-left-4')), findsNothing); // 未填
    });

    testWidgets('入库 ②：按 shelf-life 预填 今天+N，入库带 bestBefore', (tester) async {
      final captor = installMock((o) {
        if (o.path == '/ingredient') {
          return okResponse({
            'records': [
              {'id': 1, 'name': '牛奶'},
            ],
            'total': 1,
          });
        }
        if (o.path == '/pantry/grouped') {
          return okResponse({'summary': {}, 'items': []});
        }
        if (o.path == '/pantry/shelf-life') return okResponse({'suggestedDays': 7});
        return okResponse(null);
      });
      await tester.pumpWidget(_themed(const PantryManualAddPage()));
      await tester.pumpAndSettle();

      await tester.enterText(find.byType(TextField), '牛奶');
      await tester.pump();
      await tester.tap(find.text('牛奶').last);
      await tester.pump();
      await tester.tap(find.byType(ElevatedButton));
      await tester.pumpAndSettle();

      final expected = fmtDate(_today().add(const Duration(days: 7)));
      expect(captor.all.any((o) => o.path == '/pantry/shelf-life'), isTrue);
      expect(find.text('保质期至 $expected'), findsOneWidget);
      expect(find.text('按品类建议 7 天，可修改'), findsOneWidget);
      expect(find.text('剩 7 天'), findsOneWidget);

      await tester.tap(find.text('入库').last);
      await tester.pumpAndSettle();
      final post = captor.all.lastWhere((o) => o.path == '/pantry/manual');
      expect((post.data as Map)['bestBefore'], expected);
    });

    testWidgets('入库 ②：清除预填保质期 → 不带 bestBefore', (tester) async {
      final captor = installMock((o) {
        if (o.path == '/ingredient') {
          return okResponse({
            'records': [
              {'id': 1, 'name': '牛奶'},
            ],
            'total': 1,
          });
        }
        if (o.path == '/pantry/grouped') return okResponse({'summary': {}, 'items': []});
        if (o.path == '/pantry/shelf-life') return okResponse({'suggestedDays': 3});
        return okResponse(null);
      });
      await tester.pumpWidget(_themed(const PantryManualAddPage()));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), '牛奶');
      await tester.pump();
      await tester.tap(find.text('牛奶').last);
      await tester.pump();
      await tester.tap(find.byType(ElevatedButton));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('best-before-clear')));
      await tester.pump();
      expect(find.text('未填保质期（选填）'), findsOneWidget);
      await tester.tap(find.text('入库').last);
      await tester.pumpAndSettle();
      final post = captor.all.lastWhere((o) => o.path == '/pantry/manual');
      expect((post.data as Map).containsKey('bestBefore'), isFalse);
    });

    testWidgets('余量详情：显示现有保质期；清除后保存 → PUT bestBefore:null；未改则不带', (tester) async {
      final captor = installMock((o) {
        if (o.path == '/pantry/item') {
          return okResponse({
            'ingredientId': 1,
            'ingredientName': '牛奶',
            'level': 'ENOUGH',
            'bestBefore': fmtDate(_today().add(const Duration(days: 2))),
            'daysLeft': 2,
            'changes': [],
          });
        }
        return okResponse(null);
      });

      // 未改保质期 → 不带字段
      await _pushed(tester, const PantryDetailPage(ingredientId: 1));
      expect(find.text('剩 2 天'), findsOneWidget);
      await tester.tap(find.text('保存'));
      await tester.pumpAndSettle();
      var put = captor.all.lastWhere((o) => o.method == 'PUT');
      expect((put.data as Map).containsKey('bestBefore'), isFalse);

      // 清除 → 带 null
      await tester.tap(find.text('open'));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('best-before-clear')));
      await tester.pump();
      await tester.tap(find.text('保存'));
      await tester.pumpAndSettle();
      put = captor.all.lastWhere((o) => o.method == 'PUT');
      expect((put.data as Map).containsKey('bestBefore'), isTrue);
      expect((put.data as Map)['bestBefore'], isNull);
    });
  });

  group('智荐首页（G4/G5）', () {
    Widget host() => _themed(const Scaffold(
          body: SingleChildScrollView(child: SmartHomeSections()),
        ));

    Map<String, dynamic> card(int id, String name, {Map<String, dynamic>? coverage}) => {
          'dishId': id,
          'name': name,
          'coverImage': null,
          'cookMinutes': 10,
          'difficulty': 1,
          'cookedCount': 2,
          'coverage': coverage,
        };

    testWidgets('有数据分区渲染、空分区隐藏；最近做过来自 /cookbook/recent', (tester) async {
      installMock((o) {
        if (o.path == '/recommend/home') {
          return okResponse({
            'expiring': [
              {'ingredientId': 1, 'ingredientName': '豆腐', 'level': 'ENOUGH', 'bestBefore': '2026-10-16', 'daysLeft': 0},
            ],
            'expiringPicks': [
              card(3, '麻婆豆腐', coverage: {'enough': 2, 'total': 3, 'missing': []}),
            ],
            'frequent': [card(4, '番茄炒蛋')],
            'seasonal': [],
          });
        }
        if (o.path == '/cookbook/recent') {
          return okResponse([
            {'dishId': 5, 'name': '可乐鸡翅', 'coverImage': null, 'lastCookedAt': DateTime.now().toIso8601String(), 'cookedCount': 3},
          ]);
        }
        return okResponse(null);
      });
      await tester.pumpWidget(host());
      expect(find.byType(LoadingView), findsOneWidget); // 骨架屏
      await tester.pumpAndSettle();

      expect(find.text('临期主推'), findsOneWidget);
      expect(find.text('豆腐 · 今天到期'), findsOneWidget);
      expect(find.text('麻婆豆腐'), findsOneWidget);
      expect(find.text('家里够 2/3 样'), findsOneWidget);
      expect(find.text('你常做'), findsOneWidget);
      expect(find.text('番茄炒蛋'), findsOneWidget);
      expect(find.text('季节鲜'), findsNothing); // 空分区隐藏
      expect(find.text('最近做过'), findsOneWidget);
      expect(find.text('可乐鸡翅'), findsOneWidget);
      expect(find.text('今天 · 做过 3 次'), findsOneWidget);
    });

    testWidgets('全部为空 → 不渲染任何分区', (tester) async {
      installMock((o) => o.path == '/cookbook/recent'
          ? okResponse([])
          : okResponse({'expiring': [], 'expiringPicks': [], 'frequent': [], 'seasonal': []}));
      await tester.pumpWidget(host());
      await tester.pumpAndSettle();
      for (final s in ['临期主推', '你常做', '季节鲜', '最近做过']) {
        expect(find.text(s), findsNothing);
      }
      expect(find.byType(ErrorView), findsNothing);
    });

    testWidgets('失败 → 错误态 + 重试恢复', (tester) async {
      var fail = true;
      installMock((o) {
        if (fail) return errResponse(500, '服务异常');
        if (o.path == '/cookbook/recent') return okResponse([]);
        return okResponse({'expiring': [], 'expiringPicks': [], 'frequent': [card(4, '番茄炒蛋')], 'seasonal': []});
      });
      await tester.pumpWidget(host());
      await tester.pumpAndSettle();
      expect(find.text('智荐加载失败'), findsOneWidget);

      fail = false;
      await tester.tap(find.text('重试'));
      await tester.pumpAndSettle();
      expect(find.text('你常做'), findsOneWidget);
    });
  });
}
