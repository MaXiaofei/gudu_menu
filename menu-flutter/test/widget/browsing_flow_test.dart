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

    expect(find.text('菜谱'), findsOneWidget); // AppBar（原型改为"菜谱"）
    expect(find.text('番茄炒蛋'), findsOneWidget);
    expect(find.text('红烧肉'), findsOneWidget);
    // 列表项副标题显示做过次数（cookedCount 缺省 0 → "没做过"）
    expect(find.textContaining('没做过'), findsWidgets);
  });

  testWidgets('按食材模式：切模式 → 输入联想 → 选中食材 → 按食材搜菜', (tester) async {
    Map<String, dynamic>? lastDishQuery;
    installMock((options) {
      // 食材联想：输"番"返回番茄
      if (options.path == '/ingredient') {
        return okResponse({
          'records': [
            {'id': 1, 'name': '番茄'},
            {'id': 2, 'name': '番茄酱'},
          ],
        });
      }
      // 菜品搜索：记录请求，返回含番茄的菜
      if (options.path == '/dish/search') {
        lastDishQuery = options.queryParameters;
        return okResponse({
          'records': [
            {'id': 10, 'name': '番茄炒蛋'},
          ],
          'total': 1,
        });
      }
      return okResponse({});
    });

    await tester.pumpWidget(_themed(const DishListPage()));
    await tester.pump(const Duration(milliseconds: 50));

    // 初始：按菜名模式，搜索框 hint 是"搜菜名"
    expect(find.text('搜菜名'), findsOneWidget);

    // 切换到按食材模式：点模式下拉触发器（"菜名"文字）→ 选"按食材"
    await tester.tap(find.text('菜名'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('按食材').last);
    await tester.pumpAndSettle();

    // 模式切换后清空请求计数（reload 会发一次空搜索）
    lastDishQuery = null;

    // hint 变成"输食材名，如番茄"
    expect(find.text('输食材名，如番茄'), findsOneWidget);

    // 输入"番"触发联想
    await tester.enterText(find.byType(TextField), '番');
    await tester.pump(const Duration(milliseconds: 50));

    // 联想下拉出现"番茄"
    expect(find.text('番茄'), findsOneWidget);

    // 选中"番茄"
    await tester.tap(find.text('番茄'));
    await tester.pumpAndSettle();

    // 选中后：已选食材标签显示"番茄"，且菜品搜索请求带 ingredientIds=1
    expect(find.widgetWithText(Chip, '番茄'), findsOneWidget);
    expect(lastDishQuery?['ingredientIds'], '1');
    // 搜索结果渲染
    expect(find.text('番茄炒蛋'), findsOneWidget);
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
