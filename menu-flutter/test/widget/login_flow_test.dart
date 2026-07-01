import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:menu_flutter/app.dart';
import 'package:menu_flutter/core/api_client.dart';
import 'package:menu_flutter/core/theme_controller.dart';
import 'package:menu_flutter/pages/home_page.dart';
import 'package:menu_flutter/pages/login_page.dart';
import 'package:menu_flutter/stores/auth_store.dart';
import '../helpers/mock_http.dart';

/// 端到端登录流程（widget 级，无需设备）：
/// 启动(未登录) → 登录页 → 输入密码 → 点登录 → mock 返回 token →
/// AuthStore 登录态变更 → router 重定向 → 首页渲染。
///
/// 覆盖全链路：LoginPage UI → AuthStore.login → AuthService.login →
/// ApiClient 拦截器解包 → 状态持久化 → go_router refreshListenable → HomePage。
void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  testWidgets('完整登录流程：输入密码 → 登录成功 → 跳转首页', (tester) async {
    // 按路径路由 mock：登录接口返回 token，其余首页请求返回空数据
    installMock((options) {
      if (options.path == '/auth/login') {
        return okResponse({'token': 'jwt-from-mock', 'nickname': '测试大厨'});
      }
      return okResponse({});
    });

    final auth = AuthStore();
    await auth.init(); // 未登录态

    await tester.pumpWidget(MenuApp(
      authStore: auth,
      themeController: ThemeController(),
      scaffoldKey: GlobalKey<ScaffoldMessengerState>(),
    ));
    await tester.pumpAndSettle();

    // 1. 初始：登录页
    expect(find.byType(LoginPage), findsOneWidget);
    expect(find.byType(HomePage), findsNothing);

    // 2. 输入密码（用户名已预填 admin）
    await tester.enterText(find.byType(TextField).at(1), '123456');
    await tester.pump();

    // 3. 点击登录
    await tester.tap(find.text('登录'));
    await tester.pumpAndSettle();

    // 4. 登录成功 → token 持久化 + ApiClient 同步 → 重定向到首页
    expect(auth.isLoggedIn, isTrue);
    expect(auth.token, 'jwt-from-mock');
    expect(auth.nickname, '测试大厨');
    expect(ApiClient.instance.token, 'jwt-from-mock');

    expect(find.byType(LoginPage), findsNothing);
    expect(find.byType(HomePage), findsOneWidget);

    // 持久化确认（跨重启恢复登录态）
    final sp = await SharedPreferences.getInstance();
    expect(sp.getString('token'), 'jwt-from-mock');
  });

  testWidgets('登录失败（业务错误）→ 停留登录页、未登录态', (tester) async {
    String? toasted;
    installMock(
      (options) {
        if (options.path == '/auth/login') {
          return errResponse(500, '用户名或密码错误');
        }
        return okResponse({});
      },
      onErrorToast: (m) => toasted = m,
    );

    final auth = AuthStore();
    await auth.init();

    await tester.pumpWidget(MenuApp(
      authStore: auth,
      themeController: ThemeController(),
      scaffoldKey: GlobalKey<ScaffoldMessengerState>(),
    ));
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField).at(1), 'wrong');
    await tester.tap(find.text('登录'));
    await tester.pumpAndSettle();

    // 登录失败：仍停留登录页，未登录
    expect(auth.isLoggedIn, isFalse);
    expect(find.byType(LoginPage), findsOneWidget);
    expect(find.byType(HomePage), findsNothing);
    // 拦截器弹出了业务错误 toast
    expect(toasted, '用户名或密码错误');
  });

  testWidgets('用户名密码为空 → 提示且不发请求', (tester) async {
    var anyRequest = false;
    installMock((options) {
      anyRequest = true;
      return okResponse({});
    });

    final auth = AuthStore();
    await auth.init();

    final scaffoldKey = GlobalKey<ScaffoldMessengerState>();
    await tester.pumpWidget(MenuApp(
      authStore: auth,
      themeController: ThemeController(),
      scaffoldKey: scaffoldKey,
    ));
    await tester.pumpAndSettle();

    // 清空预填的用户名
    await tester.enterText(find.byType(TextField).at(0), '');
    await tester.tap(find.text('登录'));
    await tester.pumpAndSettle();

    expect(auth.isLoggedIn, isFalse);
    expect(find.byType(LoginPage), findsOneWidget);
    expect(anyRequest, isFalse); // 前端校验拦截，未发请求
    // SnackBar 提示
    expect(find.text('请输入用户名和密码'), findsOneWidget);
  });
}
