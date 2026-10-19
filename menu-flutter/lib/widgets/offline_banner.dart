import 'package:flutter/material.dart';

import '../core/app_theme.dart';
import '../core/connectivity.dart';

/// 离线顶部条（原型 States：顶部条，不挡操作）。
class OfflineBanner extends StatelessWidget {
  final String text;
  const OfflineBanner({super.key, this.text = '已离线，显示的是上次同步的数据'});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return ValueListenableBuilder<bool>(
      valueListenable: Connectivity.online,
      builder: (_, online, __) => AnimatedSize(
        duration: const Duration(milliseconds: 200),
        child: online
            ? const SizedBox(width: double.infinity)
            : Container(
                width: double.infinity,
                color: t.title,
                padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp16, vertical: AppTokens.sp8),
                child: Row(
                  children: [
                    Icon(Icons.wifi_off_rounded, size: 16, color: t.card),
                    const SizedBox(width: AppTokens.sp8),
                    Expanded(child: Text(text, style: t.textStyles.caption.copyWith(color: t.card))),
                  ],
                ),
              ),
      ),
    );
  }
}
