import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/app_theme.dart';
import '../../core/constants.dart';
import '../../widgets/action_bar.dart';
import '../../widgets/app_card.dart';
import 'legal_page.dart';

/// 关于小食单（G10/G14）：版本号 + 用户协议 / 隐私政策 / 意见反馈入口。
class AboutPage extends StatelessWidget {
  const AboutPage({super.key});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    return Scaffold(
      backgroundColor: t.bg,
      body: SafeArea(
        bottom: false,
        child: Column(
          children: [
            const BackHeader(),
            Expanded(
              child: ListView(
                padding: const EdgeInsets.all(AppTokens.sp16),
                children: [
                  const SizedBox(height: AppTokens.sp16),
                  Center(
                    child: Container(
                      width: 72,
                      height: 72,
                      decoration: BoxDecoration(
                        gradient: t.primaryGradient,
                        borderRadius: BorderRadius.circular(AppTokens.rXl),
                      ),
                      alignment: Alignment.center,
                      child: Text('咕', style: ts.h2.copyWith(color: t.card)),
                    ),
                  ),
                  const SizedBox(height: AppTokens.sp12),
                  Text('咕嘟小食单', textAlign: TextAlign.center, style: ts.subtitle),
                  const SizedBox(height: AppTokens.sp4),
                  Text('版本 v${AppConstants.appVersion}',
                      textAlign: TextAlign.center,
                      style: ts.caption.copyWith(color: t.caption)),
                  const SizedBox(height: AppTokens.sp24),
                  AppCard(
                    padding: EdgeInsets.zero,
                    child: Column(
                      children: [
                        _LinkTile(
                          label: '用户协议',
                          onTap: () => context.push(LegalKind.terms.path),
                        ),
                        const Divider(height: 1, indent: AppTokens.sp16),
                        _LinkTile(
                          label: '隐私政策',
                          onTap: () => context.push(LegalKind.privacy.path),
                        ),
                        const Divider(height: 1, indent: AppTokens.sp16),
                        _LinkTile(
                          label: '意见反馈',
                          onTap: () => context.push('/feedback'),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _LinkTile extends StatelessWidget {
  final String label;
  final VoidCallback onTap;
  const _LinkTile({required this.label, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return ListTile(
      title: Text(label, style: t.textStyles.body.copyWith(color: t.title)),
      trailing: Icon(Icons.chevron_right, color: t.caption),
      onTap: onTap,
    );
  }
}
