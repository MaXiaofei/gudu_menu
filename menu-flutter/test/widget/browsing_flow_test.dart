import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:menu_flutter/core/app_theme.dart';
import 'package:menu_flutter/pages/dish/list_page.dart';
import '../helpers/mock_http.dart';

/// 给 widget 测试注入 AppTokens 主题。
Widget _themed(Widget child) => MaterialApp(
      theme: ThemeData(extensions: const [AppTokens.cream]),
      home: child,
    );

/// 菜库浏览端到端（widget 级，按原型 cookbook-search.html）：
/// DishListPage → 浅色顶栏 + 纯搜索框 + 分类标签条 + 排序 + 卡片式列表。
void main() {
  testWidgets('菜库列表渲染 mock 菜品（卡片式）', (tester) async {
    installMock((options) {
      if (options.path == '/dish/search') {
        return okResponse({
          'records': [
            {
              'id': 1,
              'name': '番茄炒蛋',
              'cookTime': 10,
              'difficulty': 1,
              'cookedCount': 6,
            },
            {'id': 2, 'name': '红烧肉', 'cookTime': 60, 'difficulty': 3},
          ],
          'total': 2,
        });
      }
      return okResponse({});
    });

    await tester.pumpWidget(_themed(const DishListPage()));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    // 菜品卡片
    expect(find.text('番茄炒蛋'), findsOneWidget);
    expect(find.text('红烧肉'), findsOneWidget);
    // 做过次数：番茄炒蛋做过6次，红烧肉没做过
    expect(find.textContaining('做过 6 次'), findsOneWidget);
    expect(find.textContaining('没做过'), findsOneWidget);
    // 排序行结果计数
    expect(find.text('2 道'), findsOneWidget);
  });

  testWidgets('空数据 → 渲染空态"暂无菜品"', (tester) async {
    installMock((options) {
      if (options.path == '/dish/search') {
        return okResponse({'records': [], 'total': 0});
      }
      return okResponse({});
    });

    await tester.pumpWidget(_themed(const DishListPage()));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    expect(find.text('暂无菜品'), findsOneWidget);
  });

  testWidgets('搜索框输入 → 回车触发搜索（带 keyword）', (tester) async {
    String? capturedKeyword;
    installMock((options) {
      if (options.path == '/dish/search') {
        capturedKeyword = options.queryParameters['keyword'];
        return okResponse({
          'records': [
            {'id': 1, 'name': '番茄炒蛋', 'cookTime': 10},
          ],
          'total': 1,
        });
      }
      return okResponse({});
    });

    await tester.pumpWidget(_themed(const DishListPage()));
    await tester.pump(const Duration(milliseconds: 50));

    // 初始加载不带 keyword
    expect(capturedKeyword, isNull);

    // 输入"番茄"并回车
    await tester.enterText(find.byType(TextField), '番茄');
    await tester.pump();
    await tester.testTextInput.receiveAction(TextInputAction.done);
    await tester.pump(const Duration(milliseconds: 50));

    // 搜索请求带上了 keyword
    expect(capturedKeyword, '番茄');
  });
}
