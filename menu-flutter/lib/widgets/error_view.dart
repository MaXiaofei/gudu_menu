import 'package:flutter/material.dart';

import '../core/app_theme.dart';
import '../core/error_text.dart';
import 'illustration.dart';

/// 错误态（原型 States：插画 + 说明 + 重试 + 报错编号；空 ≠ 错）。
///
/// [error] 传入捕获的异常时显示报错编号（X-Trace-Id 前 8 位），供意见反馈附带。
///
/// ```dart
/// ErrorView(text: '加载失败', onRetry: _load, error: e);
/// ```
class ErrorView extends StatelessWidget {
  final String text;
  final String hint;
  final VoidCallback? onRetry;
  final Object? error;
  final bool compact;

  const ErrorView({
    super.key,
    this.text = '加载失败',
    this.hint = '网络不太好，或者服务器开小差了',
    this.onRetry,
    this.error,
    this.compact = false,
  });

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    final id = errorIdOf(error);
    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(AppTokens.sp32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (!compact) ...[
              const StateIllustration.error(),
              const SizedBox(height: AppTokens.sp16),
            ],
            Text(text, textAlign: TextAlign.center, style: ts.subtitle),
            const SizedBox(height: AppTokens.sp6),
            Text(hint, textAlign: TextAlign.center, style: ts.caption),
            if (onRetry != null) ...[
              const SizedBox(height: AppTokens.sp16),
              SizedBox(
                width: 160,
                child: ElevatedButton(onPressed: onRetry, child: const Text('重试')),
              ),
            ],
            if (id != null) ...[
              const SizedBox(height: AppTokens.sp12),
              SelectableText('报错编号 $id · 反馈时附上它', style: ts.sectionLabel.copyWith(fontWeight: FontWeight.w400)),
            ],
          ],
        ),
      ),
    );
  }
}
