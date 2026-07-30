import 'package:flutter/material.dart';

import '../core/app_theme.dart';

/// 全屏图片查看器。
///
/// - 先快速展示缩略图，原图后台加载完成后无缝切换。
/// - 支持双指缩放 + 拖拽（InteractiveViewer）。
/// - 点击背景或左上角返回关闭。
class ImageViewer extends StatefulWidget {
  final String thumbnailUrl;
  final String originalUrl;

  const ImageViewer({
    super.key,
    required this.thumbnailUrl,
    required this.originalUrl,
  });

  @override
  State<ImageViewer> createState() => _ImageViewerState();
}

class _ImageViewerState extends State<ImageViewer> {
  ImageStream? _originalStream;
  bool _originalReady = false;

  @override
  void initState() {
    super.initState();
    // 预加载原图
    final originalProvider = NetworkImage(widget.originalUrl);
    _originalStream = originalProvider.resolve(ImageConfiguration.empty);
    _originalStream!.addListener(ImageStreamListener(
      (_, __) {
        if (mounted) setState(() => _originalReady = true);
      },
      onError: (_, __) {
        // 原图加载失败，保持缩略图
        if (mounted) setState(() => _originalReady = true);
      },
    ));
  }

  @override
  void dispose() {
    _originalStream = null;
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    // 全屏图片查看器：深色底（用 title token 的深色变体，非纯黑）。
    // 前景用 card token（双主题均为白），保证 token 链完整。
    final darkBg = t.title; // cream=#4A382A / matcha=#2E3520，非纯黑
    return Scaffold(
      backgroundColor: darkBg,
      appBar: AppBar(
        backgroundColor: darkBg,
        foregroundColor: t.card,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.close),
          onPressed: () => Navigator.of(context).pop(),
        ),
      ),
      body: GestureDetector(
        onTap: () => Navigator.of(context).pop(),
        child: Center(
          child: InteractiveViewer(
            minScale: 1.0,
            maxScale: 5.0,
            child: _originalReady
                ? Image.network(
                    widget.originalUrl,
                    fit: BoxFit.contain,
                    errorBuilder: (_, __, ___) => Image.network(
                      widget.thumbnailUrl,
                      fit: BoxFit.contain,
                    ),
                    loadingBuilder: (_, child, progress) {
                      if (progress == null) return child;
                      return Image.network(
                        widget.thumbnailUrl,
                        fit: BoxFit.contain,
                      );
                    },
                  )
                : Image.network(
                    widget.thumbnailUrl,
                    fit: BoxFit.contain,
                    loadingBuilder: (_, child, progress) {
                      if (progress == null) return child;
                      return Center(
                        child: _ImageViewSkeleton(t: t),
                      );
                    },
                  ),
          ),
        ),
      ),
    );
  }
}

/// 图片加载骨架屏（DESIGN.md §1：禁止 spinner）。
class _ImageViewSkeleton extends StatelessWidget {
  final AppTokens t;
  const _ImageViewSkeleton({required this.t});
  @override
  Widget build(BuildContext context) {
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: 0.3, end: 0.8),
      duration: const Duration(milliseconds: 1200),
      builder: (_, v, child) => Opacity(opacity: v, child: child),
      child: Container(
        width: 120,
        height: 120,
        decoration: BoxDecoration(
          color: t.card.withValues(alpha: 0.3),
          borderRadius: BorderRadius.circular(AppTokens.rMd),
        ),
        child: Icon(Icons.image_outlined, color: t.card.withValues(alpha: 0.5), size: 40),
      ),
    );
  }
}
