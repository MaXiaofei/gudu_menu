import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../core/app_theme.dart';
import '../stores/auth_store.dart';
import '../stores/member_store.dart';
import '../widgets/action_bar.dart';
import '../widgets/app_card.dart';

/// 「设置」页（首页右上角设置图标进入）：用户信息 + 设置项 + 退出登录。
class ProfilePage extends StatelessWidget {
  const ProfilePage({super.key});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final auth = context.watch<AuthStore>();
    final member = context.watch<MemberStore>();
    return Scaffold(
      backgroundColor: t.bg,
      // DESIGN.md §13：Tab 主页无标题（不放「设置」），顶部用 ActionBar。
      // 我的 tab 无操作，ActionBar() 不传 action → 返回 SizedBox.shrink。
      body: SafeArea(
        bottom: false,
        child: Column(
          children: [
            const ActionBar(),
            Expanded(
              child: ListView(
                padding: const EdgeInsets.fromLTRB(AppTokens.sp16, AppTokens.sp16, AppTokens.sp16, AppTokens.sp32),
                children: [
            // 用户头部卡
            AppCard(
              child: Row(
                children: [
                  Container(
                    width: 56,
                    height: 56,
                    decoration: BoxDecoration(
                      gradient: t.primaryGradient,
                      shape: BoxShape.circle,
                    ),
                    child: Icon(Icons.person, color: t.card),
                  ),
                  const SizedBox(width: AppTokens.sp12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(auth.nickname.isNotEmpty ? auth.nickname : '掌勺人',
                            style: t.textStyles.subtitle),
                        const SizedBox(height: AppTokens.sp4),
                        Text(
                          member.currentName.isNotEmpty
                              ? '当前就餐：${member.currentName}'
                              : '未选择就餐成员',
                          style: t.textStyles.sm.copyWith(color: t.caption),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: AppTokens.sp16),
            // 功能项
            AppCard(
              padding: EdgeInsets.zero,
              child: Column(
                children: [
                  _SettingTile(
                    icon: Icons.people_outline,
                    label: '家庭成员',
                    onTap: () {},
                  ),
                  const Divider(height: 1, indent: 56),
                  _SettingTile(
                    icon: Icons.star_outline,
                    label: '我的评价',
                    onTap: () => context.push('/my-reviews'),
                  ),
                  const Divider(height: 1, indent: 56),
                  _SettingTile(
                    icon: Icons.palette_outlined,
                    label: '主题外观',
                    onTap: () {},
                  ),
                  const Divider(height: 1, indent: 56),
                  _SettingTile(
                    icon: Icons.info_outline,
                    label: '关于小食单',
                    value: 'v1.0.0',
                    onTap: () {},
                  ),
                ],
              ),
            ),
            const SizedBox(height: AppTokens.sp16),
            // 退出登录
            AppCard(
              padding: EdgeInsets.zero,
              child: ListTile(
                leading: const Icon(Icons.logout,
                    color: AppTokens.error),
                title: const Text('退出登录',
                    style: TextStyle(color: AppTokens.error)),
                trailing: Icon(Icons.chevron_right,
                    color: t.caption),
                onTap: () => auth.logout(),
              ),
            ),
          ],
              ), // ListView
            ), // Expanded
          ], // Column children
        ), // Column
      ), // SafeArea
    );
  }
}

class _SettingTile extends StatelessWidget {
  final IconData icon;
  final String label;
  final String? value;
  final VoidCallback onTap;

  const _SettingTile({
    required this.icon,
    required this.label,
    this.value,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return ListTile(
        leading: Icon(icon, color: t.primary),
        title: Text(label, style: t.textStyles.body.copyWith(color: t.title)),
        trailing: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (value != null)
              Text(value!,
                  style: t.textStyles.sm.copyWith(color: t.caption)),
            Icon(Icons.chevron_right, color: t.caption),
          ],
        ),
        onTap: onTap,
      );
  }
}
