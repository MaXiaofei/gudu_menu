import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import 'package:menu_flutter/core/app_theme.dart';
import 'package:menu_flutter/pages/ingredient/create_page.dart';
import 'package:menu_flutter/pages/ingredient/edit_page.dart';
import 'package:menu_flutter/pages/ingredient/list_page.dart';
import 'package:menu_flutter/services/ingredient_service.dart';

import '../helpers/mock_http.dart';

/// 食材库列表页 widget 测试（V55 去单位后）：
/// 品类标签/非营养非食用副标题 + 分类筛选参数 + 空态 +「+ 添加」/ 行点击跳转。
/// 原徽标三态（已设换算/换算待补/去补）与「默认 单位 · ¥价/单位」断言随单位解绑删除。
dynamic _responder(RequestOptions options) {
  final path = options.path;
  if (path == '/dict') {
    return okResponse({
      'records': [
        {'id': 24, 'name': '蔬菜'},
        {'id': 25, 'name': '蛋类'},
      ],
      'total': 2,
    });
  }
  if (path == '/ingredient' && options.method == 'GET') {
    return okResponse({
      'records': [
        {'id': 1, 'name': '鸡蛋', 'categoryName': '蛋类', 'edible': 1},
        {'id': 2, 'name': '鲈鱼', 'categoryName': '水产海鲜', 'edible': 1},
        {'id': 3, 'name': '菠菜', 'categoryName': '蔬菜', 'edible': 1},
        {'id': 4, 'name': '可乐', 'categoryName': '饮料零食', 'edible': 2},
        {'id': 5, 'name': '抽纸', 'categoryName': '生活用品', 'edible': 3},
      ],
      'total': 5,
    });
  }
  // 行点击 → 编辑页加载
  if (path == '/ingredient/1' && options.method == 'GET') {
    return okResponse({
      'id': 1, 'name': '鸡蛋', 'categoryName': '蛋类', 'edible': 1,
    });
  }
  if (path == '/nutrition/metric') {
    return okResponse([]);
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
        initialLocation: '/ingredient',
        routes: [
          GoRoute(
              path: '/ingredient', builder: (_, __) => const IngredientListPage()),
          GoRoute(
              path: '/create-ingredient',
              builder: (_, __) => const CreateIngredientPage()),
          GoRoute(
              path: '/ingredient/:id/edit',
              builder: (_, s) => IngredientEditPage(
                  ingredientId: int.parse(s.pathParameters['id']!))),
        ],
      );

  Future<void> pumpList(WidgetTester tester) async {
    await tester.pumpWidget(MaterialApp.router(
      routerConfig: router(),
      theme: ThemeData(extensions: const [AppTokens.cream]),
    ));
    await tester.pumpAndSettle();
  }

  testWidgets('品类标签 + 非营养/非食用副标题 + 分类chips + 底部注', (tester) async {
    await pumpList(tester);

    // 品类标签（蛋类/蔬菜 同时出现在分类 chips 里，故各 2 个；其余只出现在卡片）
    expect(find.text('蛋类'), findsNWidgets(2));
    expect(find.text('水产海鲜'), findsOneWidget);
    expect(find.text('蔬菜'), findsNWidgets(2));
    // V55 副标题：非营养/非食用标记，普通食材「点击编辑」
    expect(find.text('非营养'), findsOneWidget);
    expect(find.text('非食用'), findsOneWidget);
    expect(find.text('点击编辑'), findsNWidgets(3));
    // 分类 chips（全部带总数）+ 底部注
    expect(find.text('全部 5'), findsOneWidget);
    expect(find.text('食材从「我的」进入；点击食材可编辑食用属性'), findsOneWidget);
    // V55：不再有换算徽标与「默认 单位 · ¥价/单位」
    expect(find.text('已设换算'), findsNothing);
    expect(find.text('默认 个 · ¥1/个'), findsNothing);
  });

  testWidgets('点分类 chip 重新请求带 purchaseCategoryId', (tester) async {
    await pumpList(tester);

    // 「蔬菜」chips 在卡片上方，取 first
    await tester.tap(find.text('蔬菜').first);
    await tester.pumpAndSettle();

    final listReq = captor.all
        .where((o) => o.path == '/ingredient' && o.method == 'GET')
        .last;
    expect(listReq.queryParameters['purchaseCategoryId'], 24);
  });

  testWidgets('空态走 EmptyView', (tester) async {
    // 覆写 responder：空列表
    captor = installMock((options) {
      if (options.path == '/dict') {
        return okResponse({'records': [], 'total': 0});
      }
      if (options.path == '/ingredient' && options.method == 'GET') {
        return okResponse({'records': [], 'total': 0});
      }
      return okResponse(null);
    });
    await pumpList(tester);

    expect(find.text('暂无食材'), findsOneWidget);
    // 全部总数随响应为 0
    expect(find.text('全部 0'), findsOneWidget);
  });

  testWidgets('+ 添加 跳转新建页', (tester) async {
    await pumpList(tester);

    await tester.tap(find.text('+ 添加'));
    await tester.pumpAndSettle();

    expect(find.text('录入食材'), findsOneWidget);
  });

  testWidgets('点行进编辑页（名称/品类/食用属性）', (tester) async {
    await pumpList(tester);

    await tester.tap(find.text('鸡蛋'));
    await tester.pumpAndSettle();

    // 编辑页 BackHeader 标题 + 品类副信息 + 食用属性卡
    expect(find.text('鸡蛋'), findsOneWidget);
    expect(find.text('蛋类'), findsOneWidget);
    expect(find.text('食用属性'), findsOneWidget);
    // V55：编辑页不再有默认单位/换算/单价
    expect(find.text('默认单位'), findsNothing);
  });
}
