import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import 'package:menu_flutter/core/app_theme.dart';
import 'package:menu_flutter/widgets/main_shell.dart';

/// MainShell 5-Tab 切换测试（widget 级，不依赖设备）。
///
/// showcase-ai-community 导航改版：菜谱 / 食集 / 咕嘟（凸起）/ 厨房圈 / 我家余量；「我的」不再是 Tab。
void main() {
  testWidgets('点击各 tab → 路由正确切换', (tester) async {
    final router = GoRouter(
      initialLocation: '/tab0',
      routes: [
        StatefulShellRoute.indexedStack(
          builder: (_, __, shell) => MaterialApp(
            theme: ThemeData(extensions: const [AppTokens.cream]),
            home: MainShell(navigationShell: shell),
          ),
          branches: [
            for (var i = 0; i < 5; i++)
              StatefulShellBranch(routes: [
                GoRoute(
                  path: '/tab$i',
                  builder: (_, __) => Scaffold(
                    body: Center(child: Text('PAGE_$i')),
                  ),
                ),
              ]),
          ],
        ),
      ],
    );

    await tester.pumpWidget(MaterialApp.router(routerConfig: router));
    await tester.pumpAndSettle();

    // 初始：菜谱 tab (index 0)
    expect(find.text('PAGE_0'), findsOneWidget);
    expect(find.text('我的'), findsNothing, reason: '「我的」移到右上角头像，不再占 Tab');
    expect(find.text('智荐'), findsNothing, reason: '智荐并入咕嘟首屏');

    await tester.tap(find.text('食集'));
    await tester.pumpAndSettle();
    expect(find.text('PAGE_1'), findsOneWidget);

    // 中间凸起「咕嘟」→ index 2，带 AI 角标
    expect(find.text('AI'), findsOneWidget);
    await tester.tap(find.text('咕嘟'));
    await tester.pumpAndSettle();
    expect(find.text('PAGE_2'), findsOneWidget);

    await tester.tap(find.text('厨房圈'));
    await tester.pumpAndSettle();
    expect(find.text('PAGE_3'), findsOneWidget);

    await tester.tap(find.text('我家余量'));
    await tester.pumpAndSettle();
    expect(find.text('PAGE_4'), findsOneWidget);

    await tester.tap(find.text('菜谱'));
    await tester.pumpAndSettle();
    expect(find.text('PAGE_0'), findsOneWidget);
  });
}
