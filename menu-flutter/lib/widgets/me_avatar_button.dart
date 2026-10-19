import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../core/app_theme.dart';
import '../stores/auth_store.dart';
import '../stores/member_store.dart';
import 'member_avatar.dart';

/// 主 Tab 右上角「我」的头像（导航改版：「我的」不再占 Tab，从这里进入）。
///
/// 头像取当前就餐成员（有照片显示照片，否则默认插画）；没有 Provider 时退回昵称首字头像。
class MeAvatarButton extends StatelessWidget {
  final double size;
  const MeAvatarButton({super.key, this.size = 36});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    String name = '我';
    String? avatar;
    try {
      final auth = context.watch<AuthStore>();
      if (auth.nickname.isNotEmpty) name = auth.nickname;
    } catch (_) {}
    try {
      final ms = context.watch<MemberStore>();
      final i = ms.members.indexWhere((m) => m.id == ms.currentId);
      if (i >= 0) {
        avatar = ms.members[i].avatarUrl;
        if (name == '我') name = ms.members[i].name;
      }
    } catch (_) {}
    return Semantics(
      button: true,
      label: '我的',
      child: InkWell(
        key: const Key('me-avatar'),
        customBorder: const CircleBorder(),
        onTap: () => context.push('/profile'),
        child: Padding(
          padding: const EdgeInsets.all(AppTokens.sp2),
          child: MemberAvatar(name: name, avatarUrl: avatar, size: size, ringColor: t.card),
        ),
      ),
    );
  }
}
