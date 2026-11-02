import 'package:flutter/material.dart';

import '../core/feature_switch.dart';
import 'feature_paused_gate.dart';

/// 咕嘟助手紧急关停：后端暂停服务时，用暂停提示盖住咕嘟页 / 对话页，而不是报错。
/// （[FeaturePausedGate] 的咕嘟助手特化，保留原 Key `agent-paused`。）
class AgentPausedGate extends StatelessWidget {
  final Widget child;

  /// 是否显示返回按钮（Tab 页不显示）。
  final bool showBack;

  const AgentPausedGate({super.key, required this.child, this.showBack = true});

  @override
  Widget build(BuildContext context) {
    return FeaturePausedGate(
      feature: AiFeature.agentChat,
      showBack: showBack,
      pausedKey: const Key('agent-paused'),
      child: child,
    );
  }
}
