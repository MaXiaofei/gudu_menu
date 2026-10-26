import 'package:flutter/foundation.dart';

/// 厨房圈紧急关停（mini-release-compliance A.12）。
///
/// 后端开关关闭时 `/community/**` 统一回业务码 [suspendedCode]「厨房圈暂停服务」：
/// ApiClient 收到后置 [suspended]=true（不弹报错），厨房圈各页由 CommunityPausedGate 显示暂停提示；
/// 之后任一厨房圈请求成功即恢复。
class CommunityStatus {
  CommunityStatus._();

  static const int suspendedCode = 5031;
  static const String message = '厨房圈暂停服务';

  static final ValueNotifier<bool> suspended = ValueNotifier<bool>(false);

  static bool isCommunityPath(String path) {
    final p = path.replaceFirst(RegExp(r'^https?://[^/]+'), '').replaceFirst(RegExp(r'^/gudu'), '');
    return RegExp(r'^/community(/|\?|$)').hasMatch(p);
  }

  /// ApiClient 每次响应后调用：5031 → 暂停；成功 → 恢复。
  static void note(String path, int code) {
    if (!isCommunityPath(path)) return;
    if (code == suspendedCode) {
      suspended.value = true;
    } else if (code == 0) {
      suspended.value = false;
    }
  }
}
