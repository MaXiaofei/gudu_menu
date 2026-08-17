import 'package:flutter/material.dart';

import '../core/app_theme.dart';

/// 统一筛选/选择 chip（DESIGN.md §11.6，2026-08-17 定稿，样式源自菜谱页菜系筛选）：
/// 未选白底 + 1px 细边框（t.border）；选中深棕实底（t.title）+ 白字；
/// 8px 圆角（rSm）；11px/w700 文字。横排容器由调用方提供（ListView/Row/Wrap）。
///
/// 用法：
/// ```dart
/// SelectChip(label: '川菜', selected: _sel == id, onTap: () => _sel = id)
/// SelectChip(label: '缺 3', selected: true, semanticColor: AppTokens.error, ...)
/// ```
/// [semanticColor]：未选时文字语义色（如库存档位 缺=红/低=黄/够=绿），选中后统一白字。
class SelectChip extends StatelessWidget {
  final String label;
  final bool selected;
  final VoidCallback onTap;
  /// 未选态文字语义色（默认 t.body 正文色）。
  final Color? semanticColor;

  const SelectChip({
    super.key,
    required this.label,
    required this.selected,
    required this.onTap,
    this.semanticColor,
  });

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
        decoration: BoxDecoration(
          color: selected ? t.title : t.card,
          borderRadius: BorderRadius.circular(AppTokens.rSm),
          border: selected ? null : Border.all(color: t.border),
        ),
        child: Text(
          label,
          style: t.textStyles.sectionLabel.copyWith(
            color: selected ? Colors.white : (semanticColor ?? t.body),
          ),
        ),
      ),
    );
  }
}
