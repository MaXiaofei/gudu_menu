import 'package:flutter/material.dart';

import '../core/app_theme.dart';

/// 通用卡片（DESIGN.md §9 Card 契约）：
/// - 背景 `t.card` + `shadow-sm`（浮起）
/// - 圆角 `rMd(12)`（DESIGN.md §8 卡片标准）
/// - shadow 与 border 二选一，本组件选 shadow
class AppCard extends StatelessWidget {
  final Widget child;
  final EdgeInsets padding;
  final double radius;
  final VoidCallback? onTap;

  const AppCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(16),
    this.radius = AppTokens.rMd,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Container(
      decoration: BoxDecoration(
        color: t.card,
        borderRadius: BorderRadius.circular(radius),
        boxShadow: t.elevationSm,
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(radius),
          hoverColor: t.primary.withValues(alpha: 0.04),
          splashColor: t.primary.withValues(alpha: 0.08),
          child: Padding(padding: padding, child: child),
        ),
      ),
    );
  }
}
