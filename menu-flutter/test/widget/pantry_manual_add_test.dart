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

/// 入库页 widget 测试（对齐 44829 批次 pantry-manual-add-v2 定稿）：
/// ① 选食材（无标题；未输入只显示提示卡 + 新建入口，输入后才显示库里已有，精确同名隐藏新建区）
/// → ② 定档位（补充后，家里有多少？充足默认/不足=一点点）+ 来源 → 入库。
void main() {
  Map<String, dynamic> ingredientList() => {
        'records': [
          {'id': 1, 'name': '苹果', 'unitId': 3},
          {'id': 2, 'name': '大米', 'unitId': 4},
        ],
        'total': 2,
      };

  Map<String, dynamic> grouped() => {
        'summary': {'enough': 1, 'low': 0, 'none': 1},
        'items': [
          {'ingredientId': 1, 'ingredientName': '苹果', 'level': 'NONE', 'lastChange': null},
          {'ingredientId': 2, 'ingredientName': '大米', 'level': 'ENOUGH', 'lastChange': null},
        ],
      };

  Future<RequestCaptor> pumpPage(WidgetTester tester) async {
    final captor = installMock((options) {
      if (options.path == '/ingredient') return okResponse(ingredientList());
      if (options.path == '/pantry/grouped') return okResponse(grouped());
      if (options.path == '/pantry/manual') return okResponse(null);
      return okResponse({});
    });
    await tester.pumpWidget(_themed(const PantryManualAddPage()));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    return captor;
  }

  testWidgets('① 未输入：无标题 + 头说明 + 提示卡 + 新建入口；输入后显示库里已有，精确同名隐藏新建区', (tester) async {
    await pumpPage(tester);

    // 无标题（DESIGN.md §13.1 录入页只有返回箭头）
    expect(find.text('入库'), findsNothing);
    // 头说明（功能说明文案保留）
    expect(find.text('朋友送 / 赠品 / 之前忘记登的旧库存，记一笔进来'), findsOneWidget);

    // 未输入：提示卡 + 新建入口常驻，不显示库里已有
    expect(find.text('输入名称，会显示库里已有的食材'), findsOneWidget);
    expect(find.text('库存里没有？'), findsOneWidget);
    expect(find.text('+ 新建食材并入库'), findsOneWidget);
    expect(find.text('苹果'), findsNothing);
    expect(find.text('大米'), findsNothing);

    // 下一步：未选时禁用
    final nextBtn = tester.widget<ElevatedButton>(find.byType(ElevatedButton));
    expect(nextBtn.onPressed, isNull);

    // 输入「苹果」：匹配行 + 家里档位 + 精确同名隐藏新建区（.last = 行文本，输入框内容同名）
    await tester.enterText(find.byType(TextField), '苹果');
    await tester.pump();
    expect(find.text('库里已有'), findsOneWidget);
    expect(find.text('苹果').last, findsOneWidget);
    expect(find.text('家里：用完'), findsOneWidget);
    expect(find.text('选'), findsOneWidget);
    expect(find.text('库存里没有？'), findsNothing);

    // 输入「橙子」：无匹配 → 只有新建区，副文案带名称
    await tester.enterText(find.byType(TextField), '橙子');
    await tester.pump();
    expect(find.text('库里已有'), findsNothing);
    expect(find.text('库存里没有？'), findsOneWidget);
    expect(find.text('「橙子」建档同时入库'), findsOneWidget);
  });

  testWidgets('选中食材 → 下一步 → ② 定档位/来源（新文案）→ 入库带 level+sourceNote', (tester) async {
    final captor = await pumpPage(tester);

    // 输入后选中苹果（已选状态；.last = 行文本）
    await tester.enterText(find.byType(TextField), '苹果');
    await tester.pump();
    await tester.tap(find.text('苹果').last);
    await tester.pump();
    expect(find.text('已选'), findsOneWidget);

    // 下一步 → 步骤2
    await tester.tap(find.byType(ElevatedButton));
    await tester.pump();

    // 食材头：缩略图 + 家里状态
    expect(find.byType(InitialAvatar), findsOneWidget);
    expect(find.text('家里：用完 · 入库记一笔'), findsOneWidget);

    // 档位：新文案 + 充足默认 / 不足=一点点
    expect(find.text('补充后，家里有多少？'), findsOneWidget);
    expect(find.text('充足'), findsOneWidget);
    expect(find.text('不足'), findsOneWidget);
    expect(find.text('一点点'), findsOneWidget);
    expect(find.text('只买了一点'), findsNothing);
    // 已删：右侧小字 + 说明条
    expect(find.text('会显示在这条记录上'), findsNothing);
    expect(find.textContaining('库存是档位，不是账本'), findsNothing);

    // 来源备注默认 朋友送
    expect(find.text('朋友送'), findsOneWidget);

    // 选档位 不足 → 入库
    await tester.tap(find.text('不足'));
    await tester.pump();
    await tester.tap(find.text('入库'));
    await tester.pumpAndSettle();

    final req = captor.last!;
    expect(req.path, '/pantry/manual');
    final body = req.data as Map<String, dynamic>;
    expect(body['ingredientId'], 1);
    expect(body['level'], 'LOW');
    expect(body['sourceNote'], '朋友送');
  });

  testWidgets('新建档：搜不到 → 点新建入口 → 按名入库（默认充足）', (tester) async {
    final captor = await pumpPage(tester);

    // 输入查询（库里没有）
    await tester.enterText(find.byType(TextField), '橙子');
    await tester.pump();
    expect(find.text('「橙子」建档同时入库'), findsOneWidget);

    // 点新建档入口 → 下一步 → 步骤2
    await tester.tap(find.text('+ 新建食材并入库'));
    await tester.pump();
    await tester.tap(find.byType(ElevatedButton));
    await tester.pump();
    expect(find.text('新建档 · 家里还没有'), findsOneWidget);

    // 入库（默认充足）
    await tester.tap(find.text('入库'));
    await tester.pumpAndSettle();

    final body = captor.last!.data as Map<String, dynamic>;
    expect(captor.last!.path, '/pantry/manual');
    expect(body['name'], '橙子');
    expect(body.containsKey('ingredientId'), isFalse);
    expect(body['level'], 'ENOUGH');
  });
}
