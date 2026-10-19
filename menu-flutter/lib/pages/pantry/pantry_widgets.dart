import 'package:flutter/material.dart';

import '../../core/app_theme.dart';
import '../../services/pantry_service.dart';
import '../../widgets/ingredient_icon.dart';
import '../../widgets/tone_badge.dart';

/// 余量行（原型 Pantry）：食材插画 + 名称/来源 + 到期徽章 + 行内三档切换；点行进详情盘点。
class PantryRow extends StatelessWidget {
  final PantryGroupedItem item;
  final VoidCallback onTap;
  final ValueChanged<String> onLevel;
  const PantryRow({super.key, required this.item, required this.onTap, required this.onLevel});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    final hasSource = item.sourceLabel.isNotEmpty;
    final depleted = item.level == StockLevel.none;
    final expiry = depleted || item.daysLeft == null ? null : ToneBadge.expiry(item.daysLeft);
    return Container(
      margin: const EdgeInsets.only(bottom: AppTokens.sp8),
      decoration: BoxDecoration(
        color: t.card,
        borderRadius: BorderRadius.circular(AppTokens.rLg),
        border: Border.all(color: t.border),
      ),
      child: InkWell(
        borderRadius: BorderRadius.circular(AppTokens.rLg),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(AppTokens.sp10),
          child: Row(
            children: [
              IngredientIcon(
                name: item.displayName,
                iconKey: item.iconKey,
                category: item.categoryName,
                size: 44,
                depleted: depleted,
              ),
              const SizedBox(width: AppTokens.sp12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(children: [
                      Flexible(
                        child: Text(item.displayName,
                            maxLines: 1, overflow: TextOverflow.ellipsis, style: ts.cardTitle.copyWith(color: t.title)),
                      ),
                      if (expiry != null) ...[
                        const SizedBox(width: AppTokens.sp6),
                        KeyedSubtree(key: ValueKey('days-left-${item.ingredientId}'), child: expiry),
                      ] else if (!depleted && item.daysLeft != null) ...[
                        const SizedBox(width: AppTokens.sp6),
                        Text(daysLeftLabel(item.daysLeft!),
                            key: ValueKey('days-left-${item.ingredientId}'), style: ts.caption),
                      ],
                    ]),
                    const SizedBox(height: 2),
                    Text(
                      hasSource
                          ? '${item.sourceLabel} ${item.sourceSub}'
                          : (depleted ? '本来就没有' : '无变动记录'),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: ts.caption,
                    ),
                  ],
                ),
              ),
              const SizedBox(width: AppTokens.sp8),
              LevelSegment(level: item.level, onChanged: onLevel),
            ],
          ),
        ),
      ),
    );
  }
}

/// 行内三档切换（充足 / 不足 / 用完），选中为对应功能色实底。
class LevelSegment extends StatelessWidget {
  final String level;
  final ValueChanged<String> onChanged;
  const LevelSegment({super.key, required this.level, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    const opts = [
      (StockLevel.enough, '充足', AppTokens.success),
      (StockLevel.low, '不足', AppTokens.warning),
      (StockLevel.none, '用完', AppTokens.error),
    ];
    return Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(AppTokens.rSm),
        border: Border.all(color: t.border),
      ),
      clipBehavior: Clip.antiAlias,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          for (final (lv, label, color) in opts)
            GestureDetector(
              behavior: HitTestBehavior.opaque,
              onTap: lv == level ? null : () => onChanged(lv),
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp8, vertical: AppTokens.sp6),
                color: lv == level ? color : null,
                child: Text(
                  label,
                  style: t.textStyles.sectionLabel.copyWith(
                    color: lv == level ? AppTokens.onPhoto : t.body,
                    fontWeight: lv == level ? FontWeight.w700 : FontWeight.w400,
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

/// 组尾「加载更多 · 还有 N 项」胶囊（加载中禁用）。
class LoadMorePill extends StatelessWidget {
  final int remain;
  final bool loading;
  final Color? color;
  final VoidCallback onTap;
  const LoadMorePill({super.key, required this.remain, required this.loading, required this.onTap, this.color});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final c = color ?? t.body;
    return Center(
      child: GestureDetector(
        onTap: loading ? null : onTap,
        child: Container(
          margin: const EdgeInsets.symmetric(vertical: AppTokens.sp8),
          padding: const EdgeInsets.symmetric(horizontal: 18, vertical: AppTokens.sp6),
          decoration: BoxDecoration(
            color: t.card,
            border: Border.all(color: color == null ? t.border : c.withAlpha(60)),
            borderRadius: BorderRadius.circular(AppTokens.rPill),
          ),
          child: Text(
            loading ? '加载中…' : '加载更多 · 还有 $remain 项',
            style: t.textStyles.caption.copyWith(color: loading ? t.body : c, fontWeight: FontWeight.w700),
          ),
        ),
      ),
    );
  }
}
