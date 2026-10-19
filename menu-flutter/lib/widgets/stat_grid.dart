import 'package:flutter/material.dart';

import '../core/app_theme.dart';

/// 统计格语气。
enum StatTone { success, warning, error, neutral, dark }

/// 一格统计（大数字 + 标签）。
class StatItem {
  final String value;
  final String label;
  final StatTone tone;
  final VoidCallback? onTap;
  const StatItem(this.value, this.label, {this.tone = StatTone.neutral, this.onTap});
}

/// 统计格（原型：余量顶部四格、我的三格）。
class StatGrid extends StatelessWidget {
  final List<StatItem> items;
  final double height;
  const StatGrid({super.key, required this.items, this.height = 64});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    (Color, Color, Color) colors(StatTone tone) => switch (tone) {
          StatTone.success => (AppTokens.successBg, AppTokens.successText, AppTokens.successText),
          StatTone.warning => (AppTokens.warningBg, AppTokens.warningText, AppTokens.warningText),
          StatTone.error => (AppTokens.errorBg, AppTokens.errorText, AppTokens.errorText),
          StatTone.dark => (t.title, t.card, t.card),
          StatTone.neutral => (t.card, t.title, t.body),
        };
    return Row(
      children: [
        for (var i = 0; i < items.length; i++) ...[
          if (i > 0) const SizedBox(width: AppTokens.sp8),
          Expanded(
            child: GestureDetector(
              onTap: items[i].onTap,
              child: Builder(builder: (_) {
                final (bg, fg, sub) = colors(items[i].tone);
                return Container(
                  height: height,
                  padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp12, vertical: AppTokens.sp8),
                  decoration: BoxDecoration(
                    color: bg,
                    borderRadius: BorderRadius.circular(AppTokens.rMd),
                    border: items[i].tone == StatTone.neutral ? Border.all(color: t.border) : null,
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text(items[i].value, style: ts.h3.copyWith(color: fg, height: 1.1)),
                      const SizedBox(height: 2),
                      Text(items[i].label,
                          maxLines: 1, overflow: TextOverflow.ellipsis, style: ts.sectionLabel.copyWith(color: sub)),
                    ],
                  ),
                );
              }),
            ),
          ),
        ],
      ],
    );
  }
}
