import 'package:flutter/material.dart';

import '../core/app_theme.dart';
import '../core/ingredient_icons.dart';

/// 食材插画（原型：余量、入库、备菜、采购、做完确认的每一行食材）。
///
/// 解析顺序见 [IngredientIcons.resolve]；都没有时退首字。
/// [depleted] 为 true（余量「用完」）时去色 + 半透明。
class IngredientIcon extends StatelessWidget {
  final String name;
  final String? iconKey;
  final String? category;
  final double size;
  final bool depleted;
  final Color? background;

  const IngredientIcon({
    super.key,
    required this.name,
    this.iconKey,
    this.category,
    this.size = 44,
    this.depleted = false,
    this.background,
  });

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final key = IngredientIcons.resolve(iconKey: iconKey, name: name, category: category);
    final n = name.trim();
    Widget inner = key != null
        ? Image.asset(
            IngredientIcons.asset(key),
            width: size * 0.72,
            height: size * 0.72,
            errorBuilder: (_, __, ___) => _initial(t, n),
          )
        : _initial(t, n);
    if (depleted) {
      inner = Opacity(
        opacity: 0.45,
        child: ColorFiltered(
          colorFilter: const ColorFilter.matrix(<double>[
            0.33, 0.33, 0.33, 0, 0, //
            0.33, 0.33, 0.33, 0, 0, //
            0.33, 0.33, 0.33, 0, 0, //
            0, 0, 0, 1, 0,
          ]),
          child: inner,
        ),
      );
    }
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: background ?? t.secondary,
        borderRadius: BorderRadius.circular(size * 0.28),
      ),
      alignment: Alignment.center,
      child: inner,
    );
  }

  Widget _initial(AppTokens t, String n) => Text(
        n.isNotEmpty ? n.characters.first : '料',
        style: t.textStyles.lg.copyWith(
          fontSize: (size * 0.4).clamp(10, 40).toDouble(),
          color: t.title.withAlpha(140),
          height: 1,
        ),
      );
}
