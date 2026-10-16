import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/app_theme.dart';
import '../../core/theme_controller.dart';
import '../../widgets/action_bar.dart';
import '../../widgets/app_card.dart';

/// 主题外观（G10）：列出 tokens.json 注册的全部皮肤，选中即切换并持久化（ThemeController）。
class ThemePage extends StatelessWidget {
  const ThemePage({super.key});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final tc = context.watch<ThemeController>();
    return Scaffold(
      backgroundColor: t.bg,
      body: SafeArea(
        bottom: false,
        child: Column(
          children: [
            const BackHeader(title: '主题外观'),
            Expanded(
              child: ListView(
                padding: const EdgeInsets.all(AppTokens.sp16),
                children: [
                  Text('选一个你喜欢的配色，全 App 立即生效',
                      style: t.textStyles.caption.copyWith(color: t.caption)),
                  const SizedBox(height: AppTokens.sp12),
                  for (final s in tc.skins) ...[
                    _ThemeOption(
                      label: s.label,
                      sub: s.tagline,
                      preview: s.tokens,
                      selected: tc.skinKey == s.key,
                      onTap: () => tc.setSkin(s.key),
                    ),
                    const SizedBox(height: AppTokens.sp12),
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

class _ThemeOption extends StatelessWidget {
  final String label;
  final String sub;
  final AppTokens preview;
  final bool selected;
  final VoidCallback onTap;

  const _ThemeOption({
    required this.label,
    required this.sub,
    required this.preview,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return AppCard.outlined(
      onTap: onTap,
      borderColor: selected ? t.primary : null,
      borderWidth: selected ? 2 : 1,
      child: Row(
        children: [
          // 色板预览：主色 / 浅主色 / 背景 / 标题色
          for (final c in [preview.primary, preview.primarySoft, preview.bg, preview.title])
            Container(
              width: 22,
              height: 22,
              margin: const EdgeInsets.only(right: AppTokens.sp4),
              decoration: BoxDecoration(
                color: c,
                shape: BoxShape.circle,
                border: Border.all(color: t.border),
              ),
            ),
          const SizedBox(width: AppTokens.sp8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(label, style: t.textStyles.cardTitle.copyWith(color: t.title)),
                const SizedBox(height: AppTokens.sp2),
                Text(sub, style: t.textStyles.caption.copyWith(color: t.caption)),
              ],
            ),
          ),
          Icon(
            selected ? Icons.check_circle : Icons.radio_button_unchecked,
            color: selected ? t.primary : t.caption,
            size: 20,
          ),
        ],
      ),
    );
  }
}
