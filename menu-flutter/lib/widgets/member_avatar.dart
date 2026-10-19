import 'package:flutter/material.dart';

import '../core/app_theme.dart';
import '../core/image_helper.dart';

/// 成员头像（原型：成员、家庭、点菜者都显示头像）。
///
/// 有 [avatarUrl] 显示照片；否则用打包的默认插画头像（按名字稳定挑选，
/// 宝宝档案用宝宝头像），不再用首字。
class MemberAvatar extends StatelessWidget {
  final String name;
  final String? avatarUrl;
  final double size;
  final bool baby;
  final Color? ringColor;

  const MemberAvatar({
    super.key,
    required this.name,
    this.avatarUrl,
    this.size = 40,
    this.baby = false,
    this.ringColor,
  });

  static const _adults = ['xiaowang', 'linlin', 'mama', 'baba', 'laowang', 'ajie'];

  /// 名字 → 默认插画 key（稳定：同名同图）。
  static String defaultKey(String name, {bool baby = false}) {
    if (baby) return 'duoduo';
    const known = {
      '小王': 'xiaowang', '我': 'xiaowang', '林林': 'linlin', '妈妈': 'mama',
      '朵朵': 'duoduo', '爸爸': 'baba', '老王': 'laowang', '阿杰': 'ajie',
    };
    final k = known[name.trim()];
    if (k != null) return k;
    var h = 0;
    for (final c in name.codeUnits) {
      h = (h * 31 + c) & 0x7fffffff;
    }
    return _adults[h % _adults.length];
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final fallback = Image.asset(
      'assets/avatar/${defaultKey(name, baby: baby)}.png',
      width: size,
      height: size,
      fit: BoxFit.cover,
    );
    final u = avatarUrl?.trim() ?? '';
    final img = u.isEmpty
        ? fallback
        : Image.network(
            ImageHelper.resolve(u).thumbnail,
            width: size,
            height: size,
            fit: BoxFit.cover,
            errorBuilder: (_, __, ___) => fallback,
          );
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: t.secondary,
        border: ringColor == null ? null : Border.all(color: ringColor!, width: 2),
      ),
      child: ClipOval(child: img),
    );
  }
}

/// 头像叠放：最多 [max] 个，超出显示「+N」；白描边叠压。
class AvatarStack extends StatelessWidget {
  final List<({String name, String? avatarUrl})> people;
  final double size;
  final int max;

  const AvatarStack({super.key, required this.people, this.size = 28, this.max = 4});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final shown = people.length > max ? max : people.length;
    final extra = people.length - shown;
    final count = shown + (extra > 0 ? 1 : 0);
    if (count == 0) return const SizedBox.shrink();
    final step = size * 0.68;
    return SizedBox(
      width: size + (count - 1) * step,
      height: size,
      child: Stack(
        children: [
          for (var i = 0; i < shown; i++)
            Positioned(
              left: i * step,
              child: MemberAvatar(
                name: people[i].name,
                avatarUrl: people[i].avatarUrl,
                size: size,
                ringColor: t.card,
              ),
            ),
          if (extra > 0)
            Positioned(
              left: shown * step,
              child: Container(
                width: size,
                height: size,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: t.secondary,
                  border: Border.all(color: t.card, width: 2),
                ),
                alignment: Alignment.center,
                child: Text('+$extra', style: t.textStyles.chip.copyWith(color: t.title)),
              ),
            ),
        ],
      ),
    );
  }
}
