import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:menu_flutter/core/app_theme.dart';
import 'package:menu_flutter/pages/pantry/manual_add_page.dart';
import 'package:menu_flutter/widgets/initial_avatar.dart';
import '../helpers/mock_http.dart';

/// 给 widget 测试注入 AppTokens 主题。
Widget _themed(Widget child) => MaterialApp(
      theme: ThemeData(extensions: const [AppTokens.cream]),
      home: child,
    );

/// 手动添加页 widget 测试（对齐 pantry-manual-add.html 两屏）：
/// ① 选食材（库里已有带现有量 / 新建档）→ ② 填数量 + 批次属性 + 来源 → 入库。
void main() {
  Map<String, dynamic> ingredientList() => {
        'records': [
          {
            'id': 1,
            'name': '苹果',
            'unitId': 3,
            'stockAmount': 0,
            'stockUnitName': '个',
          },
          {
            'id': 2,
            'name': '大米',
            'unitId': 4,
            'stockAmount': 2,
            'stockUnitName': 'kg',
          },
        ],
        'total': 2,
      };

  Future<RequestCaptor> pumpPage(WidgetTester tester) async {
    final captor = installMock((options) {
      if (options.path == '/ingredient') return okResponse(ingredientList());
      if (options.path == '/pantry/manual') return okResponse(null);
      return okResponse({});
    });
    await tester.pumpWidget(_themed(const PantryManualAddPage()));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    return captor;
  }

  testWidgets('① 选食材：库里已有带现有量 + 新建档入口', (tester) async {
    await pumpPage(tester);

    // 头说明
    expect(find.text('选食材'), findsOneWidget);
    expect(find.text('朋友送 / 赠品 / 之前忘记登的旧库存，记一笔进来'), findsOneWidget);

    // 库里已有行：名称 + 现有量/单位 + 选
    expect(find.text('苹果'), findsOneWidget);
    expect(find.text('现有 0 个 · 个'), findsOneWidget);
    expect(find.text('大米'), findsOneWidget);
    expect(find.text('现有 2 kg · kg'), findsOneWidget);
    expect(find.text('选'), findsNWidgets(2));

    // 新建档入口
    expect(find.text('+ 新建食材并添加'), findsOneWidget);

    // 下一步：未选时禁用
    final nextBtn = tester.widget<ElevatedButton>(find.byType(ElevatedButton));
    expect(nextBtn.onPressed, isNull);
  });

  testWidgets('选中食材 → 下一步 → ② 填数量/批次/来源 → 入库带 storage+expireDate', (tester) async {
    final captor = await pumpPage(tester);

    // 选中苹果（已选状态）
    await tester.tap(find.text('苹果'));
    await tester.pump();
    expect(find.text('已选'), findsOneWidget);

    // 下一步 → 步骤2
    await tester.tap(find.byType(ElevatedButton));
    await tester.pump();

    // 食材头：缩略图 + 现有量
    expect(find.byType(InitialAvatar), findsOneWidget);
    expect(find.text('现有 0 个 · 手动新增一笔'), findsOneWidget);

    // 批次属性：日期=今天 / 存放=未设 / 保质期=未设
    expect(find.text('日期'), findsOneWidget);
    expect(find.textContaining('今天 '), findsOneWidget);
    expect(find.text('存放'), findsOneWidget);
    expect(find.text('未设'), findsNWidgets(2));
    expect(find.text('保质期'), findsOneWidget);

    // 选存放：冷藏
    await tester.tap(find.text('未设').first);
    await tester.pumpAndSettle();
    await tester.tap(find.text('冷藏'));
    await tester.pumpAndSettle();
    expect(find.text('冷藏'), findsOneWidget);

    // 选保质期：7 天
    await tester.tap(find.text('保质期'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('7 天'));
    await tester.pumpAndSettle();
    expect(find.text('7 天'), findsOneWidget);

    // 来源备注默认 朋友送；数量 +1 → 入库 · 2 个
    expect(find.text('朋友送'), findsOneWidget);
    await tester.tap(find.byIcon(Icons.add));
    await tester.pump();
    expect(find.text('入库 · 2 个'), findsOneWidget);

    // 入库 → POST /pantry/manual
    await tester.tap(find.text('入库 · 2 个'));
    await tester.pumpAndSettle();

    final req = captor.last!;
    expect(req.path, '/pantry/manual');
    final body = req.data as Map<String, dynamic>;
    expect(body['ingredientId'], 1);
    expect(body['amount'], 2.0);
    expect(body['sourceNote'], '朋友送');
    expect(body['storage'], '冷藏');
    // 保质期 7 天 → expireDate = 今天 + 7
    final today = DateTime.now();
    final expectDate = DateTime(today.year, today.month, today.day)
        .add(const Duration(days: 7));
    final fmt = '${expectDate.year}-${expectDate.month.toString().padLeft(2, '0')}-'
        '${expectDate.day.toString().padLeft(2, '0')}';
    expect(body['expireDate'], fmt);
  });

  testWidgets('新建档：搜不到 → 点新建入口 → 按名入库', (tester) async {
    final captor = await pumpPage(tester);

    // 输入查询（库里没有）
    await tester.enterText(find.byType(TextField), '橙子');
    await tester.pump();
    expect(find.text('「橙子」建档同时入库'), findsOneWidget);

    // 点新建档入口 → 下一步 → 步骤2
    await tester.tap(find.text('+ 新建食材并添加'));
    await tester.pump();
    await tester.tap(find.byType(ElevatedButton));
    await tester.pump();
    expect(find.text('现有 0 · 手动新增一笔'), findsOneWidget);

    // 入库
    await tester.tap(find.text('入库 · 1'));
    await tester.pumpAndSettle();

    final body = captor.last!.data as Map<String, dynamic>;
    expect(captor.last!.path, '/pantry/manual');
    expect(body['name'], '橙子');
    expect(body.containsKey('ingredientId'), isFalse);
    expect(body['storage'], isNull);
  });
}
