import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:menu_flutter/core/ai_label.dart';
import 'package:menu_flutter/core/app_theme.dart';
import 'package:menu_flutter/core/theme_controller.dart';
import 'package:menu_flutter/pages/settings/feedback_page.dart';
import 'package:menu_flutter/stores/auth_store.dart';
import 'package:menu_flutter/stores/member_store.dart';
import 'package:menu_flutter/widgets/ai_badge.dart';
import '../helpers/mock_http.dart';

/// mini-release-compliance A.4：AiBadge（AI 生成标识 / 免责提示 / 反馈入口）与 AI 身份说明。
void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  Future<void> pump(WidgetTester tester, Widget child) async {
    final theme = ThemeController();
    final router = GoRouter(initialLocation: '/', routes: [
      GoRoute(path: '/', builder: (_, __) => Scaffold(body: Padding(padding: const EdgeInsets.all(16), child: child))),
      GoRoute(
          path: '/feedback',
          builder: (_, s) => FeedbackPage(
              aiSource: AiSource.parse(s.uri.queryParameters['source']), aiRef: s.uri.queryParameters['ref'])),
    ]);
    await tester.pumpWidget(MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => AuthStore()),
        ChangeNotifierProvider.value(value: theme),
        ChangeNotifierProvider(create: (_) => MemberStore()),
      ],
      child: MaterialApp.router(routerConfig: router, theme: buildBrandTheme(AppTokens.cream)),
    ));
    await tester.pumpAndSettle();
  }

  test('文案与隐私政策、小程序一致', () {
    expect(kAiLabel, 'AI 生成');
    expect(kAiDisclaimer, 'AI 生成，仅供参考，不能替代医生或营养师建议');
    expect(kAiDisclaimer, '$kAiLabel，$kAiDisclaimerTail');
    expect(aiFeedbackLocation(AiSource.agent, 'm 1'), '/feedback?type=ai&source=agent&ref=m+1');
    expect(aiFeedbackLocation(AiSource.scan), '/feedback?type=ai&source=scan');
    expect(aiSourceTag(AiSource.nutrition), 'source=AI/营养报告');
  });

  testWidgets('默认：只有「AI 生成」徽章', (tester) async {
    await pump(tester, const AiBadge());
    expect(find.byKey(const Key('ai-badge-chip')), findsOneWidget);
    expect(find.text('AI 生成'), findsOneWidget);
    expect(find.byKey(const Key('ai-disclaimer')), findsNothing);
    expect(find.byKey(const Key('ai-feedback')), findsNothing);
  });

  testWidgets('disclaimer：徽章 + 免责提示', (tester) async {
    await pump(tester, const AiBadge(disclaimer: true, source: AiSource.nutrition));
    expect(find.byKey(const Key('ai-disclaimer')), findsOneWidget);
    expect(find.text(kAiDisclaimerTail), findsOneWidget);
    expect(find.text('AI 生成'), findsOneWidget);
  });

  testWidgets('AI 身份说明', (tester) async {
    await pump(tester, const AiAssistantIntro());
    expect(find.textContaining('我是 AI 助手'), findsOneWidget);
    expect(find.text('AI 生成'), findsOneWidget);
  });

  testWidgets('反馈：进意见反馈页，默认选「AI 内容」，clientInfo 带来源标记', (tester) async {
    final captor = installMock((_) => okResponse(1));
    await pump(tester, const AiBadge(source: AiSource.weekplan, feedbackRef: 77));
    await tester.tap(find.byKey(const Key('ai-feedback')));
    await tester.pumpAndSettle();
    expect(find.byType(FeedbackPage), findsOneWidget);
    expect(find.text('AI 内容'), findsOneWidget);

    await tester.enterText(find.byKey(const Key('feedback-content')), '周三排了两道荤菜');
    await tester.tap(find.text('提交'));
    await tester.pumpAndSettle();
    final body = captor.all.lastWhere((o) => o.path == '/feedback').data as Map;
    expect(body['type'], 'ai');
    expect(body['clientInfo'], startsWith('source=AI/一周餐程#77'));
  });
}
