import 'package:flutter/material.dart';

import '../core/app_theme.dart';

/// 卡片变体（DESIGN.md §8/§9：shadow 与 border 二选一，两类卡片各有归宿）。
enum _CardVariant {
  /// 浮起：`t.card` 底 + `elevationSm`（详情页/凸出卡片用）。
  elevated,

  /// 描边：`t.card` 底 + `Border.all(t.border)`（列表页扁平卡片用）。
  outlined,
}

/// 通用卡片（DESIGN.md §9 Card 契约 / §14.3 禁止页面手写）。
///
/// 提供两种变体，由构造方法选择：
/// - [AppCard]（默认 shadow 版）：背景 `t.card` + `elevationSm`（浮起）。
///   适用于详情页、需要从背景凸出的卡片。
/// - [AppCard.outlined]：背景 `t.card` + `Border.all(t.border)`（描边，无阴影）。
///   适用于列表页扁平卡片（列表项要 border 不要 shadow）。
///
/// 两类变体共享圆角（默认 `rMd`）、padding、`onTap` InkWell 反馈。
class AppCard extends StatelessWidget {
  final Widget child;
  final EdgeInsets padding;
  final double radius;
  final VoidCallback? onTap;

  // —— 描边变体专用 ——
  final _CardVariant _variant;
  final Color? _borderColor;
  final double _borderWidth;

  const AppCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(16),
    this.radius = AppTokens.rMd,
    this.onTap,
  })  : _variant = _CardVariant.elevated,
        _borderColor = null,
        _borderWidth = 1;

  /// 描边变体：背景 `t.card` + `Border.all(borderColor ?? t.border)`，
  /// 无阴影。列表页扁平卡片用（列表要 border 不要 shadow）。
  factory AppCard.outlined({
    Key? key,
    EdgeInsets padding = const EdgeInsets.all(16),
    double radius = AppTokens.rMd,
    VoidCallback? onTap,
    Color? borderColor,
    double borderWidth = 1,
    required Widget child,
  }) {
    return AppCard._(
      key: key,
      padding: padding,
      radius: radius,
      onTap: onTap,
      variant: _CardVariant.outlined,
      borderColor: borderColor,
      borderWidth: borderWidth,
      child: child,
    );
  }

  const AppCard._({
    super.key,
    required this.child,
    required this.padding,
    required this.radius,
    this.onTap,
    required _CardVariant variant,
    Color? borderColor,
    double borderWidth = 1,
  })  : _variant = variant,
        _borderColor = borderColor,
        _borderWidth = borderWidth;

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final border = _variant == _CardVariant.outlined
        ? Border.all(color: _borderColor ?? t.border, width: _borderWidth)
        : null;
    final boxShadow = _variant == _CardVariant.elevated ? t.elevationSm : null;
    return Container(
      decoration: BoxDecoration(
        color: t.card,
        borderRadius: BorderRadius.circular(radius),
        border: border,
        boxShadow: boxShadow,
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
