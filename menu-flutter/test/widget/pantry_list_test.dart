import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:menu_flutter/core/app_theme.dart';
import 'package:menu_flutter/pages/pantry/list_page.dart';
import 'package:menu_flutter/widgets/initial_avatar.dart';
import '../helpers/mock_http.dart';

/// 给 widget 测试注入 AppTokens 主题。
Widget _themed(Widget child) => MaterialApp(
      theme: ThemeData(extensions: const [AppTokens.cream]),
      home: child,
    );

/// 库存主页 widget 测试（对齐 pantry-page.html 原型）：
/// 筛选条（全部/缺/低/够）替代三色汇总条 + 点选筛档 + 行缩略图 + 右上角按钮。
void main() {
  // 三档各 2/1/2 条，覆盖 缺/低/够
  Map<String, dynamic> grouped() => {
        'summary': {'enough': 2, 'low': 1, 'none': 2},
        'items': [
          {
            'ingredientId': 1,
            'ingredientName': '鸡蛋',            'level': 'NONE',
            'lastChange': {
              'source': 'cook',              'sourceNote': null,
              'createTime': '2026-08-07T19:00:00',
            },
          },
          {
            'ingredientId': 2,
            'ingredientName': '鲈鱼',            'level': 'NONE',
            'lastChange': null,
          },
          {
            'ingredientId': 3,
            'ingredientName': '牛奶',            'level': 'LOW',
            'lastChange': null,
          },
          {
            'ingredientId': 4,
            'ingredientName': '大米',            'level': 'ENOUGH',
            'lastChange': {
              'source': 'purchase',              'sourceNote': null,
              'createTime': '2026-07-02T10:00:00',
            },
          },
          {
            'ingredientId': 5,
            'ingredientName': '苹果',            'level': 'ENOUGH',
            'lastChange': {
              'source': 'manual',              'sourceNote': '朋友送',
              'createTime': '2026-07-02T11:00:00',
            },
          },
        ],
      };

  Future<void> pumpList(WidgetTester tester) async {
    installMock((options) {
      if (options.path == '/pantry/grouped') return okResponse(grouped());
      return okResponse({});
    });
    await tester.pumpWidget(_themed(const PantryListPage()));
    await tester.pump(); // 加载中 → 数据
    await tester.pump(const Duration(milliseconds: 50));
  }

  testWidgets('筛选条替代汇总条 + 缩略图 + 右上角按钮（无 FAB）', (tester) async {
    await pumpList(tester);

    // 筛选条带计数：全部/用完/不足/充足
    expect(find.text('全部 5'), findsOneWidget);
    expect(find.text('用完 2'), findsOneWidget);
    expect(find.text('不足 1'), findsOneWidget);
    expect(find.text('充足 2'), findsOneWidget);

    // 分组标题 + 全部行
    expect(find.text('用完 · 2'), findsOneWidget);
    expect(find.text('不足 · 1'), findsOneWidget);
    expect(find.text('充足 · 2'), findsOneWidget);
    expect(find.text('鸡蛋'), findsOneWidget);
    expect(find.text('鲈鱼'), findsOneWidget);
    expect(find.text('牛奶'), findsOneWidget);
    expect(find.text('大米'), findsOneWidget);
    expect(find.text('苹果'), findsOneWidget);

    // 行前置缩略图（首字占位）
    expect(find.byType(InitialAvatar), findsNWidgets(5));

    // 右上角「入库」「去采购」，无右下 FAB
    expect(find.text('入库'), findsOneWidget);
    expect(find.text('去采购'), findsOneWidget);
    expect(find.byType(FloatingActionButton), findsNothing);

    // 手动来源标签
    expect(find.textContaining('手动'), findsOneWidget);
  });

  testWidgets('点「缺」只显示缺组；点「够」只显示够组；回「全部」恢复', (tester) async {
    await pumpList(tester);

    // 用完：只留 鸡蛋/鲈鱼
    await tester.tap(find.text('用完 2'));
    await tester.pump();
    expect(find.text('用完 · 2'), findsOneWidget);
    expect(find.text('鸡蛋'), findsOneWidget);
    expect(find.text('鲈鱼'), findsOneWidget);
    expect(find.text('牛奶'), findsNothing);
    expect(find.text('大米'), findsNothing);

    // 充足：只留 大米/苹果
    await tester.tap(find.text('充足 2'));
    await tester.pump();
    expect(find.text('充足 · 2'), findsOneWidget);
    expect(find.text('大米'), findsOneWidget);
    expect(find.text('苹果'), findsOneWidget);
    expect(find.text('鸡蛋'), findsNothing);
    expect(find.text('牛奶'), findsNothing);

    // 全部：恢复
    await tester.tap(find.text('全部 5'));
    await tester.pump();
    expect(find.text('鸡蛋'), findsOneWidget);
    expect(find.text('牛奶'), findsOneWidget);
    expect(find.text('苹果'), findsOneWidget);
  });
}
