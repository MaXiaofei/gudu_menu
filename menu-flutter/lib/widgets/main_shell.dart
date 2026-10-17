import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../core/app_theme.dart';
import 'offline_banner.dart';

/// 底部 5-Tab 主外壳（菜谱 / 食集 / 智荐 / 我家余量 / 我的，定稿命名）。
///
/// 由 go_router 的 StatefulShellRoute.indexedStack 注入 [navigationShell]，
/// 各 tab 状态独立保持（IndexedStack），切 tab 不重建。
///
/// 视觉（原型 Components「底部 Tab 栏」）：中间「智荐」为 primaryDeep 凸起圆钮；
/// 选中文字 primaryDeep、未选 body（caption 在 10px 下对比度不足）。
/// 顶部叠加全局离线条（不推挤页面布局，不挡操作）。
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
    final mq = MediaQuery.of(context);
    final bottomPadding = mq.padding.bottom;
    return Scaffold(
      body: Stack(
        children: [
          navigationShell,
          Positioned(top: mq.padding.top, left: 0, right: 0, child: const OfflineBanner()),
        ],
      ),
      bottomNavigationBar: Container(
        height: 60 + bottomPadding,
        padding: EdgeInsets.only(bottom: bottomPadding),
        decoration: BoxDecoration(
          color: t.card,
          border: Border(top: BorderSide(color: t.border)),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            _navItem(t, 0, Icons.menu_book_outlined, Icons.menu_book, '菜谱'),
            _navItem(t, 1, Icons.receipt_long_outlined, Icons.receipt_long, '食集'),
            _centerItem(t),
            _navItem(t, 3, Icons.kitchen_outlined, Icons.kitchen, '我家余量'),
            _navItem(t, 4, Icons.person_outline, Icons.person, '我的'),
          ],
        ),
      ),
    );
  }

  Widget _navItem(AppTokens t, int index, IconData icon, IconData activeIcon, String label) {
    final selected = navigationShell.currentIndex == index;
    final color = selected ? t.primaryDeep : t.body;
    return Expanded(
      child: InkWell(
        onTap: () => _goBranch(index),
        child: SizedBox(
          height: 60,
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(selected ? activeIcon : icon, size: 22, color: color),
              const SizedBox(height: 3),
              Text(label,
                  style: t.textStyles.tiny.copyWith(
                    color: color,
                    fontWeight: selected ? FontWeight.w800 : FontWeight.w500,
                  )),
            ],
          ),
        ),
      ),
    );
  }

  /// 中间凸起的「智荐」圆钮（50px，上凸 18px，白描边 + FAB 阴影）。
  Widget _centerItem(AppTokens t) {
    final selected = navigationShell.currentIndex == 2;
    return Expanded(
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: () => _goBranch(2),
        child: SizedBox(
          height: 60,
          child: Stack(
            clipBehavior: Clip.none,
            alignment: Alignment.topCenter,
            children: [
              Positioned(
                top: -18,
                child: Container(
                  width: 50,
                  height: 50,
                  decoration: BoxDecoration(
                    color: t.primaryDeep,
                    shape: BoxShape.circle,
                    border: Border.all(color: t.card, width: 3),
                    boxShadow: t.elevationFab,
                  ),
                  child: Icon(Icons.soup_kitchen_rounded, size: 24, color: t.card),
                ),
              ),
              Positioned(
                bottom: 8,
                child: Text('智荐',
                    style: t.textStyles.tiny.copyWith(
                      color: selected ? t.primaryDeep : t.body,
                      fontWeight: selected ? FontWeight.w800 : FontWeight.w500,
                    )),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
