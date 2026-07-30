import 'package:flutter/material.dart';

import '../core/app_theme.dart';

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
class EmptyView extends StatelessWidget {
  final String text;
  const EmptyView({super.key, this.text = '暂无数据'});
  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Text(text, style: TextStyle(color: t.caption)),
      ),
    );
  }
}
