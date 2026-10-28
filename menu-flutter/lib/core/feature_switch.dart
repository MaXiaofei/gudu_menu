import 'package:flutter/foundation.dart';

import 'api_client.dart';

/// 咕嘟助手 / 问咕嘟紧急关停（出现内容风险时由后台一键关闭，默认开）。
///
/// - 进入相关页时 `GET /feature-switches` 拉取状态，失败按「开」处理；
/// - 发消息收到业务码 [agentSuspendedCode]（咕嘟助手暂停）/ [cookAskSuspendedCode]（问咕嘟暂停）时不弹报错，
///   直接切到暂停状态；咕嘟页与对话页由 AgentPausedGate 显示暂停提示，跟做模式隐藏问咕嘟输入区；
/// - 只拦发新消息，查看 / 删除历史、确认 / 撤销动作等不受影响。
class FeatureSwitch {
  FeatureSwitch._();

  static const int agentSuspendedCode = 5032;
  static const int cookAskSuspendedCode = 5033;
  static const String agentMessage = '咕嘟助手暂停服务，其他功能不受影响';
  static const String cookAskMessage = '问咕嘟暂停服务，跟做模式其他功能照常使用';

  /// true = 可用（默认开）。
  static final ValueNotifier<bool> agentChat = ValueNotifier<bool>(true);
  static final ValueNotifier<bool> cookAsk = ValueNotifier<bool>(true);

  /// 5032 / 5033 → 切到暂停状态；返回是否识别为暂停码。
  static bool noteCode(int code) {
    if (code == agentSuspendedCode) {
      agentChat.value = false;
      return true;
    }
    if (code == cookAskSuspendedCode) {
      cookAsk.value = false;
      return true;
    }
    return false;
  }

  /// 对话上下文当前是否可发消息（cook_mode 看问咕嘟，其余看咕嘟助手）。
  static bool canSend(String? page) => page == 'cook_mode' ? cookAsk.value : agentChat.value;

  /// 解析 `/feature-switches` 返回并更新状态（缺字段按开处理）。
  static void apply(dynamic data) {
    if (data is! Map) return;
    agentChat.value = data['agentChat'] != false;
    cookAsk.value = data['cookAsk'] != false;
  }

  /// 拉取用户侧状态；失败保持原状（默认开），不抛错。
  static Future<void> load() async {
    try {
      apply(await ApiClient.instance.get('/feature-switches'));
    } catch (_) {
      // 拉取失败不阻塞：按已知状态（默认开）
    }
  }

  @visibleForTesting
  static void reset() {
    agentChat.value = true;
    cookAsk.value = true;
  }
}
