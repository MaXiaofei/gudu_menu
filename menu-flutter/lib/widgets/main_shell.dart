import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../core/app_theme.dart';

/// 底部 5-Tab 主外壳（菜谱 / 食集 / [凸起推荐FAB] / 库存 / 我的）。
///
/// 由 go_router 的 StatefulShellRoute.indexedStack 注入 [navigationShell]，
/// 各 tab 状态独立保持（IndexedStack），切 tab 不重建。
///
/// 视觉规范（docs/design/DESIGN.md §8 TabBar、§9 TabBar 契约）：
/// - Tab Bar 高 56px · icon 20px · label 9px
/// - 推荐为凸起 FAB：直径 40px · 上凸 16px · --shadow-fab
/// - 选中态用 primary，未选中用 caption
class MainShell extends StatelessWidget {
  final StatefulNavigationShell navigationShell;

  const MainShell({super.key, required this.navigationShell});

  void _goBranch(int index) {
    navigationShell.goBranch(
      index,
      initialLocation: index == navigationShell.currentIndex,
    );
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final bottomPadding = MediaQuery.of(context).padding.bottom;
    return Scaffold(
      body: navigationShell,
      bottomNavigationBar: Material(
        color: t.card,
        child: SizedBox(
          height: 56 + bottomPadding,
          child: Stack(
            children: [
              // 底栏顶部分隔线
              Positioned(
                top: 0,
                left: 0,
                right: 0,
                child: ColoredBox(
                  color: t.border,
                  child: const SizedBox(height: 1, width: double.infinity),
                ),
              ),
              // 5 个等宽 tab（菜谱/食集/推荐/库存/我的）——2026-08-14：推荐去凸起，与其他风格一致
              Padding(
                padding: EdgeInsets.only(bottom: bottomPadding),
                child: Row(
                  children: [
                    _navItem(t, 0, Icons.menu_book_outlined, Icons.menu_book, '菜谱'),
                    _navItem(t, 1, Icons.restaurant_menu_outlined, Icons.restaurant_menu, '食集'),
                    _navItem(t, 2, Icons.auto_awesome_outlined, Icons.auto_awesome, '推荐'),
                    _navItem(t, 3, Icons.kitchen_outlined, Icons.kitchen, '库存'),
                    _navItem(t, 4, Icons.person_outline, Icons.person, '我的'),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  /// 单个普通 tab 项（占 1/4 宽度）。
  Widget _navItem(AppTokens t, int index, IconData icon, IconData activeIcon, String label) {
    final selected = navigationShell.currentIndex == index;
    final color = selected ? t.primary : t.caption;
    return Expanded(
      child: InkWell(
        onTap: () => _goBranch(index),
        child: SizedBox(
          height: 56,
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(selected ? activeIcon : icon, size: 20, color: color),
              const SizedBox(height: 2),
              Text(
                label,
                style: t.textStyles.tiny.copyWith(color: color),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

