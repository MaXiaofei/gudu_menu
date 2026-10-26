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
    expect(aiSourceTag(AiSource.scan), 'source=AI/拍照识别');
    // 营养报告（规则计算）、智荐（本地向量召回）不是模型生成：不在 AI 来源里，健康提示不带「AI」
    expect(AiSource.values.map((s) => s.name), ['agent', 'scan', 'weekplan']);
    expect(kHealthDisclaimer, '仅供参考，不能替代医生或营养师建议');
    expect(kHealthDisclaimer, isNot(contains('AI')));
  });

  testWidgets('默认：只有「AI 生成」徽章', (tester) async {
    await pump(tester, const AiBadge());
    expect(find.byKey(const Key('ai-badge-chip')), findsOneWidget);
    expect(find.text('AI 生成'), findsOneWidget);
    expect(find.byKey(const Key('ai-disclaimer')), findsNothing);
    expect(find.byKey(const Key('ai-feedback')), findsNothing);
  });

  testWidgets('disclaimer：徽章 + 免责提示', (tester) async {
    await pump(tester, const AiBadge(disclaimer: true, source: AiSource.weekplan));
    expect(find.byKey(const Key('ai-disclaimer')), findsOneWidget);
    expect(find.text(kAiDisclaimerTail), findsOneWidget);
    expect(find.text('AI 生成'), findsOneWidget);
  });

  testWidgets('HealthNote：只有健康免责提示，无「AI 生成」与反馈入口', (tester) async {
    await pump(tester, const HealthNote());
    expect(find.byKey(const Key('health-note')), findsOneWidget);
    expect(find.text(kHealthDisclaimer), findsOneWidget);
    expect(find.text('AI 生成'), findsNothing);
    expect(find.byKey(const Key('ai-feedback')), findsNothing);
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
