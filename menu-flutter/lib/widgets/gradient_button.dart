import 'package:flutter/material.dart';

import '../core/app_theme.dart';

/// 暖橙渐变主按钮（登录、主推卡片 CTA 等用）。
class GradientButton extends StatelessWidget {
  final String label;
  final VoidCallback? onPressed;
  final IconData? icon;

  const GradientButton({
    super.key,
    required this.label,
    this.onPressed,
    this.icon,
  });

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final radius = BorderRadius.circular(AppTokens.rMd);
    return Opacity(
        opacity: onPressed == null ? 0.5 : 1,
        child: Container(
          decoration: BoxDecoration(
            gradient: t.primaryGradient,
            borderRadius: radius,
            boxShadow: [
              BoxShadow(
                color: t.shadowMd,
                blurRadius: 12,
                offset: const Offset(0, 6),
              ),
            ],
          ),
          child: Material(
            color: Colors.transparent,
            child: InkWell(
              onTap: onPressed,
              borderRadius: radius,
              hoverColor: t.primaryDeep.withValues(alpha: 0.15),
              splashColor: t.primaryDeep.withValues(alpha: 0.25),
              child: Container(
                padding: const EdgeInsets.symmetric(vertical: 16),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    if (icon != null) ...[
                      Icon(icon, color: t.card, size: 20),
                      const SizedBox(width: 8),
                    ],
                    Text(label,
                        style: t.textStyles.pageTitle.copyWith(
                          color: t.card,
                        )),
                  ],
                ),
              ),
            ),
          ),
        ),
      );
  }
}
