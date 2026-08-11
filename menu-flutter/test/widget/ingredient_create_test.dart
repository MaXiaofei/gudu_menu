import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:menu_flutter/core/app_theme.dart';
import 'package:menu_flutter/pages/ingredient/create_page.dart';
import 'package:menu_flutter/services/ingredient_service.dart';

import '../helpers/mock_http.dart';

/// 食材录入页冒烟测试：
/// 渲染不崩（含名称行 AI 按钮——Row 非弹性子项必须限宽，回归 2026-08-11 无限宽约束崩溃）。
dynamic _responder(RequestOptions options) {
  final path = options.path;
  if (path == '/dict/purchase_category') {
    return okResponse([
      {'id': 24, 'name': '蔬菜'},
      {'id': 25, 'name': '畜禽肉'},
    ]);
  }
  if (path == '/nutrition/metric') {
    return okResponse([
      {'id': 1, 'name': '热量', 'unit': 'kcal'},
    ]);
  }
  return okResponse(null);
}

Widget _themed(Widget child) => MaterialApp(
      theme: ThemeData(extensions: const [AppTokens.cream]),
      home: child,
    );

void main() {
  setUp(() {
    IngredientService.clearDictCache();
    installMock(_responder);
  });

  testWidgets('录入页渲染（名称行 + AI 按钮不崩）', (tester) async {
    await tester.pumpWidget(_themed(const CreateIngredientPage()));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    expect(find.text('录入食材'), findsOneWidget);
    expect(find.text('食材名'), findsOneWidget);
    expect(find.text('AI\n补全'), findsOneWidget);
    expect(find.text('保存'), findsOneWidget);
  });

  testWidgets('输入名称 → 保存 → POST /ingredient 带分类', (tester) async {
    final captor = RequestCaptor();
    installMock((options) {
      captor.last = options;
      captor.all.add(options);
      return _responder(options);
    });

    await tester.pumpWidget(_themed(const CreateIngredientPage()));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    await tester.enterText(
        find.byType(TextField).first, '秋葵');
    await tester.tap(find.text('保存'));
    await tester.pumpAndSettle();

    final post = captor.all
        .where((o) => o.method == 'POST' && o.path == '/ingredient')
        .last;
    expect(post.data['ingredient']['name'], '秋葵');
  });
}
