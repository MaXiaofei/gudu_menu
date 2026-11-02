import 'package:flutter/foundation.dart';

import 'api_client.dart';

/// 生成式 AI 的类别（与后端 FeatureKey 对应）。
enum AiFeature { agentChat, cookAsk, vision, mealPlan }

/// 生成式 AI 紧急关停（出现内容风险 / 审核要求时由后台按类别、按端一键关闭，默认开）。
///
/// 后端按请求端判定：App 不带 X-Client 头，按「App」端取状态（小程序端是另一套开关）。
/// - 进入相关页时 `GET /feature-switches` 拉取状态，失败按「开」处理；
/// - 收到业务码 5032（咕嘟助手）/ 5033（问咕嘟）/ 5034（拍照识别）/ 5035（一周餐程）时不弹报错，直接切到暂停状态；
/// - 关闭的类别：入口隐藏，对应页面由 [FeaturePausedGate] 显示「XX暂停服务，其他功能不受影响」；
/// - 只拦生成 / 发新消息，查看已有内容、删除历史、确认 / 撤销动作等不受影响。
class FeatureSwitch {
  FeatureSwitch._();

  static const int agentSuspendedCode = 5032;
  static const int cookAskSuspendedCode = 5033;
  static const int visionSuspendedCode = 5034;
  static const int mealPlanSuspendedCode = 5035;
  static const String agentMessage = '咕嘟助手暂停服务，其他功能不受影响';
  static const String cookAskMessage = '问咕嘟暂停服务，跟做模式其他功能照常使用';
  static const String visionMessage = '拍照识别暂停服务，其他功能不受影响';
  static const String mealPlanMessage = '一周餐程暂停服务，其他功能不受影响';

  /// true = 可用（默认开）。
  static final ValueNotifier<bool> agentChat = ValueNotifier<bool>(true);
  static final ValueNotifier<bool> cookAsk = ValueNotifier<bool>(true);
  static final ValueNotifier<bool> vision = ValueNotifier<bool>(true);
  static final ValueNotifier<bool> mealPlan = ValueNotifier<bool>(true);

  static ValueNotifier<bool> notifierOf(AiFeature f) => switch (f) {
        AiFeature.agentChat => agentChat,
        AiFeature.cookAsk => cookAsk,
        AiFeature.vision => vision,
        AiFeature.mealPlan => mealPlan,
      };

  static String messageOf(AiFeature f) => switch (f) {
        AiFeature.agentChat => agentMessage,
        AiFeature.cookAsk => cookAskMessage,
        AiFeature.vision => visionMessage,
        AiFeature.mealPlan => mealPlanMessage,
      };

  static AiFeature? _featureOfCode(int code) => switch (code) {
        agentSuspendedCode => AiFeature.agentChat,
        cookAskSuspendedCode => AiFeature.cookAsk,
        visionSuspendedCode => AiFeature.vision,
        mealPlanSuspendedCode => AiFeature.mealPlan,
        _ => null,
      };

  /// 暂停码对应的提示语（非暂停码返回 null）。
  static String? messageOfCode(int code) {
    final f = _featureOfCode(code);
    return f == null ? null : messageOf(f);
  }

  /// 5032 ~ 5035 → 切到对应暂停状态；返回是否识别为暂停码。
  static bool noteCode(int code) {
    final f = _featureOfCode(code);
    if (f == null) return false;
    notifierOf(f).value = false;
    return true;
  }

  /// 对话上下文当前是否可发消息（cook_mode 看问咕嘟，其余看咕嘟助手）。
  static bool canSend(String? page) => page == 'cook_mode' ? cookAsk.value : agentChat.value;

  /// 解析 `/feature-switches` 返回并更新状态（缺字段按开处理）。
  static void apply(dynamic data) {
    if (data is! Map) return;
    agentChat.value = data['agentChat'] != false;
    cookAsk.value = data['cookAsk'] != false;
    vision.value = data['vision'] != false;
    mealPlan.value = data['mealPlan'] != false;
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
    for (final f in AiFeature.values) {
      notifierOf(f).value = true;
    }
  }
}
