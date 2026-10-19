import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/app_theme.dart';
import '../../models/dish.dart';
import '../../widgets/dish_image.dart';
import '../../widgets/tone_badge.dart';

/// 「上次 3 天前 · 妈妈做的」。
String? lastCookedText(Dish d) {
  final at = d.lastCookedAt;
  if (at == null) return null;
  final days = DateTime.now().difference(at).inDays;
  final when = days <= 0 ? '今天' : days == 1 ? '昨天' : days < 30 ? '$days 天前' : '${at.month}/${at.day}';
  final who = (d.lastCookedBy ?? '').isEmpty ? '' : ' · ${d.lastCookedBy}做的';
  return '上次 $when$who';
}

/// 首推大图卡（原型 Recipes：第一道配大图 + 渐变 + 覆盖率徽章）。
class FeaturedDishCard extends StatelessWidget {
  final Dish dish;
  const FeaturedDishCard({super.key, required this.dish});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    final cov = dish.coverage;
    final meta = [
      if ((dish.cookTime ?? 0) > 0) '${dish.cookTime} 分钟',
      dish.cookedCount > 0 ? '做过 ${dish.cookedCount} 次' : '没做过',
    ].join(' · ');
    return GestureDetector(
      onTap: () => context.push('/dish/${dish.id}'),
      child: Container(
        margin: const EdgeInsets.only(bottom: AppTokens.sp12),
        height: 180,
        decoration: BoxDecoration(borderRadius: BorderRadius.circular(AppTokens.rLg), boxShadow: t.elevationMd),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(AppTokens.rLg),
          child: Stack(
            fit: StackFit.expand,
            children: [
              DishImage(url: dish.coverUrl, name: dish.name, radius: 0, original: true),
              const DecoratedBox(
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    stops: [0.35, 1],
                    colors: [Colors.transparent, AppTokens.photoScrim],
                  ),
                ),
              ),
              if (cov != null && cov.label.isNotEmpty)
                Positioned(
                  left: AppTokens.sp12,
                  top: AppTokens.sp12,
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp10, vertical: AppTokens.sp4),
                    decoration: BoxDecoration(color: t.card, borderRadius: BorderRadius.circular(AppTokens.rPill)),
                    child: Row(mainAxisSize: MainAxisSize.min, children: [
                      Container(
                        width: 7,
                        height: 7,
                        decoration: BoxDecoration(
                          color: cov.isFull ? AppTokens.success : AppTokens.warning,
                          shape: BoxShape.circle,
                        ),
                      ),
                      const SizedBox(width: AppTokens.sp6),
                      Text(cov.isFull ? '${cov.label} · 现在就能做' : cov.label,
                          style: ts.sectionLabel.copyWith(color: t.title)),
                    ]),
                  ),
                ),
              Positioned(
                left: AppTokens.sp16,
                right: AppTokens.sp16,
                bottom: AppTokens.sp12,
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text(dish.name, style: ts.h3.copyWith(color: AppTokens.onPhoto)),
                          const SizedBox(height: 2),
                          Text(meta, style: ts.caption.copyWith(color: AppTokens.onPhoto)),
                        ],
                      ),
                    ),
                    if (lastCookedText(dish) != null)
                      Text(lastCookedText(dish)!, style: ts.sectionLabel.copyWith(color: AppTokens.onPhoto)),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// 列表行：84px 菜品照片 + 菜名/做过次数 + 覆盖率/时长/标签徽章 + 上次谁做的。
class DishRowCard extends StatelessWidget {
  final Dish dish;
  /// 选择模式：非空时点击走此回调（加菜），否则默认进菜谱详情。
  final VoidCallback? onTapOverride;
  const DishRowCard({super.key, required this.dish, this.onTapOverride});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    final covBadge = ToneBadge.coverage(dish.coverage);
    final tag = [...dish.tagNames, ...dish.categoryNames].where((s) => s.isNotEmpty).take(1).toList();
    final sub = lastCookedText(dish) ?? (dish.sourceName?.isNotEmpty == true ? '来自${dish.sourceName}' : null);
    return GestureDetector(
      onTap: onTapOverride ?? () => context.push('/dish/${dish.id}'),
      child: Container(
        margin: const EdgeInsets.only(bottom: AppTokens.sp10),
        padding: const EdgeInsets.all(AppTokens.sp12),
        decoration: BoxDecoration(
          color: t.card,
          borderRadius: BorderRadius.circular(AppTokens.rLg),
          border: Border.all(color: t.border),
          boxShadow: t.elevationSm,
        ),
        child: Row(
          children: [
            DishImage.square(url: dish.coverUrl, name: dish.name, size: 84),
            const SizedBox(width: AppTokens.sp12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(dish.name,
                            maxLines: 1, overflow: TextOverflow.ellipsis, style: ts.cardTitle.copyWith(color: t.title)),
                      ),
                      Text(dish.cookedCount > 0 ? '做过 ${dish.cookedCount} 次' : '没做过', style: ts.caption),
                    ],
                  ),
                  const SizedBox(height: AppTokens.sp6),
                  Wrap(
                    spacing: AppTokens.sp6,
                    runSpacing: AppTokens.sp4,
                    children: [
                      if (covBadge != null) covBadge,
                      if ((dish.cookTime ?? 0) > 0) ToneBadge('${dish.cookTime} 分钟'),
                      for (final x in tag) ToneBadge(x),
                    ],
                  ),
                  if (sub != null) ...[
                    const SizedBox(height: AppTokens.sp6),
                    Text(sub, maxLines: 1, overflow: TextOverflow.ellipsis, style: ts.caption),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
