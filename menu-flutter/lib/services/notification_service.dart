import '../core/api_client.dart';

/// 站内消息（GET /notification；P1 先接 expiry / shopping 两类，P3 扩展邀请/反馈/投稿）。
class AppNotification {
  final int id;
  final String type;
  final String title;
  final String content;
  final bool read;
  final DateTime? createTime;

  const AppNotification({
    required this.id,
    required this.type,
    required this.title,
    required this.content,
    required this.read,
    this.createTime,
  });

  factory AppNotification.fromJson(Map<String, dynamic> j) => AppNotification(
        id: (j['id'] as num).toInt(),
        type: (j['type'] ?? '') as String,
        title: (j['title'] ?? '') as String,
        content: (j['content'] ?? '') as String,
        read: ((j['isRead'] as num?)?.toInt() ?? 0) == 1,
        createTime: DateTime.tryParse(j['createTime']?.toString() ?? ''),
      );

  /// 提醒类（临期 / 采购）。
  bool get isReminder => type == 'expiry' || type == 'shopping';

  AppNotification markedRead() =>
      AppNotification(id: id, type: type, title: title, content: content, read: true, createTime: createTime);
}

class NotificationService {
  NotificationService._();

  static Future<List<AppNotification>> list() async {
    final data = await ApiClient.instance.get('/notification');
    if (data is! List) return const [];
    return data.map((e) => AppNotification.fromJson(e as Map<String, dynamic>)).toList();
  }

  static Future<int> unreadCount() async {
    final data = await ApiClient.instance.get('/notification/unread-count');
    return data is Map ? ((data['count'] as num?)?.toInt() ?? 0) : 0;
  }

  static Future<void> markRead(int id) async {
    await ApiClient.instance.put('/notification/$id/read');
  }
}
