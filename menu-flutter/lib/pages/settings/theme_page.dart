import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/app_theme.dart';
import '../../core/theme_controller.dart';
import '../../widgets/action_bar.dart';
import '../../widgets/app_card.dart';
import '../../widgets/ingredient_icon.dart';
import '../../widgets/tone_badge.dart';

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
                  Text('选一个你喜欢的配色，全 App 立即生效，下次打开保持', style: t.textStyles.caption),
                  const SizedBox(height: AppTokens.sp12),
                  const _Preview(),
                  const SizedBox(height: AppTokens.sp16),
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
      borderColor: selected ? t.primaryDeep : null,
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
                Text(sub, style: t.textStyles.caption),
              ],
            ),
          ),
          Icon(
            selected ? Icons.check_circle : Icons.radio_button_unchecked,
            color: selected ? t.primaryDeep : t.border,
            size: 20,
          ),
        ],
      ),
    );
  }
}

/// 预览卡（原型 Theme）：用当前皮肤渲染一张菜谱卡 + 主按钮 + 底栏文字，切换即可看到效果。
class _Preview extends StatelessWidget {
  const _Preview();

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    return AppCard(
      radius: AppTokens.rXl,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text('预览', style: ts.sectionLabel.copyWith(letterSpacing: 1)),
          const SizedBox(height: AppTokens.sp10),
          Container(
            padding: const EdgeInsets.all(AppTokens.sp12),
            decoration: BoxDecoration(
              color: t.bg,
              border: Border.all(color: t.border),
              borderRadius: BorderRadius.circular(AppTokens.rLg),
            ),
            child: Row(children: [
              const IngredientIcon(name: '虾仁', iconKey: 'shrimp', size: 56),
              const SizedBox(width: AppTokens.sp12),
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text('滑蛋虾仁', style: ts.cardTitle.copyWith(color: t.title)),
                  const SizedBox(height: AppTokens.sp6),
                  const Wrap(spacing: AppTokens.sp6, children: [
                    ToneBadge('家里够 3/3 样', tone: BadgeTone.success),
                    ToneBadge('10 分钟'),
                  ]),
                ]),
              ),
            ]),
          ),
          const SizedBox(height: AppTokens.sp12),
          ElevatedButton(onPressed: () {}, child: const Text('现在就做')),
          const SizedBox(height: AppTokens.sp12),
          Container(
            padding: const EdgeInsets.only(top: AppTokens.sp10),
            decoration: BoxDecoration(border: Border(top: BorderSide(color: t.border))),
            child: Row(children: [
              for (final (i, label) in const [(0, '菜谱'), (1, '食集'), (2, '智荐'), (3, '我家余量'), (4, '我的')])
                Expanded(
                  child: Text(label,
                      textAlign: TextAlign.center,
                      style: ts.chip.copyWith(color: i == 0 ? t.primaryDeep : t.body)),
                ),
            ]),
          ),
        ],
      ),
    );
  }
}
