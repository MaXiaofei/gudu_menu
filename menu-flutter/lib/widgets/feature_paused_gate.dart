import 'package:flutter/material.dart';

import '../core/app_theme.dart';
import '../core/feature_switch.dart';
import 'loading_empty.dart';

/// 生成式 AI 某类别紧急关停：后端暂停服务时，用暂停提示盖住对应页面，而不是报错。
/// 文案「XX暂停服务，其他功能不受影响」。
class FeaturePausedGate extends StatelessWidget {
  final AiFeature feature;
  final Widget child;

  /// 是否显示返回按钮（Tab 页不显示）。
  final bool showBack;

  /// 暂停态根节点的 Key；缺省为 `<feature>-paused`。
  final Key? pausedKey;

  const FeaturePausedGate({
    super.key,
    required this.feature,
    required this.child,
    this.showBack = true,
    this.pausedKey,
  });

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<bool>(
      valueListenable: FeatureSwitch.notifierOf(feature),
      builder: (context, available, _) {
        if (available) return child;
        final t = AppTokens.of(context);
        return Scaffold(
          key: pausedKey ?? Key('${feature.name}-paused'),
          backgroundColor: t.bg,
          appBar: showBack ? AppBar(backgroundColor: t.bg, elevation: 0) : null,
          body: SafeArea(
            child: Center(
              child: EmptyView(
                text: FeatureSwitch.messageOf(feature),
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
