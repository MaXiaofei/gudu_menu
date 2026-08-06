import 'package:flutter/material.dart';

import '../core/app_theme.dart';

/// 通用状态徽章：用 color.withAlpha 做半透明底 + 描边，跟随主题透明度逻辑。
/// 范式对齐项目内联 chip 写法（pantry list_page _miniBtn / dish detail_page 标签）。
class StatusChip extends StatelessWidget {
  final String label;
  final Color color;
  final double fontSize;

  const StatusChip({
    super.key,
    required this.label,
    required this.color,
    this.fontSize = 11,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withAlpha(15),
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: color.withAlpha(60)),
      ),
      child: Text(
        label,
        style: TextStyle(fontSize: fontSize, color: color, fontWeight: FontWeight.w600),
      ),
    );
  }
}

/// 库存三色状态 → 颜色映射。
Color stockColor(String status) {
  switch (status) {
    case 'NONE':
      return AppTokens.error; // 红（缺/空）
    case 'LOW':
      return AppTokens.warning; // 黄（偏低）
    default:
      return AppTokens.success; // 绿（够）
  }
}

/// 库存三色状态 → 中文标签。
String stockLabel(String status) {
  switch (status) {
    case 'NONE':
      return '缺';
    case 'LOW':
      return '低';
    default:
      return '够';
  }
}
