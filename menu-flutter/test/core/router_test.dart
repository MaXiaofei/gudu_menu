import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:menu_flutter/app.dart';
import 'package:menu_flutter/core/constants.dart';
import 'package:menu_flutter/core/theme_controller.dart';
import 'package:menu_flutter/pages/home_page.dart';
import 'package:menu_flutter/pages/login_page.dart';
import 'package:menu_flutter/stores/auth_store.dart';
import '../helpers/mock_http.dart';

/// createRouter + AuthStore 联动的重定向契约（对应小程序未登录 reLaunch 到登录页）：
/// - 未登录启动 → 重定向 /login
/// - 已登录启动 → 放行 / 首页；登出后 → refreshListenable 触发重定向回 /login
///
/// 用真实 MenuApp（注入 Provider + 绑定 401 跳转）驱动，验证端到端重定向结果。
void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  testWidgets('未登录启动 → 重定向到 /login', (tester) async {
    final auth = AuthStore(); // isLoggedIn=false
    expect(auth.isLoggedIn, isFalse); // 前置

    await tester.pumpWidget(MenuApp(
      authStore: auth,
      themeController: ThemeController(),
      scaffoldKey: GlobalKey<ScaffoldMessengerState>(),
    ));
    await tester.pumpAndSettle();

    expect(find.byType(LoginPage), findsOneWidget);
    expect(find.byType(HomePage), findsNothing);
  });

  testWidgets('已登录启动 → 首页；登出后 → 重定向回 /login', (tester) async {
    // 持久化 token + init 模拟登录态
    SharedPreferences.setMockInitialValues(
      {AppConstants.tokenKey: 'fake-token'},
    );
    final auth = AuthStore();
    await auth.init();
    expect(auth.isLoggedIn, isTrue); // 前置

    // 安装 mock：首页 initState 会请求统计，避免真实联网超时
    installMock((_) => okResponse({}));

    await tester.pumpWidget(MenuApp(
      authStore: auth,
      themeController: ThemeController(),
      scaffoldKey: GlobalKey<ScaffoldMessengerState>(),
    ));
    await tester.pumpAndSettle();

    // 已登录 → 放行首页
    expect(find.byType(HomePage), findsOneWidget);

    // 登出 → refreshListenable(auth) 触发 redirect 重算 → 回登录页
    await auth.logout();
    await tester.pumpAndSettle();

    expect(find.byType(LoginPage), findsOneWidget);
    expect(find.byType(HomePage), findsNothing);
  });
}
