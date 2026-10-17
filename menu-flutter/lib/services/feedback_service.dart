import '../core/api_client.dart';

/// 意见反馈（api-contract §7，G14）。
class FeedbackService {
  FeedbackService._();

  /// 内容长度约束（与后端一致：5–500 字）。
  static const minContent = 5;
  static const maxContent = 500;

  /// 最多 3 张截图（本站 uploads 地址）。
  static const maxImages = 3;

  /// 提交反馈：POST /feedback → 新反馈 id。每账号每日 ≤ 10 条（超限后端返回 429）。
  static Future<int> submit({
    required String content,
    String? contact,
    List<String> images = const [],
    String? clientInfo,
    /// 反馈类型 bug/feature/recipe/other（后端字段 P3 上线前忽略，同时写入 clientInfo）。
    String? type,
    /// 报错编号（短 traceId，xxxx-xxxx）。
    String? errorId,
  }) async {
    final data = await ApiClient.instance.post('/feedback', body: {
      'content': content,
      if (contact != null && contact.isNotEmpty) 'contact': contact,
      if (images.isNotEmpty) 'images': images.take(maxImages).toList(),
      if (clientInfo != null && clientInfo.isNotEmpty)
        'clientInfo': clientInfo.length > 200 ? clientInfo.substring(0, 200) : clientInfo,
      if (type != null) 'type': type,
      if (errorId != null) 'errorId': errorId,
    });
    return (data as num?)?.toInt() ?? 0;
  }
}
