import 'package:flutter/material.dart';

import '../core/app_theme.dart';
import '../core/image_helper.dart';

/// 菜品图（原型「图片规范」：有图的位置一律有图，缺图/失败回退首字占位）。
///
/// - 默认加载缩略图（`/original/` → `/thumbnail/`），[original] 为 true 加载原图（大图位）
/// - 加载中显示 secondary 底色块（不转圈），失败或无 url 显示首字
/// - 圆角裁切；`BoxFit.cover` 不拉伸
///
/// ```dart
/// DishImage(url: dish.coverUrl, name: dish.name, size: 84);
/// DishImage(url: cover, name: name, width: double.infinity, height: 280, radius: 0, original: true);
/// ```
class DishImage extends StatelessWidget {
  final String? url;
  final String name;
  final double? width;
  final double? height;
  final double radius;
  final bool original;
  final Alignment alignment;

  const DishImage({
    super.key,
    required this.url,
    required this.name,
    this.width,
    this.height,
    this.radius = AppTokens.rMd,
    this.original = false,
    this.alignment = Alignment.center,
  });

  /// 正方形便捷构造。
  const DishImage.square({
    super.key,
    required this.url,
    required this.name,
    required double size,
    this.radius = AppTokens.rMd,
    this.original = false,
    this.alignment = Alignment.center,
  })  : width = size,
        height = size;

  @override
  Widget build(BuildContext context) {
    final u = url?.trim() ?? '';
    final child = u.isEmpty
        ? DishInitialPlaceholder(name: name, width: width, height: height)
        : Image.network(
            original ? ImageHelper.toAbsolute(u) : ImageHelper.resolve(u).thumbnail,
            width: width,
            height: height,
            fit: BoxFit.cover,
            alignment: alignment,
            gaplessPlayback: true,
            errorBuilder: (_, __, ___) =>
                DishInitialPlaceholder(name: name, width: width, height: height),
            frameBuilder: (ctx, img, frame, sync) {
              if (sync || frame != null) return img;
              return _LoadingBlock(width: width, height: height);
            },
          );
    return ClipRRect(
      borderRadius: BorderRadius.circular(radius),
      child: SizedBox(width: width, height: height, child: child),
    );
  }
}

class _LoadingBlock extends StatelessWidget {
  final double? width;
  final double? height;
  const _LoadingBlock({this.width, this.height});
  @override
  Widget build(BuildContext context) => Container(
        width: width,
        height: height,
        color: AppTokens.of(context).secondary,
      );
}

/// 首字占位（secondary 底 + 45% 标题色首字，字号随尺寸）。
class DishInitialPlaceholder extends StatelessWidget {
  final String name;
  final double? width;
  final double? height;
  const DishInitialPlaceholder({super.key, required this.name, this.width, this.height});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final n = name.trim();
    final initial = n.isNotEmpty ? n.characters.first : '菜';
    return LayoutBuilder(builder: (ctx, c) {
      final w = width ?? (c.maxWidth.isFinite ? c.maxWidth : 80);
      final h = height ?? (c.maxHeight.isFinite ? c.maxHeight : w);
      final side = w < h ? w : h;
      return Container(
        width: w,
        height: h,
        color: t.secondary,
        alignment: Alignment.center,
        child: Text(
          initial,
          style: t.textStyles.h2.copyWith(
            fontSize: (side * 0.38).clamp(12, 72).toDouble(),
            fontWeight: FontWeight.w700,
            color: t.title.withAlpha(115),
            height: 1,
          ),
        ),
      );
    });
  }
}

/// 2~4 张菜品图拼贴（食集封面）。第一张占左侧大块，其余竖排在右。
class DishCollage extends StatelessWidget {
  final List<String?> urls;
  final List<String> names;
  final double height;
  final double radius;
  const DishCollage({
    super.key,
    required this.urls,
    required this.names,
    this.height = 150,
    this.radius = AppTokens.rMd,
  });

  @override
  Widget build(BuildContext context) {
    final n = urls.length;
    String nameAt(int i) => i < names.length ? names[i] : '';
    if (n <= 1) {
      return DishImage(
          url: n == 1 ? urls[0] : null, name: nameAt(0), height: height, width: double.infinity, radius: radius);
    }
    final rest = urls.sublist(1, n > 3 ? 3 : n);
    return ClipRRect(
      borderRadius: BorderRadius.circular(radius),
      child: SizedBox(
        height: height,
        child: Row(
          children: [
            Expanded(flex: 3, child: DishImage(url: urls[0], name: nameAt(0), height: height, radius: 0)),
            const SizedBox(width: 3),
            Expanded(
              flex: 2,
              child: Column(
                children: [
                  for (var i = 0; i < rest.length; i++) ...[
                    if (i > 0) const SizedBox(height: 3),
                    Expanded(child: DishImage(url: rest[i], name: nameAt(i + 1), width: double.infinity, radius: 0)),
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

/// 叠放的小圆菜品图（食集「接下来」行）。
class DishCircleStack extends StatelessWidget {
  final List<String?> urls;
  final List<String> names;
  final double size;
  final int max;
  const DishCircleStack({super.key, required this.urls, required this.names, this.size = 36, this.max = 3});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final shown = urls.length > max ? max : urls.length;
    if (shown == 0) return const SizedBox.shrink();
    final overlap = size * 0.32;
    return SizedBox(
      width: size + (shown - 1) * (size - overlap),
      height: size,
      child: Stack(
        children: [
          for (var i = 0; i < shown; i++)
            Positioned(
              left: i * (size - overlap),
              child: Container(
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  border: Border.all(color: t.card, width: 2),
                ),
                child: DishImage.square(
                    url: urls[i], name: i < names.length ? names[i] : '', size: size - 4, radius: size),
              ),
            ),
        ],
      ),
    );
  }
}
