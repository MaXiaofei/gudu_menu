import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:menu_flutter/core/app_theme.dart';
import 'package:menu_flutter/core/constants.dart';
import 'package:menu_flutter/core/theme_controller.dart';
import 'package:menu_flutter/pages/profile_page.dart';
import 'package:menu_flutter/pages/settings/about_page.dart';
import 'package:menu_flutter/pages/settings/feedback_page.dart';
import 'package:menu_flutter/pages/settings/legal_page.dart';
import 'package:menu_flutter/pages/settings/profile_edit_page.dart';
import 'package:menu_flutter/pages/settings/theme_page.dart';
import 'package:menu_flutter/stores/auth_store.dart';
import 'package:menu_flutter/stores/member_store.dart';
import '../helpers/mock_http.dart';

/// 我的 tab 设置项新页面（G7/G10/G14）：主题外观 / 关于 / 协议 / 意见反馈 / 个人资料。
void main() {
  late ThemeController theme;
  late AuthStore auth;

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    theme = ThemeController();
    auth = AuthStore()..nickname = '掌勺人';
  });

  Future<void> pump(WidgetTester tester, String initial) async {
    final router = GoRouter(initialLocation: initial, routes: [
      GoRoute(path: '/profile', builder: (_, __) => const ProfilePage()),
      GoRoute(path: '/settings/theme', builder: (_, __) => const ThemePage()),
      GoRoute(path: '/about', builder: (_, __) => const AboutPage()),
      GoRoute(path: '/feedback', builder: (_, __) => const FeedbackPage()),
      GoRoute(path: '/profile/edit', builder: (_, __) => const ProfileEditPage()),
      GoRoute(
          path: '/legal/terms',
          builder: (_, __) => const LegalPage(kind: LegalKind.terms)),
      GoRoute(
          path: '/legal/privacy',
          builder: (_, __) => const LegalPage(kind: LegalKind.privacy)),
    ]);
    await tester.pumpWidget(MultiProvider(
      providers: [
        ChangeNotifierProvider.value(value: auth),
        ChangeNotifierProvider.value(value: theme),
        ChangeNotifierProvider(create: (_) => MemberStore()),
      ],
      child: AnimatedBuilder(
        animation: theme,
        builder: (_, __) =>
            MaterialApp.router(routerConfig: router, theme: theme.themeData),
      ),
    ));
    await tester.pumpAndSettle();
  }

  testWidgets('我的页：主题外观/关于 不再是空 onTap，进入对应页面', (tester) async {
    tester.view.physicalSize = const Size(1080, 2400);
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.reset);
    installMock((o) => o.path == '/dish/draft/list'
        ? okResponse({'records': [], 'total': 0})
        : okResponse(null));
    await pump(tester, '/profile');

    expect(find.text('奶油轻食'), findsOneWidget); // 主题外观尾部当前值
    expect(find.text('v${AppConstants.appVersion}'), findsOneWidget);

    await tester.tap(find.text('主题外观'));
    await tester.pumpAndSettle();
    expect(find.byType(ThemePage), findsOneWidget);

    await tester.pageBack();
    await tester.pumpAndSettle();
    await tester.tap(find.text('关于小食单'));
    await tester.pumpAndSettle();
    expect(find.byType(AboutPage), findsOneWidget);
  });

  testWidgets('主题外观：点抹茶禅意 → ThemeController 切换并持久化', (tester) async {
    installMock((_) => okResponse(null));
    await pump(tester, '/settings/theme');

    expect(theme.skinKey, 'cream');
    await tester.tap(find.text('抹茶禅意'));
    await tester.pumpAndSettle();

    expect(theme.skinKey, 'matcha');
    // 整个 App 主题跟随
    final ctx = tester.element(find.byType(ThemePage));
    expect(AppTokens.of(ctx).primary, AppTokens.matcha.primary);
    final sp = await SharedPreferences.getInstance();
    expect(sp.getString(ThemeController.prefKey), 'matcha');
    // 重新 load 恢复
    expect((await ThemeController.load()).skinKey, 'matcha');
  });

  test('旧版 int 偏好（gudu.brandTheme=1）迁移为 matcha', () async {
    SharedPreferences.setMockInitialValues({'gudu.brandTheme': 1});
    expect((await ThemeController.load()).skinKey, 'matcha');
    SharedPreferences.setMockInitialValues({'gudu.brandTheme': 9});
    expect((await ThemeController.load()).skinKey, 'cream');
  });

  testWidgets('关于：版本号 + 协议/隐私/反馈入口', (tester) async {
    installMock((_) => okResponse(null));
    await pump(tester, '/about');

    expect(find.text('版本 v${AppConstants.appVersion}'), findsOneWidget);
    await tester.tap(find.text('隐私政策'));
    await tester.pumpAndSettle();
    expect(find.byType(LegalPage), findsOneWidget);
    // 隐私政策正文从 assets/legal/privacy.md 渲染（含表格与小节标题）
    expect(find.textContaining('我们收集的信息及用途', findRichText: true), findsOneWidget);
    expect(find.byType(Table), findsWidgets);

    await tester.pageBack();
    await tester.pumpAndSettle();
    await tester.tap(find.text('意见反馈'));
    await tester.pumpAndSettle();
    expect(find.byType(FeedbackPage), findsOneWidget);
  });

  testWidgets('用户协议页渲染 terms.md', (tester) async {
    installMock((_) => okResponse(null));
    await pump(tester, '/legal/terms');
    expect(find.textContaining('咕嘟小食单用户协议', findRichText: true), findsOneWidget);
    expect(find.textContaining('服务内容', findRichText: true), findsWidgets);
  });

  testWidgets('意见反馈：少于 5 字提示且不发请求；合法内容 POST /feedback', (tester) async {
    final captor = installMock((_) => okResponse(1));
    await pump(tester, '/about');
    await tester.tap(find.text('意见反馈'));
    await tester.pumpAndSettle();

    await tester.enterText(find.byKey(const Key('feedback-content')), '慢');
    await tester.tap(find.text('提交'));
    await tester.pump();
    expect(find.textContaining('请至少写 5 个字'), findsOneWidget);
    expect(captor.all.where((o) => o.path == '/feedback'), isEmpty);

    await tester.enterText(
        find.byKey(const Key('feedback-content')), '采购清单分享图片不清楚');
    await tester.enterText(find.byKey(const Key('feedback-contact')), 'wx_abc');
    await tester.tap(find.text('提交'));
    await tester.pumpAndSettle();

    final req = captor.all.lastWhere((o) => o.path == '/feedback');
    expect(req.method, 'POST');
    final body = req.data as Map;
    expect(body['content'], '采购清单分享图片不清楚');
    expect(body['contact'], 'wx_abc');
    expect(body['clientInfo'], contains('v${AppConstants.appVersion}'));
    // 成功后返回上一页
    expect(find.byType(FeedbackPage), findsNothing);
    expect(find.byType(AboutPage), findsOneWidget);
  });

  testWidgets('意见反馈：后端失败 → 提示失败、停留本页', (tester) async {
    installMock((_) => errResponse(429, '今日反馈次数已达上限'));
    await pump(tester, '/feedback');
    await tester.enterText(
        find.byKey(const Key('feedback-content')), '有个按钮点了没反应');
    await tester.tap(find.text('提交'));
    await tester.pumpAndSettle();
    expect(find.textContaining('今日反馈次数已达上限'), findsOneWidget);
    expect(find.byType(FeedbackPage), findsOneWidget);
  });

  group('个人资料', () {
    dynamic responder(RequestOptions o) {
      if (o.path == '/member/me' && o.method == 'GET') {
        return okResponse({'id': 1, 'name': '妈妈', 'avatarUrl': null, 'phone': '138****0001'});
      }
      return okResponse(null);
    }

    testWidgets('加载 → 改昵称 → PUT /member/me → 同步 AuthStore', (tester) async {
      final captor = installMock(responder);
      await pump(tester, '/about');
      // 从关于页 push，保证保存后 pop 有落点
      GoRouter.of(tester.element(find.byType(AboutPage))).push('/profile/edit');
      await tester.pumpAndSettle();

      expect(find.text('妈妈'), findsOneWidget);
      expect(find.text('手机号 138****0001'), findsOneWidget);

      await tester.enterText(find.byKey(const Key('profile-name')), '老妈');
      await tester.tap(find.text('保存'));
      await tester.pumpAndSettle();

      final put = captor.all.lastWhere((o) => o.method == 'PUT');
      expect(put.path, '/member/me');
      expect(put.data, {'name': '老妈'}); // 头像未改不传
      expect(auth.nickname, '老妈');
      expect(find.byType(ProfileEditPage), findsNothing);
    });

    testWidgets('昵称为空 → 提示，不提交', (tester) async {
      final captor = installMock(responder);
      await pump(tester, '/profile/edit');
      await tester.enterText(find.byKey(const Key('profile-name')), '  ');
      await tester.tap(find.text('保存'));
      await tester.pump();
      expect(find.text('昵称需 1–20 个字'), findsOneWidget);
      expect(captor.all.where((o) => o.method == 'PUT'), isEmpty);
    });

    testWidgets('加载失败 → 错误态 + 重试', (tester) async {
      var fail = true;
      installMock((o) {
        if (o.path == '/member/me' && fail) return errResponse(500, '服务开小差');
        return responder(o);
      });
      await pump(tester, '/profile/edit');
      expect(find.text('服务开小差'), findsOneWidget);
      expect(find.text('重试'), findsOneWidget);

      fail = false;
      await tester.tap(find.text('重试'));
      await tester.pumpAndSettle();
      expect(find.text('妈妈'), findsOneWidget);
    });
  });
}
