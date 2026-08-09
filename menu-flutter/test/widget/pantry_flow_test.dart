import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:menu_flutter/app.dart';
import 'package:menu_flutter/core/theme_controller.dart';
import 'package:menu_flutter/pages/pantry/detail_page.dart';
import 'package:menu_flutter/pages/pantry/list_page.dart';
import 'package:menu_flutter/pages/pantry/manual_add_page.dart';
import 'package:menu_flutter/stores/auth_store.dart';
import '../helpers/mock_http.dart';

/// 库存流程回归（真实路由 + 真实主题）：
/// 登录 → 库存 tab → 点行进详情（防 app_theme 全宽按钮在 Row 里的 infinite-width 崩溃）
/// → 返回 → 添加 → 手动添加两屏（防 Center 里同款崩溃）。
void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  Future<void> pumpApp(WidgetTester tester) async {
    installMock((options) {
      if (options.path == '/auth/login') {
        return okResponse({'token': 'jwt-from-mock', 'nickname': 't'});
      }
      if (options.path == '/pantry/grouped') {
        return okResponse({
          'summary': {'enough': 1, 'low': 0, 'none': 1},
          'items': [
            {
              'ingredientId': 1,
              'ingredientName': '番茄',              'level': 'NONE',
              'lastChange': null,
            },
            {
              'ingredientId': 2,
              'ingredientName': '大米',              'level': 'ENOUGH',
              'lastChange': null,
            },
          ],
        });
      }
      if (options.path == '/pantry/item') {
        return okResponse({
          'ingredientId': 1,
          'ingredientName': '番茄',
          'unitId': 1,          'thresholdGrams': 0,
          'level': 'NONE',
          'changes': [],
        });
      }
      if (options.path == '/ingredient') {
        return okResponse({
          'records': [
            {'id': 1, 'name': '番茄', 'unitId': 1, 'stockAmount': 0, 'stockUnitName': '个'},
          ],
          'total': 1,
        });
      }
      return okResponse({});
    });

    final auth = AuthStore();
    await auth.init();
    await tester.pumpWidget(MenuApp(
      authStore: auth,
      themeController: ThemeController(),
      scaffoldKey: GlobalKey<ScaffoldMessengerState>(),
    ));
    await tester.pumpAndSettle();
    // 登录
    await tester.enterText(find.byType(TextField).at(1), '123456');
    await tester.tap(find.text('登录'));
    await tester.pumpAndSettle();
  }

  testWidgets('库存行点击 → 详情页渲染（真实路由，防 infinite-width 崩溃）', (tester) async {
    await pumpApp(tester);

    // 切库存 tab
    await tester.tap(find.text('库存').last);
    await tester.pumpAndSettle();
    expect(find.byType(PantryListPage), findsOneWidget);

    // 点某一行 → 详情页完整渲染（返回图标 + 3 档单选 + 保存按钮）
    await tester.tap(find.text('番茄'));
    await tester.pumpAndSettle();
    expect(find.byType(PantryDetailPage), findsOneWidget);
    expect(find.byType(BackButton), findsOneWidget);
    expect(find.text('现在家里是什么情况？'), findsOneWidget);
    expect(find.text('保存'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('添加 → 手动添加两屏按钮渲染（真实主题）', (tester) async {
    await pumpApp(tester);

    await tester.tap(find.text('库存').last);
    await tester.pumpAndSettle();

    // 右上角「入库」→ 选食材页（下一步按钮不崩）
    await tester.tap(find.text('入库'));
    await tester.pumpAndSettle();
    expect(find.byType(PantryManualAddPage), findsOneWidget);
    expect(find.text('下一步'), findsOneWidget);
    expect(tester.takeException(), isNull);

    // 选中食材 → 下一步 → 定档位+来源页（入库按钮不崩）
    await tester.tap(find.text('番茄'));
    await tester.pump();
    await tester.tap(find.byType(ElevatedButton).last);
    await tester.pumpAndSettle();
    expect(find.text('入库'), findsWidgets);
    expect(tester.takeException(), isNull);
  });
}
