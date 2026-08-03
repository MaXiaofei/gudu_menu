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

/// 菜库浏览端到端（widget 级）：
/// DishListPage.initState → _reload → DishService.search → ApiClient → mock →
/// 解析分页 → 渲染菜品卡片。
///
/// 注意：首屏 LoadingView 含 CircularProgressIndicator，mock 让 _reload 瞬时完成、
/// 移除 spinner 后即可 settle。
void main() {
  testWidgets('菜库列表渲染 mock 菜品', (tester) async {
    installMock((options) {
      if (options.path == '/dish/search') {
        return okResponse({
          'records': [
            {
              'id': 1,
              'name': '番茄炒蛋',
              'cookTime': 10,
              'difficulty': 1,
              'cuisineNames': ['鲁菜'],
              'categoryNames': ['热菜'],
              'tagNames': ['家常'],
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
    // 让 _reload 的异步链路完成（mock 瞬时返回）→ setState 移除 spinner
    await tester.pump(const Duration(milliseconds: 50));

    expect(find.text('菜库'), findsOneWidget); // AppBar
    expect(find.text('番茄炒蛋'), findsOneWidget);
    expect(find.text('红烧肉'), findsOneWidget);
    // Bug B 修复后 Dish model 解析字典名，列表副标题展示菜系
    expect(find.textContaining('鲁菜'), findsOneWidget);
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
}
