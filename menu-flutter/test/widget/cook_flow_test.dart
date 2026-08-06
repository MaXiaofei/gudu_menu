import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:menu_flutter/core/app_theme.dart';
import 'package:menu_flutter/pages/dish/detail_page.dart';
import '../helpers/mock_http.dart';

/// 给 widget 测试注入 AppTokens 主题。
Widget _themed(Widget child) => MaterialApp(
      theme: ThemeData(extensions: const [AppTokens.cream]),
      home: child,
    );

/// 详情页"加到食集"端到端（widget 级）。
void main() {
  testWidgets('无今天及以后的食集 → 点加到食集 → 弹输入框 → 确认新建', (tester) async {
    installMock((options) {
      if (options.path == '/dish/1') {
        return okResponse({
          'dish': {'id': 1, 'name': '番茄炒蛋', 'cookTime': 10, 'difficulty': 2},
          'steps': [{'seq': 1, 'text': '番茄切块'}],
        });
      }
      if (options.path == '/dish/1/nutrition') return okResponse({});
      if (options.path == '/nutrition/metric') return okResponse([]);
      // GET /menu 返回旧食集（createTime 远早于今天）
      if (options.path == '/menu' && options.method == 'GET') {
        return okResponse({
          'records': [
            {'id': 1, 'name': '旧食集', 'servingCount': 1, 'status': 'DONE',
             'createTime': '2020-01-01T00:00:00'},
          ],
          'total': 1,
        });
      }
      // POST /menu（createMenu）返回新 id
      if (options.path == '/menu' && options.method == 'POST') {
        return okResponse(99);
      }
      return okResponse({});
    });

    await tester.pumpWidget(_themed(const DishDetailPage(id: 1)));
    await tester.pumpAndSettle();

    await tester.tap(find.text('加到食集'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));

    // 弹出输入框（预填菜谱名"番茄炒蛋"）
    expect(find.text('新建食集'), findsOneWidget);
    expect(find.byType(TextField), findsOneWidget);

    // 点确定
    await tester.tap(find.text('确定'));
    await tester.pumpAndSettle();

    // 新建成功 → SnackBar
    expect(find.textContaining('已加入新食集'), findsOneWidget);
  });

  testWidgets('有今天食集 → 点加到食集 → 弹出选择弹窗', (tester) async {
    installMock((options) {
      if (options.path == '/dish/1') {
        return okResponse({
          'dish': {'id': 1, 'name': '番茄炒蛋', 'cookTime': 10},
          'steps': [],
        });
      }
      if (options.path == '/dish/1/nutrition') return okResponse({});
      if (options.path == '/nutrition/metric') return okResponse([]);
      // GET /menu 返回今天的食集
      if (options.path == '/menu' && options.method == 'GET') {
        return okResponse({
          'records': [
            {'id': 10, 'name': '今晚的饭', 'servingCount': 2, 'status': 'ACTIVE',
             'createTime': DateTime.now().toIso8601String()},
          ],
          'total': 1,
        });
      }
      return okResponse({});
    });

    await tester.pumpWidget(_themed(const DishDetailPage(id: 1)));
    await tester.pumpAndSettle();

    await tester.tap(find.text('加到食集'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));

    // 弹出 BottomSheet（不是输入框）
    expect(find.text('加到哪个食集？'), findsOneWidget);
    expect(find.text('今晚的饭'), findsOneWidget);
  });
}
