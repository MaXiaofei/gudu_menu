import 'package:flutter/material.dart';

import '../core/app_theme.dart';

/// 首字色块占位（DESIGN.md §14.2：禁图标顶图，统一首字色块）。
///
/// 合并 4 处重复的首字占位逻辑（dish 列表、menu `_InitialStack`、
/// dish 详情用料行、pantry 详情头）。
///
/// 规格：
/// - 首字 = `name.trim().characters.first`，name 为空时回退「菜」
/// - 背景 `t.secondary`
/// - 文字色 `t.title.withAlpha(115)`（≈ 0.45 透明度，与 dish 列表现有占位一致）
/// - 字号 `size * 0.4`，字重 w600
///
/// 用法：
/// ```dart
/// InitialAvatar(name: dish.name, size: 44);
/// ```
class InitialAvatar extends StatelessWidget {
  /// 取首字的名称（菜名 / 食材名 / 食系名）。
  final String name;

  /// 占位尺寸（正方形）。
  final double size;

  /// 圆角，默认 `AppTokens.rMd`。
  final double radius;

  const InitialAvatar({
    super.key,
    required this.name,
    this.size = 44,
    this.radius = AppTokens.rMd,
  });

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final initial =
        name.trim().isNotEmpty ? name.trim().characters.first : '菜';
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: t.secondary,
        borderRadius: BorderRadius.circular(radius),
      ),
      alignment: Alignment.center,
      child: Text(
        initial,
        style: TextStyle(
          fontSize: size * 0.4,
          fontWeight: FontWeight.w600,
          color: t.title.withAlpha(115), // ≈ 0.45 透明度
          height: 1.0,
        ),
      ),
    );
  }
}
