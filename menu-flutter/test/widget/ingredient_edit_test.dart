import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import 'package:menu_flutter/core/app_theme.dart';
import 'package:menu_flutter/pages/ingredient/edit_page.dart';
import 'package:menu_flutter/services/ingredient_service.dart';

import '../helpers/mock_http.dart';

/// 食材编辑页 widget 测试（V55 去单位后）：
/// 加载展示（名称/品类/食用属性）→ 改食用属性 / 删除，保存请求体断言。
/// 原默认单位「改」/ 换算表 / 单价卡片测试随单位解绑一并删除。
dynamic _responder(RequestOptions options) {
  final path = options.path;
  if (path == '/ingredient/1' && options.method == 'GET') {
    return okResponse({
      'id': 1, 'name': '鸡蛋', 'categoryName': '蛋类', 'edible': 1,
    });
  }
  return okResponse(null);
}

void main() {
  late RequestCaptor captor;

  setUp(() {
    IngredientService.clearDictCache();
    captor = installMock(_responder);
  });

  GoRouter router() => GoRouter(
        initialLocation: '/ingredient/1/edit',
        routes: [
          GoRoute(
              path: '/ingredient',
              builder: (_, __) => const Scaffold(body: SizedBox())),
          GoRoute(
              path: '/ingredient/:id/edit',
              builder: (_, s) => IngredientEditPage(
                  ingredientId: int.parse(s.pathParameters['id']!))),
        ],
      );

  Future<void> pumpEdit(WidgetTester tester) async {
    await tester.pumpWidget(MaterialApp.router(
      routerConfig: router(),
      theme: ThemeData(extensions: const [AppTokens.cream]),
    ));
    await tester.pumpAndSettle();
  }

  testWidgets('加载展示：名称/品类/食用属性', (tester) async {
    await pumpEdit(tester);

    // BackHeader 标题 = 真实名；副信息 = 品类
    expect(find.text('鸡蛋'), findsOneWidget);
    expect(find.text('蛋类'), findsOneWidget);
    // 食用属性卡片
    expect(find.text('食用属性'), findsOneWidget);
    expect(find.text('食用'), findsOneWidget);
    // 底部保存
    expect(find.text('保存'), findsOneWidget);
    // V55 去单位：不再有默认单位/换算/单价 UI
    expect(find.text('默认单位'), findsNothing);
    expect(find.text('单价'), findsNothing);
  });

  testWidgets('改食用属性 → 保存请求体（只含 id/edible）', (tester) async {
    await pumpEdit(tester);

    await tester.tap(find.text('食用'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('生活用品'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('保存'));
    await tester.pumpAndSettle();

    final put = captor.all
        .where((o) => o.method == 'PUT' && o.path == '/ingredient')
        .last;
    final body = put.data as Map<String, dynamic>;
    expect(body['id'], 1);
    expect(body['edible'], 3);
  });

  testWidgets('✕ 删除：确认弹窗 → DELETE 请求', (tester) async {
    await pumpEdit(tester);

    await tester.tap(find.text('✕'));
    await tester.pumpAndSettle();
    expect(find.text('删除食材'), findsOneWidget);

    await tester.tap(find.text('删除'));
    await tester.pumpAndSettle();

    final del = captor.all
        .where((o) => o.method == 'DELETE' && o.path == '/ingredient/1')
        .last;
    expect(del, isNotNull);
  });
}
