import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import 'package:menu_flutter/core/app_theme.dart';
import 'package:menu_flutter/models/member.dart';
import 'package:menu_flutter/pages/member/member_list_page.dart';
import 'package:menu_flutter/pages/profile_page.dart';
import 'package:menu_flutter/stores/auth_store.dart';
import 'package:menu_flutter/stores/member_store.dart';

import '../helpers/mock_http.dart';

/// 我的页头部卡 = 当前就餐成员入口测试：
/// 成员已加载 → 显示「当前就餐：xxx」+ 点击进家庭成员列表；
/// 成员未拉到 → 兜底显示登录人昵称（登录即就餐成员，不再出现「未选择」）。
dynamic _responder(RequestOptions options) {
  final path = options.path;
  if (path == '/member' && options.method == 'GET') {
    return okResponse({
      'records': [
        {'id': 1, 'name': '爸爸', 'roleTags': '掌勺'},
        {'id': 2, 'name': '小明'},
      ],
      'total': 2,
    });
  }
  if (path == '/member/current' && options.method == 'GET') {
    return okResponse(1);
  }
  if (path == '/dish/draft/list') {
    return okResponse({'records': [], 'total': 0});
  }
  return okResponse(null);
}

void main() {
  setUp(() {
    installMock(_responder);
  });

  Future<void> pumpProfile(WidgetTester tester, MemberStore memberStore) async {
    final authStore = AuthStore()..nickname = '掌勺人';
    final router = GoRouter(
      initialLocation: '/profile',
      routes: [
        GoRoute(path: '/profile', builder: (_, __) => const ProfilePage()),
        GoRoute(path: '/members', builder: (_, __) => const MemberListPage()),
      ],
    );
    await tester.pumpWidget(MultiProvider(
      providers: [
        ChangeNotifierProvider.value(value: authStore),
        ChangeNotifierProvider.value(value: memberStore),
      ],
      child: MaterialApp.router(
        routerConfig: router,
        theme: ThemeData(extensions: const [AppTokens.cream]),
      ),
    ));
    await tester.pumpAndSettle();
  }

  testWidgets('成员已加载：头部卡显示当前就餐成员 + 点击进列表', (tester) async {
    // 模拟启动/登录后 load 完成的状态（直接预置，避免测试区假异步死锁）
    final memberStore = MemberStore()
      ..members = [
        const Member(id: 1, name: '爸爸', roleTags: '掌勺'),
        const Member(id: 2, name: '小明'),
      ]
      ..currentId = 1;
    await pumpProfile(tester, memberStore);

    expect(find.text('掌勺人'), findsOneWidget);
    expect(find.text('当前就餐：爸爸'), findsOneWidget);

    await tester.tap(find.text('当前就餐：爸爸'));
    await tester.pumpAndSettle();

    // 进入家庭成员列表页：成员行 + 当前标记（列表页 initState 会真实 load）
    expect(find.text('小明'), findsOneWidget);
    expect(find.text('当前'), findsOneWidget);
  });

  testWidgets('成员未拉到：兜底显示登录人昵称（不再出现未选择）', (tester) async {
    final memberStore = MemberStore(); // members 空 + currentId 0
    await pumpProfile(tester, memberStore);

    expect(find.text('当前就餐：掌勺人'), findsOneWidget);
    expect(find.text('未选择就餐成员'), findsNothing);
  });
}
