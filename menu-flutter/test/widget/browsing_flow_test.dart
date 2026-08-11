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

  testWidgets('左滑删除 → 确认弹窗 → 调 DELETE → 卡片移除', (tester) async {
    final captor = installMock((options) {
      if (options.path == '/dish/search') {
        return okResponse({
          'records': [
            {'id': 1, 'name': '番茄炒蛋', 'cookTime': 10, 'difficulty': 1},
          ],
          'total': 1,
        });
      }
      if (options.path == '/dish/1' && options.method == 'DELETE') {
        return okResponse(null);
      }
      return okResponse({});
    });

    await tester.pumpWidget(_themed(const DishListPage()));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    expect(find.text('番茄炒蛋'), findsOneWidget);

    // 左滑露出删除 → 松手触发 confirmDismiss → 弹确认框
    await tester.drag(find.text('番茄炒蛋'), const Offset(-400, 0));
    await tester.pumpAndSettle();
    expect(find.text('删除菜谱'), findsOneWidget);
    expect(find.textContaining('确定删除「番茄炒蛋」吗'), findsOneWidget);

    // 取消：不调接口，卡片还在
    await tester.tap(find.text('取消'));
    await tester.pumpAndSettle();
    expect(find.text('番茄炒蛋'), findsOneWidget);
    expect(captor.all.where((r) => r.method == 'DELETE'), isEmpty);

    // 再滑一次，确认删除
    await tester.drag(find.text('番茄炒蛋'), const Offset(-400, 0));
    await tester.pumpAndSettle();
    await tester.tap(find.text('删除'));
    await tester.pumpAndSettle();

    // DELETE /dish/1 已调用，卡片移除
    final deletes = captor.all.where((r) => r.method == 'DELETE').toList();
    expect(deletes, hasLength(1));
    expect(deletes.first.path, '/dish/1');
    expect(find.text('番茄炒蛋'), findsNothing);
    expect(find.text('已删除「番茄炒蛋」'), findsOneWidget);
  });
}
