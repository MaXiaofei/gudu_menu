import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../core/app_theme.dart';
import 'offline_banner.dart';

/// 底部 5-Tab 主外壳（showcase-ai-community 导航改版）：
/// 菜谱 / 食集 / 咕嘟（中间凸起）/ 厨房圈 / 我家余量。
///
/// 「我的」移到各主 Tab 右上角头像（[MeAvatarButton]）；原「智荐」并入咕嘟首屏，
/// 「全部推荐」为二级页。
///
/// 由 go_router 的 StatefulShellRoute.indexedStack 注入 [navigationShell]，
/// 各 tab 状态独立保持（IndexedStack），切 tab 不重建。
/// 视觉（原型 Agent.html `nav.gnav`）：中间「咕嘟」为渐变凸起圆钮 + AI 角标；
/// 选中文字 primaryDeep、未选 body（caption 在 10px 下对比度不足）。
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
            _navItem(t, 3, Icons.people_alt_outlined, Icons.people_alt, '厨房圈'),
            _navItem(t, 4, Icons.kitchen_outlined, Icons.kitchen, '我家余量'),
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

  /// 中间凸起的「咕嘟」圆钮（54px，上凸 22px，白描边 + FAB 阴影 + 右上角 AI 角标）。
  Widget _centerItem(AppTokens t) {
    final selected = navigationShell.currentIndex == 2;
    return Expanded(
      child: Semantics(
        button: true,
        label: '咕嘟 AI 助手',
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
                  top: -22,
                  child: Container(
                    width: 54,
                    height: 54,
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                        colors: [t.primary, t.primaryDeep],
                      ),
                      shape: BoxShape.circle,
                      border: Border.all(color: t.card, width: 3),
                      boxShadow: t.elevationFab,
                    ),
                    child: Icon(Icons.soup_kitchen_rounded, size: 26, color: t.card),
                  ),
                ),
                Positioned(
                  top: -24,
                  right: 0,
                  left: 34,
                  child: Center(
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp4, vertical: 1),
                      decoration: BoxDecoration(
                        color: t.title,
                        borderRadius: BorderRadius.circular(AppTokens.rPill),
                        border: Border.all(color: t.card, width: 1.5),
                      ),
                      child: Text('AI', style: t.textStyles.micro.copyWith(color: t.card)),
                    ),
                  ),
                ),
                Positioned(
                  bottom: 8,
                  child: Text('咕嘟',
                      style: t.textStyles.tiny.copyWith(
                        color: selected ? t.primaryDeep : t.body,
                        fontWeight: selected ? FontWeight.w800 : FontWeight.w500,
                      )),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
