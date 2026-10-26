import 'package:flutter/material.dart';

import '../core/app_theme.dart';
import '../core/community_status.dart';
import 'loading_empty.dart';

/// 厨房圈紧急关停（A.12）：后端返回「厨房圈暂停服务」时，用暂停提示盖住厨房圈页面，而不是报错。
class CommunityPausedGate extends StatelessWidget {
  final Widget child;

  /// 是否显示返回按钮（Tab 页不显示）。
  final bool showBack;

  const CommunityPausedGate({super.key, required this.child, this.showBack = true});

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<bool>(
      valueListenable: CommunityStatus.suspended,
      builder: (context, paused, _) {
        if (!paused) return child;
        final t = AppTokens.of(context);
        return Scaffold(
          key: const Key('community-paused'),
          backgroundColor: t.bg,
          appBar: showBack ? AppBar(backgroundColor: t.bg, elevation: 0) : null,
          body: SafeArea(
            child: Center(
              child: EmptyView(
                text: CommunityStatus.message,
                subtitle: '我们正在处理一些内容，稍后再来看看',
                actionLabel: '再看看',
                // 重新展示页面并请求一次：仍在暂停会再次回到本提示
                onAction: () => CommunityStatus.suspended.value = false,
              ),
            ),
          ),
        );
      },
    );
  }
}
