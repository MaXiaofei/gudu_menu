import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../core/app_theme.dart';
import '../core/theme_controller.dart';
import '../widgets/app_card.dart';

/// 「更多」页（第三个 tab）：厨房工具集合。
/// 参照小程序 menu-mini/src/pages/misc/Home.vue。
/// 顶栏提供双主题换肤入口（奶油轻食 / 抹茶禅意）。
class MorePage extends StatelessWidget {
  const MorePage({super.key});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Scaffold(
      backgroundColor: t.bg,
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(20, 16, 20, 32),
          children: [
            // 标题行（标题 + 换肤 + 设置入口）
            Row(
              children: [
                Container(
                    width: 4,
                    height: 22,
                    decoration: BoxDecoration(
                        color: t.primary,
                        borderRadius: BorderRadius.circular(2))),
                const SizedBox(width: 8),
                Text('更多', style: t.textStyles.h2),
                const Spacer(),
                Consumer<ThemeController>(
                  builder: (_, tc, __) => IconButton(
                    icon: Icon(
                      tc.isCream
                          ? Icons.spa_outlined
                          : Icons.local_cafe_outlined,
                      color: t.caption,
                    ),
                    tooltip: '换肤 · 当前「${tc.label}」',
                    onPressed: () => tc.toggle(),
                  ),
                ),
                IconButton(
                  icon: Icon(Icons.settings_outlined, color: t.caption),
                  tooltip: '设置',
                  onPressed: () => context.push('/settings'),
                ),
              ],
            ),
            const SizedBox(height: 6),
            Text('厨房里的得力小工具',
                style: t.textStyles.sm.copyWith(color: t.caption)),
            const SizedBox(height: 22),
            _ToolCard(
                name: '买菜',
                sub: '采购清单，缺啥买啥',
                onTap: () => context.push('/shopping')),
            _ToolCard(
                name: '家里有啥',
                sub: '冰箱食材库存、临期提醒',
                onTap: () => context.push('/pantry')),
            _ToolCard(
                name: '今天吃了啥',
                sub: '饮食日记，记一笔安心',
                onTap: () => context.push('/dailylog')),
            _ToolCard(
                name: '本周排菜',
                sub: '一周菜单排起来',
                onTap: () => context.push('/mealplan')),
            _ToolCard(
                name: '推荐菜',
                sub: '换菜单、算热量',
                onTap: () => context.push('/ai-recommend')),
            _ToolCard(
                name: '食材找菜',
                sub: '手里有啥，能做啥',
                onTap: () => context.push('/find-dish')),
          ],
        ),
      ),
    );
  }
}

class _ToolCard extends StatelessWidget {
  final String name;
  final String sub;
  final VoidCallback onTap;

  const _ToolCard({
    required this.name,
    required this.sub,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: AppCard(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        onTap: onTap,
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(name, style: t.textStyles.lg),
                  const SizedBox(height: 3),
                  Text(sub, style: t.textStyles.sm.copyWith(color: t.caption)),
                ],
              ),
            ),
            Icon(Icons.chevron_right, color: t.caption),
          ],
        ),
      ),
    );
  }
}
