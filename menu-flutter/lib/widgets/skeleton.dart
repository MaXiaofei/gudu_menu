import 'package:flutter/material.dart';

import '../core/app_theme.dart';

/// 骨架块（原型 States：主色浅底，透明度 0.3↔0.7 呼吸，不用转圈）。
///
/// 呼吸为单程动画（不 repeat），避免 widget 测试 pumpAndSettle 卡死。
class SkeletonBox extends StatelessWidget {
  final double? width;
  final double height;
  final double radius;
  const SkeletonBox({super.key, this.width, required this.height, this.radius = AppTokens.rSm});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: 0.3, end: 0.7),
      duration: const Duration(milliseconds: 1200),
      builder: (_, v, child) => Opacity(opacity: v, child: child),
      child: Container(
        width: width,
        height: height,
        decoration: BoxDecoration(
          color: t.primarySoft,
          borderRadius: BorderRadius.circular(radius),
        ),
      ),
    );
  }
}

/// 列表骨架：[count] 行「左图右两行字」。
class SkeletonList extends StatelessWidget {
  final int count;
  final double thumb;
  const SkeletonList({super.key, this.count = 5, this.thumb = 72});

  @override
  Widget build(BuildContext context) {
    return ListView.separated(
      physics: const NeverScrollableScrollPhysics(),
      padding: const EdgeInsets.all(AppTokens.sp16),
      itemCount: count,
      separatorBuilder: (_, __) => const SizedBox(height: AppTokens.sp16),
      itemBuilder: (_, __) => Row(
        children: [
          SkeletonBox(width: thumb, height: thumb, radius: AppTokens.rMd),
          const SizedBox(width: AppTokens.sp12),
          const Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                SkeletonBox(width: 140, height: 14),
                SizedBox(height: AppTokens.sp8),
                SkeletonBox(width: double.infinity, height: 10),
                SizedBox(height: AppTokens.sp6),
                SkeletonBox(width: 90, height: 10),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
