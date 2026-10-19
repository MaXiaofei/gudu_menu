import 'package:flutter/material.dart';

import '../core/app_theme.dart';
import 'dish_image.dart';

/// 满宽主图轮播（菜谱详情顶部）：页码 + 顶部渐变 + 浮动按钮。
///
/// [leading]/[actions] 叠在图上（如返回、分享、收藏，用 [HeroIconButton]）；
/// [onTapImage] 点图回调（如打开全屏查看器）。
class HeroGallery extends StatefulWidget {
  final List<String> urls;
  final String name;
  final double height;
  final Widget? leading;
  final List<Widget> actions;
  final ValueChanged<int>? onTapImage;

  const HeroGallery({
    super.key,
    required this.urls,
    required this.name,
    this.height = 280,
    this.leading,
    this.actions = const [],
    this.onTapImage,
  });

  @override
  State<HeroGallery> createState() => _HeroGalleryState();
}

class _HeroGalleryState extends State<HeroGallery> {
  int _index = 0;

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final urls = widget.urls;
    final top = MediaQuery.of(context).padding.top;
    return SizedBox(
      height: widget.height + top,
      child: Stack(
        fit: StackFit.expand,
        children: [
          if (urls.isEmpty)
            DishInitialPlaceholder(name: widget.name)
          else
            PageView.builder(
              itemCount: urls.length,
              onPageChanged: (i) => setState(() => _index = i),
              itemBuilder: (_, i) => GestureDetector(
                onTap: widget.onTapImage == null ? null : () => widget.onTapImage!(i),
                child: DishImage(
                  url: urls[i],
                  name: widget.name,
                  width: double.infinity,
                  height: widget.height + top,
                  radius: 0,
                  original: true,
                ),
              ),
            ),
          // 顶部渐变：保证状态栏与浮动按钮在任意照片上可读
          IgnorePointer(
            child: Align(
              alignment: Alignment.topCenter,
              child: Container(
                height: top + 96,
                decoration: const BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: [AppTokens.photoScrim, Colors.transparent],
                  ),
                ),
              ),
            ),
          ),
          Positioned(
            top: top + AppTokens.sp8,
            left: AppTokens.sp12,
            right: AppTokens.sp12,
            child: Row(
              children: [
                if (widget.leading != null) widget.leading!,
                const Spacer(),
                for (final a in widget.actions) ...[const SizedBox(width: AppTokens.sp8), a],
              ],
            ),
          ),
          if (urls.length > 1)
            Positioned(
              right: AppTokens.sp16,
              bottom: AppTokens.sp24,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
                decoration: BoxDecoration(
                  color: AppTokens.photoScrim,
                  borderRadius: BorderRadius.circular(AppTokens.rPill),
                ),
                child: Text('${_index + 1} / ${urls.length}',
                    style: t.textStyles.chip.copyWith(color: AppTokens.onPhoto)),
              ),
            ),
        ],
      ),
    );
  }
}

/// 叠在照片上的圆形图标按钮（白色半透明底）。
class HeroIconButton extends StatelessWidget {
  final IconData icon;
  final VoidCallback? onTap;
  final String tooltip;
  final Color? color;
  const HeroIconButton({super.key, required this.icon, required this.tooltip, this.onTap, this.color});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Semantics(
      button: true,
      label: tooltip,
      child: Material(
        color: t.card.withValues(alpha: 0.92),
        shape: const CircleBorder(),
        child: InkWell(
          customBorder: const CircleBorder(),
          onTap: onTap,
          child: SizedBox(
            width: 40,
            height: 40,
            child: Icon(icon, size: 20, color: color ?? t.title),
          ),
        ),
      ),
    );
  }
}
