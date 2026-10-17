import 'package:flutter/material.dart';

import '../core/app_theme.dart';
import 'illustration.dart';

/// 骨架屏加载态（DESIGN.md §1：禁止 spinner）。
/// 3 行占位矩形 + 主色浅底闪烁。
class LoadingView extends StatelessWidget {
  const LoadingView({super.key});
  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            _SkeletonLine(width: double.infinity, height: 16, t: t),
            const SizedBox(height: 12),
            _SkeletonLine(width: double.infinity, height: 12, t: t),
            const SizedBox(height: 8),
            _SkeletonLine(width: 180, height: 12, t: t),
          ],
        ),
      ),
    );
  }
}

class _SkeletonLine extends StatelessWidget {
  final double width;
  final double height;
  final AppTokens t;
  const _SkeletonLine({required this.width, required this.height, required this.t});

  @override
  Widget build(BuildContext context) {
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: 0.3, end: 0.7),
      duration: const Duration(milliseconds: 1200),
      builder: (_, value, child) => Opacity(opacity: value, child: child),
      child: Container(
        width: width,
        height: height,
        decoration: BoxDecoration(
          color: t.primarySoft,
          borderRadius: BorderRadius.circular(AppTokens.rSm),
        ),
      ),
    );
  }
}

/// 空态（复刻小程序「暂无菜品」等灰字居中样式）。
///
/// [text] 主文案；[subtitle] 可选副标题；[actionLabel]+[onAction] 可选 CTA 按钮。
/// 不传 subtitle/action 时为最简灰字空态（向后兼容现有调用）。
class EmptyView extends StatelessWidget {
  final String text;
  final String? subtitle;
  final String? actionLabel;
  final VoidCallback? onAction;
  /// 插画（原型 States：空态要有插画）；默认三样食材插画。
  final Widget? illustration;
  /// 次要动作（如「手动添加」）。
  final String? secondaryLabel;
  final VoidCallback? onSecondary;
  const EmptyView({
    super.key,
    this.text = '暂无数据',
    this.subtitle,
    this.actionLabel,
    this.onAction,
    this.illustration,
    this.secondaryLabel,
    this.onSecondary,
  });

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    // 最简形态：小插画 + 一行字（列表内嵌的轻量空态）。
    if (subtitle == null && actionLabel == null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              illustration ?? const StateIllustration(size: 88, faded: true),
              const SizedBox(height: AppTokens.sp12),
              Text(text, style: t.textStyles.body),
            ],
          ),
        ),
      );
    }
    // 增强形态：占位图块 + 主文案 + 副标题 + CTA（DESIGN.md §10.2 占位、§2.2 去 emoji）。
    return Padding(
      padding: const EdgeInsets.only(top: 80),
      child: Center(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 40),
          child: Column(
            children: [
              illustration ?? const StateIllustration(),
              const SizedBox(height: AppTokens.sp16),
              Text(text, style: t.textStyles.subtitle),
              if (subtitle != null) ...[
                const SizedBox(height: 6),
                Text(
                  subtitle!,
                  textAlign: TextAlign.center,
                  style: t.textStyles.caption,
                ),
              ],
              if (actionLabel != null && onAction != null) ...[
                const SizedBox(height: AppTokens.sp16),
                SizedBox(
                  width: 200,
                  child: ElevatedButton(onPressed: onAction, child: Text(actionLabel!)),
                ),
              ],
              if (secondaryLabel != null && onSecondary != null) ...[
                const SizedBox(height: AppTokens.sp8),
                SizedBox(
                  width: 200,
                  child: OutlinedButton(onPressed: onSecondary, child: Text(secondaryLabel!)),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
