import 'package:flutter/material.dart';

import '../core/app_theme.dart';
import '../core/feature_switch.dart';
import 'loading_empty.dart';

/// 咕嘟助手紧急关停：后端暂停服务时，用暂停提示盖住咕嘟页 / 对话页，而不是报错。
class AgentPausedGate extends StatelessWidget {
  final Widget child;

  /// 是否显示返回按钮（Tab 页不显示）。
  final bool showBack;

  const AgentPausedGate({super.key, required this.child, this.showBack = true});

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<bool>(
      valueListenable: FeatureSwitch.agentChat,
      builder: (context, available, _) {
        if (available) return child;
        final t = AppTokens.of(context);
        return Scaffold(
          key: const Key('agent-paused'),
          backgroundColor: t.bg,
          appBar: showBack ? AppBar(backgroundColor: t.bg, elevation: 0) : null,
          body: const SafeArea(
            child: Center(
              child: EmptyView(
                text: FeatureSwitch.agentMessage,
                subtitle: '我们正在处理一些内容，稍后再来看看',
                actionLabel: '再看看',
                // 重新读取开关：已恢复则回到原页面，仍在暂停保持提示
                onAction: FeatureSwitch.load,
              ),
            ),
          ),
        );
      },
    );
  }
}
