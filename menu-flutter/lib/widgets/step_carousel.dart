import 'package:flutter/material.dart';

import '../core/app_theme.dart';
import 'dish_image.dart';

/// 横滑步骤卡（菜谱详情「步骤」）：步骤图 4:3 + STEP n + 文字；无图时为纯文字卡。
class StepCarousel extends StatelessWidget {
  final List<({String text, String? image})> steps;
  final String dishName;
  final double cardWidth;
  final ValueChanged<int>? onTapImage;

  const StepCarousel({
    super.key,
    required this.steps,
    required this.dishName,
    this.cardWidth = 228,
    this.onTapImage,
  });

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    final anyImage = steps.any((s) => (s.image ?? '').isNotEmpty);
    final imgH = cardWidth * 0.75;
    return SizedBox(
      height: (anyImage ? imgH : 0) + 132,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp16),
        itemCount: steps.length,
        separatorBuilder: (_, __) => const SizedBox(width: AppTokens.sp12),
        itemBuilder: (_, i) {
          final s = steps[i];
          final hasImg = (s.image ?? '').isNotEmpty;
          return Container(
            width: cardWidth,
            decoration: BoxDecoration(
              color: t.card,
              borderRadius: BorderRadius.circular(AppTokens.rLg),
              border: Border.all(color: t.border),
              boxShadow: t.elevationSm,
            ),
            clipBehavior: Clip.antiAlias,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (anyImage)
                  GestureDetector(
                    onTap: hasImg && onTapImage != null ? () => onTapImage!(i) : null,
                    child: hasImg
                        ? DishImage(url: s.image, name: dishName, width: cardWidth, height: imgH, radius: 0)
                        : Container(
                            width: cardWidth,
                            height: imgH,
                            color: t.secondary,
                            alignment: Alignment.center,
                            child: Text('${i + 1}', style: ts.h1.copyWith(color: t.title.withAlpha(90))),
                          ),
                  ),
                Padding(
                  padding: const EdgeInsets.fromLTRB(AppTokens.sp12, AppTokens.sp10, AppTokens.sp12, AppTokens.sp12),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('STEP ${i + 1}', style: ts.sectionLabel.copyWith(color: t.primaryDeep, letterSpacing: 1)),
                      const SizedBox(height: AppTokens.sp4),
                      Text(s.text, maxLines: 4, overflow: TextOverflow.ellipsis, style: ts.body.copyWith(color: t.title, height: 1.45)),
                    ],
                  ),
                ),
              ],
            ),
          );
        },
      ),
    );
  }
}
