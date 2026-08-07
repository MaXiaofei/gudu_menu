import 'package:flutter/material.dart';

import '../core/app_theme.dart';

/// 分区标题（DESIGN.md §14.3：禁止页面私有重造 `_SectionTitle`）。
///
/// 统一规格（替换原 dish/menu 详情各自私有且不一致的 `_SectionTitle`）：
/// - 字号 `t.textStyles.h3`（20 / w700）
/// - 颜色 `t.title`
/// - padding `EdgeInsets.all(AppTokens.sp16)`，左对齐
/// - 可选 [subtitle]：右侧副标题（如「份数 1 · 共 2 样」），右对齐，
///   `t.textStyles.xs.copyWith(color: t.caption)`，与标题 baseline 对齐
///
/// 用法：
/// ```dart
/// SectionTitle('做法', subtitle: '约 30 分钟');
/// ```
class SectionTitle extends StatelessWidget {
  /// 左侧主标题。
  final String text;

  /// 可选右侧副标题（右对齐，caption 色）。
  final String? subtitle;

  const SectionTitle(this.text, {super.key, this.subtitle});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Padding(
      padding: const EdgeInsets.all(AppTokens.sp16),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.baseline,
        textBaseline: TextBaseline.alphabetic,
        children: [
          Expanded(
            child: Text(
              text,
              style: t.textStyles.h3,
            ),
          ),
          if (subtitle != null)
            Text(
              subtitle!,
              style: t.textStyles.xs.copyWith(color: t.caption),
              textAlign: TextAlign.right,
            ),
        ],
      ),
    );
  }
}
