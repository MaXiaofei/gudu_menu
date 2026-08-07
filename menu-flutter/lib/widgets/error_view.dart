import 'package:flutter/material.dart';

import '../core/app_theme.dart';

/// 错误态（DESIGN.md §14.1：空 ≠ 错，错误必须有重试入口）。
///
/// 替换 5 处错误态（此前用 `EmptyView` 顶替，语义错位；menu detail 还自造了
/// `_RetryView`）。错误态与空态语义不同：错误必须有重试入口，描述性文案。
///
/// 布局（克制感对齐 `EmptyView`，不花哨）：
/// - 居中 Column
/// - 灰色图标 `Icons.cloud_off_outlined`（`t.caption.withAlpha(120)`，size 40）
/// - 错误文案（`t.textStyles.sm.copyWith(color: t.caption)`，默认「加载失败」）
/// - 若提供 [onRetry]，显示 `OutlinedButton`「重试」（主色边框 / 主色文字）
///
/// 用法：
/// ```dart
/// ErrorView(text: '加载失败', onRetry: () => vm.reload());
/// ```
class ErrorView extends StatelessWidget {
  /// 错误描述文案。
  final String text;

  /// 可选重试回调；为 null 时不显示重试按钮。
  final VoidCallback? onRetry;

  const ErrorView({
    super.key,
    this.text = '加载失败',
    this.onRetry,
  });

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(AppTokens.sp32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              Icons.cloud_off_outlined,
              color: t.caption.withAlpha(120),
              size: 40,
            ),
            const SizedBox(height: AppTokens.sp12),
            Text(
              text,
              textAlign: TextAlign.center,
              style: t.textStyles.sm.copyWith(color: t.caption),
            ),
            if (onRetry != null) ...[
              const SizedBox(height: AppTokens.sp16),
              OutlinedButton(
                onPressed: onRetry,
                style: OutlinedButton.styleFrom(
                  foregroundColor: t.primary,
                  side: BorderSide(color: t.primary, width: 1),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(AppTokens.rMd),
                  ),
                ),
                child: const Text('重试'),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
