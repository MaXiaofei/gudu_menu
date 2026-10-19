import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../core/app_theme.dart';
import '../core/ingredient_icons.dart';

/// 空态 / 错误态插画：浅色圆底 + 几个错落的食材插画（原型 States，不只有文字）。
///
/// [keys] 食材插画 key（见 docs/design/assets.json）；[badge] 右上角小图标（如错误感叹号）。
class StateIllustration extends StatelessWidget {
  final List<String> keys;
  final IconData? badge;
  final Color? badgeColor;
  final double size;
  final bool faded;

  const StateIllustration({
    super.key,
    this.keys = const ['tomato', 'egg', 'broccoli'],
    this.badge,
    this.badgeColor,
    this.size = 128,
    this.faded = false,
  });

  /// 空冰箱（余量空态）。
  const StateIllustration.fridge({super.key})
      : keys = const ['milk', 'tomato', 'egg'],
        badge = Icons.kitchen_outlined,
        badgeColor = null,
        size = 128,
        faded = true;

  /// 打翻的碗（错误态）。
  const StateIllustration.error({super.key})
      : keys = const ['rice', 'noodle'],
        badge = Icons.priority_high_rounded,
        badgeColor = AppTokens.error,
        size = 128,
        faded = false;

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    // 三个位置：左下、中上、右下（相对 size）
    const spots = [(-0.26, 0.12, -0.18, 0.42), (0.0, -0.14, 0.08, 0.5), (0.27, 0.14, 0.22, 0.4)];
    final ks = keys.where(IngredientIcons.keys.contains).take(3).toList();
    return SizedBox(
      width: size * 1.3,
      height: size,
      child: Stack(
        alignment: Alignment.center,
        children: [
          Container(
            width: size * 0.92,
            height: size * 0.92,
            decoration: BoxDecoration(color: t.secondary, shape: BoxShape.circle),
          ),
          for (var i = 0; i < ks.length; i++)
            Transform.translate(
              offset: Offset(spots[i].$1 * size, spots[i].$2 * size),
              child: Transform.rotate(
                angle: spots[i].$3 * math.pi / 2,
                child: Opacity(
                  opacity: faded ? 0.55 : 1,
                  child: Image.asset(IngredientIcons.asset(ks[i]),
                      width: size * spots[i].$4, height: size * spots[i].$4),
                ),
              ),
            ),
          if (badge != null)
            Positioned(
              right: size * 0.14,
              top: size * 0.04,
              child: Container(
                width: size * 0.3,
                height: size * 0.3,
                decoration: BoxDecoration(
                  color: badgeColor ?? t.card,
                  shape: BoxShape.circle,
                  border: Border.all(color: t.card, width: 3),
                  boxShadow: t.elevationSm,
                ),
                child: Icon(badge, size: size * 0.17, color: badgeColor == null ? t.primaryDeep : AppTokens.onPhoto),
              ),
            ),
        ],
      ),
    );
  }
}
