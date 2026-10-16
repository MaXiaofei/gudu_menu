import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:menu_flutter/core/app_theme.dart';
import 'package:menu_flutter/pages/dish/create_page.dart';
import 'package:menu_flutter/services/ingredient_service.dart';
import '../helpers/mock_http.dart';

/// 写菜谱「加用料」弹层的常用食材来自 GET /ingredient/frequent（G11），不再取食材库前 8。
void main() {
  setUp(IngredientService.clearDictCache);

  testWidgets('常用 chips = /ingredient/frequent 返回顺序', (tester) async {
    tester.view.physicalSize = const Size(1080, 2400);
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.reset);
    final captor = installMock((o) {
      if (o.path == '/ingredient/frequent') {
        return okResponse([
          {'id': 11, 'name': '鸡蛋'},
          {'id': 12, 'name': '小葱'},
        ]);
      }
      if (o.path == '/dict') return okResponse({'records': [], 'total': 0});
      return okResponse({'records': [], 'total': 0});
    });
    await tester.pumpWidget(MaterialApp(
      theme: buildBrandTheme(AppTokens.cream),
      home: const CreateDishPage(),
    ));
    await tester.pumpAndSettle();

    final freq = captor.all.where((o) => o.path == '/ingredient/frequent').toList();
    expect(freq, hasLength(1));
    expect(freq.single.queryParameters['limit'], 8);
    // 不再为「常用」全量拉食材库
    expect(captor.all.where((o) => o.path == '/ingredient'), isEmpty);

    await tester.ensureVisible(find.text('加用料'));
    await tester.tap(find.text('加用料'));
    await tester.pumpAndSettle();
    expect(find.text('常用'), findsOneWidget);
    expect(find.text('鸡蛋'), findsOneWidget);
    expect(find.text('小葱'), findsOneWidget);
  });
}
