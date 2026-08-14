import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';

import 'package:menu_flutter/core/app_theme.dart';
import 'package:menu_flutter/pages/member/member_list_page.dart';
import 'package:menu_flutter/stores/member_store.dart';

import '../helpers/mock_http.dart';

/// 家庭成员列表页 widget 测试（最小闭环）：
/// 成员展示（姓名/副信息/当前标记）→ 点行切换当前就餐成员 → 空态。
dynamic _responder(RequestOptions options) {
  final path = options.path;
  if (path == '/member' && options.method == 'GET') {
    return okResponse({
      'records': [
        {
          'id': 1, 'name': '爸爸', 'roleTags': '掌勺',
          'healthProfile': {'audiences': ['高血压']},
        },
        {
          'id': 2, 'name': '小明', 'roleTags': '备菜,普通成员',
          'healthProfile': {'audiences': []},
        },
      ],
      'total': 2,
    });
  }
  if (path == '/member/current' && options.method == 'GET') {
    return okResponse(1);
  }
  if (path == '/member/current' && options.method == 'POST') {
    return okResponse(null);
  }
  return okResponse(null);
}

void main() {
  late RequestCaptor captor;

  setUp(() {
    captor = installMock(_responder);
  });

  Future<MemberStore> pumpPage(WidgetTester tester) async {
    final store = MemberStore();
    await tester.pumpWidget(MultiProvider(
      providers: [ChangeNotifierProvider.value(value: store)],
      child: MaterialApp(
        theme: ThemeData(extensions: const [AppTokens.cream]),
        home: const MemberListPage(),
      ),
    ));
    await tester.pumpAndSettle();
    return store;
  }

  testWidgets('成员列表：姓名 + 特殊人群/角色副信息 + 当前标记', (tester) async {
    final store = await pumpPage(tester);

    expect(find.text('爸爸'), findsOneWidget);
    expect(find.text('小明'), findsOneWidget);
    // 副信息：特殊人群在前，角色在后（爸爸）+ 纯角色（小明）
    expect(find.text('高血压 · 掌勺'), findsOneWidget);
    expect(find.text('备菜 · 普通成员'), findsOneWidget);
    // 当前标记只在当前成员行
    expect(find.text('当前'), findsOneWidget);
    expect(find.text('切换'), findsOneWidget);
    expect(store.currentId, 1);
  });

  testWidgets('点成员行 → POST /member/current 切换 + 标记移动', (tester) async {
    final store = await pumpPage(tester);

    await tester.tap(find.text('小明'));
    await tester.pumpAndSettle();

    // 切换请求带 memberId
    final post = captor.all
        .where((o) => o.method == 'POST' && o.path == '/member/current')
        .last;
    expect(post.queryParameters['memberId'], 2);
    // store 状态 + 标记移动
    expect(store.currentId, 2);
    expect(find.text('当前'), findsOneWidget);
    expect(find.text('已切换为 小明'), findsOneWidget);
  });

  testWidgets('空态：暂无成员提示', (tester) async {
    captor = installMock((options) {
      if (options.path == '/member' && options.method == 'GET') {
        return okResponse({'records': [], 'total': 0});
      }
      if (options.path == '/member/current' && options.method == 'GET') {
        return okResponse(0);
      }
      return okResponse(null);
    });
    await pumpPage(tester);

    expect(find.text('暂无成员，请先在后台添加'), findsOneWidget);
  });
}
