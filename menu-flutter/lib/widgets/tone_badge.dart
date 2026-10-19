import 'package:flutter/material.dart';

import '../core/app_theme.dart';
import '../models/dish.dart' show Coverage;

/// 徽章语气（彩色浅底 + 深色文字档，WCAG ≥4.5）。
enum BadgeTone { success, warning, error, info, neutral, solidError }

/// 小徽章（覆盖率、到期、标签）。原型：圆角 4、11px。
class ToneBadge extends StatelessWidget {
  final String label;
  final BadgeTone tone;
  final IconData? icon;
  const ToneBadge(this.label, {super.key, this.tone = BadgeTone.neutral, this.icon});

  /// 「家里够 X/Y 样」：全够绿；缺 1–2 样黄（点名缺的那样）；更少红。total 为 0 不显示。
  static Widget? coverage(Coverage? c) {
    if (c == null || c.total <= 0) return null;
    final missing = c.total - c.enough;
    if (missing <= 0) return ToneBadge(c.label, tone: BadgeTone.success);
    if (missing <= 2) {
      final name = c.missingNames.isNotEmpty ? ' · 缺${c.missingNames.first}' : '';
      return ToneBadge('${c.label}$name', tone: BadgeTone.warning);
    }
    return ToneBadge(c.label, tone: BadgeTone.error);
  }

  /// 到期徽章：今天/明天/已过期 实心红；3 天内浅黄；更久不显示。
  static Widget? expiry(int? daysLeft) {
    if (daysLeft == null || daysLeft > 3) return null;
    if (daysLeft < 0) return const ToneBadge('已过期', tone: BadgeTone.solidError);
    if (daysLeft == 0) return const ToneBadge('今天到期', tone: BadgeTone.solidError);
    if (daysLeft == 1) return const ToneBadge('明天到期', tone: BadgeTone.solidError);
    return ToneBadge('剩 $daysLeft 天', tone: BadgeTone.warning);
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final (bg, fg, border) = switch (tone) {
      BadgeTone.success => (AppTokens.successBg, AppTokens.successText, null),
      BadgeTone.warning => (AppTokens.warningBg, AppTokens.warningText, null),
      BadgeTone.error => (AppTokens.errorBg, AppTokens.errorText, null),
      BadgeTone.info => (AppTokens.infoBg, AppTokens.infoText, null),
      BadgeTone.solidError => (AppTokens.error, AppTokens.onPhoto, null),
      BadgeTone.neutral => (t.bg, t.body, t.border),
    };
    final weight = tone == BadgeTone.neutral ? FontWeight.w400 : FontWeight.w700;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp8, vertical: 2),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(AppTokens.rXs),
        border: border == null ? null : Border.all(color: border),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[Icon(icon, size: 12, color: fg), const SizedBox(width: 3)],
          Flexible(
            child: Text(label,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: t.textStyles.sectionLabel.copyWith(color: fg, fontWeight: weight)),
          ),
        ],
      ),
    );
  }
}
