import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../core/ai_label.dart';
import '../core/app_theme.dart';
import 'tone_badge.dart';

/// AI 生成标识（A.4）：「AI 生成」徽章（复用 [ToneBadge] info 语气）；
/// [disclaimer] 为 true 时变成浅蓝提示条并接上「仅供参考，不能替代医生或营养师建议」；
/// 传 [source] 时右侧给「反馈」入口（复用意见反馈页，带来源标记）。
class AiBadge extends StatelessWidget {
  const AiBadge({super.key, this.disclaimer = false, this.source, this.feedbackRef});

  final bool disclaimer;
  final AiSource? source;
  final Object? feedbackRef;

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    const chip = ToneBadge(kAiLabel, key: Key('ai-badge-chip'), tone: BadgeTone.info, icon: Icons.auto_awesome_outlined);
    final feedback = source == null
        ? null
        : InkWell(
            key: const Key('ai-feedback'),
            borderRadius: BorderRadius.circular(AppTokens.rXs),
            onTap: () => context.push(aiFeedbackLocation(source!, feedbackRef)),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp4, vertical: AppTokens.sp2),
              child: Text('反馈',
                  style: ts.sectionLabel.copyWith(color: t.caption, decoration: TextDecoration.underline)),
            ),
          );
    if (!disclaimer) {
      return Row(mainAxisSize: MainAxisSize.min, children: [
        chip,
        if (feedback != null) ...[const SizedBox(width: AppTokens.sp6), feedback],
      ]);
    }
    return Container(
      key: const Key('ai-disclaimer'),
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp10, vertical: AppTokens.sp8),
      decoration: BoxDecoration(
        color: AppTokens.infoBg,
        borderRadius: BorderRadius.circular(AppTokens.rSm),
      ),
      child: Row(children: [
        chip,
        const SizedBox(width: AppTokens.sp6),
        Expanded(child: Text(kAiDisclaimerTail, style: ts.caption.copyWith(color: AppTokens.infoText))),
        if (feedback != null) ...[const SizedBox(width: AppTokens.sp6), feedback],
      ]),
    );
  }
}

/// 咕嘟助手首屏的 AI 身份说明（「AI 生成」徽章 + 我是 AI 助手…）。
class AiAssistantIntro extends StatelessWidget {
  const AiAssistantIntro({super.key});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Row(key: const Key('ai-intro'), children: [
      const AiBadge(),
      const SizedBox(width: AppTokens.sp6),
      Expanded(child: Text(kAiAssistantIntro, style: t.textStyles.caption.copyWith(color: t.caption))),
    ]);
  }
}
