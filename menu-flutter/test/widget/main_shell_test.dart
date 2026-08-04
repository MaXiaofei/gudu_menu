import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import 'package:menu_flutter/core/app_theme.dart';
import 'package:menu_flutter/widgets/main_shell.dart';

/// MainShell 5-Tab 切换测试（widget 级，不依赖设备）。
///
/// 用真实 StatefulShellRoute.indexedStack 构造 5 个占位 tab，
/// 验证：4 个普通 tab + 凸起推荐 FAB 点击后路由正确切换。
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

    // 点"食集" → index 1
    await tester.tap(find.text('食集'));
    await tester.pumpAndSettle();
    expect(find.text('PAGE_1'), findsOneWidget);

    // 点"库存" → index 3
    await tester.tap(find.text('库存'));
    await tester.pumpAndSettle();
    expect(find.text('PAGE_3'), findsOneWidget);

    // 点"我的" → index 4
    await tester.tap(find.text('我的'));
    await tester.pumpAndSettle();
    expect(find.text('PAGE_4'), findsOneWidget);

    // 点凸起"推荐"FAB（通过 icon 定位）→ index 2
    await tester.tap(find.byIcon(Icons.auto_awesome));
    await tester.pumpAndSettle();
    expect(find.text('PAGE_2'), findsOneWidget);

    // 点"菜谱" → 回 index 0
    await tester.tap(find.text('菜谱'));
    await tester.pumpAndSettle();
    expect(find.text('PAGE_0'), findsOneWidget);
  });
}
